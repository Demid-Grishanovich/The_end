package com.datacrowd.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;

@Service
public class StorageService {

    private final Path dataDir;

    public StorageService(@Value("${app.data-dir:/data}") String dataDir) {
        this.dataDir = Paths.get(dataDir).toAbsolutePath().normalize();
    }

    public String saveDatasetSource(UUID datasetId, MultipartFile file) {
        try {
            Files.createDirectories(dataDir);

            String original = file.getOriginalFilename();
            String ext = "";
            if (StringUtils.hasText(original) && original.contains(".")) {
                ext = original.substring(original.lastIndexOf('.'));
                if (ext.length() > 10) ext = "";
            }

            Path datasetDir = dataDir.resolve("datasets").resolve(datasetId.toString());
            Files.createDirectories(datasetDir);

            Path target = datasetDir.resolve("source" + ext).normalize();

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return target.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store dataset file: " + e.getMessage(), e);
        }
    }
}
