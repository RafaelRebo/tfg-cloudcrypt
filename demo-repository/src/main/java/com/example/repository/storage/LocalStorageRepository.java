package com.example.repository.storage;

import org.springframework.context.annotation.Primary;
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
@Primary
public class LocalStorageRepository implements IStorageRepository {

    private final Path root = Paths.get("uploads");

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            // Permisos restringidos: solo el dueño puede leer/escribir/ejecutar
            root.toFile().setReadable(true, false);
            root.toFile().setWritable(true, false);
            root.toFile().setExecutable(true, false);
        } catch (IOException e) {
            throw new RuntimeException("Error inicializando almacenamiento local");
        }
    }

    @Override
    public void save(InputStream input, String folder, String filename) throws IOException {
        Path targetFolder = this.root.resolve(folder);
        if (!Files.exists(targetFolder)) {
            Files.createDirectories(targetFolder);
        }
        Path targetFile = targetFolder.resolve(filename);

        try (OutputStream os = Files.newOutputStream(targetFile)) {
            input.transferTo(os);
        }
    }

    @Override
    public void delete(String storagePath) throws IOException {
        Path path = this.root.resolve(storagePath);

        if (Files.exists(path)) {
            Files.delete(path);

            // Borrado recursivo de carpetas vacías
            Path parent = path.getParent();
            while (parent != null && !parent.equals(this.root) && Files.exists(parent)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
                    if (!stream.iterator().hasNext()) {
                        Files.delete(parent);
                        parent = parent.getParent();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public InputStream loadStream(String relativePath) throws IOException {
        // Retornamos el chorro de bytes (cifrados) directamente del disco
        return Files.newInputStream(this.root.resolve(relativePath));
    }

    @Override
    public boolean exists(String storagePath) {
        return Files.exists(this.root.resolve(storagePath));
    }
}