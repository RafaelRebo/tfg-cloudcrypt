package com.cloudcrypt.mapper;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {

    public FileDto toDto(FileEntity entity, String currentLoggedUser) {
        if (entity == null) return null;

        String ownerName = (entity.getOwner() != null) ? entity.getOwner().getUsername() : "Desconocido";

        boolean isShared = false;
        if (entity.getFileKeys() != null) {
            isShared = entity.getFileKeys().stream()
                    .anyMatch(k -> !k.getUser().getUsername().equals(ownerName));
        }

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
                ownerName,
                entity.getOwner().getFullName(),
                entity.getOwner().getAvatarUrl(),
                isShared,
                userStarredStatus,
                entity.getParent() != null ? entity.getParent().getId() : null
        );
    }
}