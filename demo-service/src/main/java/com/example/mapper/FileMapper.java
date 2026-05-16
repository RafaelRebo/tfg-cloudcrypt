package com.example.mapper;

import com.example.dto.file.FileDto;
import com.example.model.FileEntity;
import com.example.model.FileKeyEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    public FileDto toDto(FileEntity entity) {
        if (entity == null) return null;

        String ownerName = (entity.getOwner() != null) ? entity.getOwner().getUsername() : "Desconocido";

        // 1. Calculamos si el archivo está compartido de forma general
        boolean isShared = false;
        if (entity.getFileKeys() != null) {
            isShared = entity.getFileKeys().stream()
                    .anyMatch(k -> !k.getUser().getUsername().equals(ownerName));
        }

        // 2. Extraemos el usuario actual autenticado en el hilo de Spring Security de forma segura
        String currentLoggedUser = SecurityContextHolder.getContext().getAuthentication().getName();

        // 3. Buscamos el estado privado de la estrella dentro de la llave de este usuario específico
        boolean userStarredStatus = false;
        if (entity.getFileKeys() != null) {
            userStarredStatus = entity.getFileKeys().stream()
                    .filter(k -> k.getUser().getUsername().equals(currentLoggedUser))
                    .map(FileKeyEntity::isStarred)
                    .findFirst()
                    .orElse(false);
        }

        return new FileDto(
                entity.getId(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getFolderPath(),
                entity.getChecksum(),
                entity.getUpdatedAt(),
                entity.getDeletedAt(),
                entity.getSalt(),
                ownerName,
                isShared,
                userStarredStatus,
                entity.getParent() != null ? entity.getParent().getId() : null
        );
    }
}