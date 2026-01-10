package com.datacrowd.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    /**
     * Extract uploaded dataset ZIP (stored at sourcePath) into:
     *   {dataDir}/datasets/{datasetId}/unzipped/
     * and return absolute path to manifest.jsonl inside extracted folder.
     *
     * Supported locations:
     *  - unzipped/manifest.jsonl
     *  - unzipped/manifest/manifest.jsonl
     */
    public String extractDatasetZipAndFindManifest(UUID datasetId, String sourcePath) {
        try {
            Path zipPath = Paths.get(sourcePath).toAbsolutePath().normalize();
            if (!Files.exists(zipPath)) {
                throw new IllegalStateException("ZIP file not found: " + zipPath);
            }

            Path datasetDir = dataDir.resolve("datasets").resolve(datasetId.toString()).normalize();
            Files.createDirectories(datasetDir);

            Path unzipDir = datasetDir.resolve("unzipped").normalize();
            if (Files.exists(unzipDir)) {
                deleteRecursively(unzipDir);
            }
            Files.createDirectories(unzipDir);

            unzipSafely(zipPath, unzipDir);

            Path manifest1 = unzipDir.resolve("manifest.jsonl").normalize();
            Path manifest2 = unzipDir.resolve("manifest").resolve("manifest.jsonl").normalize();

            Path manifest;
            if (Files.exists(manifest1)) {
                manifest = manifest1;
            } else if (Files.exists(manifest2)) {
                manifest = manifest2;
            } else {
                // Fallback: find first manifest.jsonl anywhere inside unzipped
                manifest = findFirstByName(unzipDir, "manifest.jsonl");
                if (manifest == null) {
                    throw new IllegalStateException("manifest.jsonl not found in dataset zip");
                }
            }

            return manifest.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to extract dataset zip: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve an asset path (relative like "assets/img.jpg") inside extracted dataset zip folder.
     * This prevents path traversal and ensures the file is inside dataset's unzipped directory.
     */
    public Path resolveDatasetAsset(UUID datasetId, String assetRelPath) {
        try {
            if (assetRelPath == null || assetRelPath.isBlank()) {
                throw new IllegalArgumentException("assetRelPath is blank");
            }
            // Normalize and strip leading slashes
            String rel = assetRelPath.replace("\\", "/");
            while (rel.startsWith("/")) rel = rel.substring(1);

            Path unzipDir = dataDir.resolve("datasets").resolve(datasetId.toString()).resolve("unzipped").normalize();
            Path resolved = unzipDir.resolve(rel).normalize();

            if (!resolved.startsWith(unzipDir)) {
                throw new IllegalStateException("Forbidden asset path");
            }
            if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
                throw new IllegalStateException("Asset not found: " + rel);
            }
            return resolved;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to resolve asset: " + e.getMessage(), e);
        }
    }

    public Path ensureExportFile(UUID projectId, UUID datasetId, String filename) {
        try {
            Files.createDirectories(dataDir);
            Path exportDir = dataDir.resolve("exports")
                    .resolve(projectId.toString())
                    .resolve(datasetId.toString());
            Files.createDirectories(exportDir);

            // avoid path traversal in filename
            String safe = filename == null ? "export.jsonl" : filename;
            safe = safe.replace("\\", "/");
            if (safe.contains("..") || safe.contains("/")) {
                safe = safe.substring(safe.lastIndexOf('/') + 1);
            }
            if (safe.isBlank()) safe = "export.jsonl";

            return exportDir.resolve(safe).normalize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to prepare export directory: " + e.getMessage(), e);
        }
    }

    public byte[] readBytes(String absolutePath) {
        try {
            return Files.readAllBytes(Paths.get(absolutePath));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read file: " + e.getMessage(), e);
        }
    }

    private void unzipSafely(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    Path dir = safeResolve(targetDir, entry.getName());
                    Files.createDirectories(dir);
                    continue;
                }
                Path out = safeResolve(targetDir, entry.getName());
                Files.createDirectories(out.getParent());
                Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path safeResolve(Path rootDir, String entryName) throws IOException {
        String name = entryName.replace("\\", "/");
        while (name.startsWith("/")) name = name.substring(1);
        Path resolved = rootDir.resolve(name).normalize();
        if (!resolved.startsWith(rootDir)) {
            throw new IOException("Blocked zip-slip entry: " + entryName);
        }
        return resolved;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted((a, b) -> b.compareTo(a)) // delete children first
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
    }

    private Path findFirstByName(Path root, String filename) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream
                    .filter(p -> p.getFileName() != null && filename.equalsIgnoreCase(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        }
    }
}
