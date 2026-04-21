package com.example.repository;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;

@Component
public class FileStorageRepository {
    private final Path root = Paths.get("uploads");

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(root)) Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Error inicializando almacenamiento");
        }
    }

    public Path getTargetPath(String physicalFolder, String filename) throws IOException {
        Path targetPath = this.root.resolve(physicalFolder);
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }
        return targetPath.resolve(filename);
    }

    public InputStream loadStream(String relativePath) throws IOException {
        return Files.newInputStream(this.root.resolve(relativePath));
    }

    public void delete(String storagePath) throws IOException {
        Path path = this.root.resolve(storagePath);
        Files.deleteIfExists(path);
    }
}