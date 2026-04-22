package com.example.repository.file;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import javax.crypto.CipherOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    public void save(InputStream input, String folder, String filename, javax.crypto.Cipher cipher) throws IOException {
        Path targetFolder = this.root.resolve(folder);
        if (!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }

        Path targetFile = targetFolder.resolve(filename);

        try (OutputStream os = Files.newOutputStream(targetFile);
             CipherOutputStream cos = new CipherOutputStream(os, cipher)) {
            input.transferTo(cos);
        }
    }

    public InputStream loadStream(String relativePath) throws IOException {
        return Files.newInputStream(this.root.resolve(relativePath));
    }

    public void delete(String storagePath) throws IOException {
        Path path = this.root.resolve(storagePath);
        Files.deleteIfExists(path);
    }
}