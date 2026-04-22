package com.example.repository.file;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
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

    public InputStream loadDecryptedStream(String relativePath, Cipher cipher) throws IOException {
        InputStream is = Files.newInputStream(this.root.resolve(relativePath));
        return new CipherInputStream(is, cipher);
    }

    public void delete(String storagePath) throws IOException {
        Path path = this.root.resolve(storagePath);
        Files.deleteIfExists(path);
    }
}