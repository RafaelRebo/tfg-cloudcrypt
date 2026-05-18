package com.example.mapper;

import com.example.dto.file.FileDto;
import com.example.model.FileEntity;
import com.example.model.FileKeyEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {

    public FileDto toDto(FileEntity entity, String currentLoggedUser) {
        if (entity == null) return null;

        String ownerName = (entity.getOwner() != null) ? entity.getOwner().getUsername() : "Desconocido";

        // 1. Cálculo de compartido (optimizado en memoria RAM sobre la colección prefetched)
        boolean isShared = false;
        if (entity.getFileKeys() != null) {
            isShared = entity.getFileKeys().stream()
                    .anyMatch(k -> !k.getUser().getUsername().equals(ownerName));
        }

        // 2. Estado privado de la estrella utilizando el parámetro inyectado de forma limpia
        boolean userStarredStatus = false;
        if (entity.getFileKeys() != null && currentLoggedUser != null) {
            userStarredStatus = entity.getFileKeys().stream()
                    .filter(k -> k.getUser().getUsername().equals(currentLoggedUser))
                    .map(FileKeyEntity::isStarred)
                    .findFirst()
                    .orElse(false);
        }

        assert entity.getOwner() != null;
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
                entity.getOwner().getFullName(),
                entity.getOwner().getAvatarUrl(),
                isShared,
                userStarredStatus,
                entity.getParent() != null ? entity.getParent().getId() : null
        );
    }
}