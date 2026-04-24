package com.datacrowd.core.service;

import io.minio.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * MinIO реализация StorageService.
 * Активируется когда app.storage.type=minio в конфиге.
 * Для переключения с local на minio достаточно одной строки в .env:
 *   STORAGE_TYPE=minio
 *
 * Совместима с AWS S3 API — переезд на S3 требует только смены endpoint и ключей.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "minio")
public class MinioStorageService extends StorageService {

    private final MinioClient minioClient;
    private final String      bucket;

    public MinioStorageService(
            @Value("${app.storage.minio.endpoint}") String endpoint,
            @Value("${app.storage.minio.access-key}") String accessKey,
            @Value("${app.storage.minio.secret-key}") String secretKey,
            @Value("${app.storage.minio.bucket}") String bucket,
            @Value("${app.data-dir:/data}") String dataDir) {

        super(dataDir);
        this.bucket = bucket;

        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        ensureBucketExists();
    }

    /**
     * Создаём bucket если его нет — идемпотентная операция.
     */
    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to initialize MinIO bucket '" + bucket + "': " + e.getMessage(), e);
        }
    }

    @Override
    public String saveDatasetSource(UUID datasetId, MultipartFile file) {
        try {
            String originalName = (file.getOriginalFilename() != null)
                    ? file.getOriginalFilename() : "source";

            // Путь в MinIO: datasets/{datasetId}/{filename}
            String objectName = "datasets/" + datasetId + "/" + originalName;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType() != null
                            ? file.getContentType() : "application/octet-stream")
                    .build());

            return "minio://" + bucket + "/" + objectName;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to upload dataset to MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] readBytes(String path) {
        try {
            // Убираем префикс minio://bucket/
            String objectName = path.replace("minio://" + bucket + "/", "");

            InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());

            return stream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to read from MinIO path '" + path + "': " + e.getMessage(), e);
        }
    }

    @Override
    public Path resolveDatasetAsset(UUID datasetId, String relPath) {
        try {
            // Скачиваем файл из MinIO во временный локальный файл
            // чтобы стриминг в TasksController работал без изменений
            String objectName = "datasets/" + datasetId + "/" + relPath;

            Path tmpDir  = Paths.get(System.getProperty("java.io.tmpdir"), "datacrowd-assets");
            Files.createDirectories(tmpDir);

            Path localFile = tmpDir.resolve(datasetId + "_" + relPath.replace("/", "_"));

            if (!Files.exists(localFile)) {
                try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build())) {
                    Files.copy(stream, localFile);
                }
            }
            return localFile;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to resolve asset from MinIO: " + e.getMessage(), e);
        }
    }
}