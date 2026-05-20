package com.cloudcrypt.service.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AvatarService {

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        try {
            Path rootFolder = Paths.get(uploadDir, "avatars");

            if (!Files.exists(rootFolder)) {
                Files.createDirectories(rootFolder);
            }

            String originalName = file.getOriginalFilename();
            String extension = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".png";

            String uniqueFilename = UUID.randomUUID().toString() + extension;
            Path targetPath = rootFolder.resolve(uniqueFilename);

            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            return "/static/avatars/" + uniqueFilename;

        } catch (IOException e) {
            throw new RuntimeException("Error crítico de E/S al guardar el avatar en el disco: " + e.getMessage(), e);
        }
    }
}