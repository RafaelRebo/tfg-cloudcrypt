package com.example.repository;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;

@Component
public class FileStorageRepository {
    private final Path root = Paths.get("uploads");

    @PostConstruct
    public void init() {
        try {
            if (!Files.exists(root)) Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Error inicializando almacenamiento ");
        }
    }

    public void save(String physicalFolder, String filename, byte[] data) throws IOException {
        // Resolvemos la ruta relativa dentro de 'uploads'
        Path targetPath = this.root.resolve(physicalFolder);

        // Creamos todos los directorios intermedios (usuario + carpetas virtuales)
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }

        // Escribimos el fichero cifrado en su ubicación final
        Files.write(targetPath.resolve(filename), data);
    }

    public byte[] load(String relativePath) throws IOException {
        // La ruta ya incluye el usuario y las subcarpetas desde la base de datos [cite: 34]
        return Files.readAllBytes(this.root.resolve(relativePath));
    }

    public void delete(String storagePath) throws IOException {
        // Usamos resolve para mantener la consistencia con root
        Path path = this.root.resolve(storagePath);
        Files.deleteIfExists(path);
    }
}