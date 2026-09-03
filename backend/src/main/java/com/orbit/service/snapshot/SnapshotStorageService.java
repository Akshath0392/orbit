package com.orbit.service.snapshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Persists rendered PNG / PDF bytes to local disk. Interface is intentionally narrow so
 * a future S3 implementation can drop in without touching {@link RadarSnapshotAgent} or
 * {@link SnapshotService}.
 */
@Service
public class SnapshotStorageService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotStorageService.class);

    private final Path root;

    public SnapshotStorageService(@Value("${snapshot.storage.root:/var/snapshots}") String rootDir) {
        this.root = Paths.get(rootDir);
    }

    public Stored save(long snapshotId, byte[] png, byte[] pdf) {
        Path dir = root.resolve(String.valueOf(snapshotId));
        try {
            Files.createDirectories(dir);
            Path pngPath = dir.resolve("snapshot.png");
            Path pdfPath = dir.resolve("snapshot.pdf");
            Files.write(pngPath, png);
            Files.write(pdfPath, pdf);
            return new Stored(pngPath.toString(), pdfPath.toString());
        } catch (IOException e) {
            throw new RuntimeException("snapshot storage failed for id=" + snapshotId + ": " + e.getMessage(), e);
        }
    }

    public byte[] read(String path) {
        try {
            return Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            log.warn("snapshot read failed for path={}: {}", path, e.getMessage());
            return null;
        }
    }

    public record Stored(String pngPath, String pdfPath) {}
}
