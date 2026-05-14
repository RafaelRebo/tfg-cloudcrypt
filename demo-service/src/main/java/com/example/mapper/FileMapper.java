package com.example.mapper;

import com.example.dto.FileDto;
import com.example.model.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    public FileDto toDto(FileEntity entity) {
        if (entity == null) return null;

        String ownerName = (entity.getOwner() != null) ? entity.getOwner().getUsername() : "Desconocido";

        boolean isShared = false;
        if (entity.getFileKeys() != null) {
            isShared = entity.getFileKeys().stream()
                    .anyMatch(k -> !k.getUser().getUsername().equals(ownerName));
        }

        return new FileDto(
                entity.getId(),
                entity.getFileName(),
                entity.getFileType(),
                entity.getFileSize(),
                entity.getFolderPath(),
                entity.getChecksum(),
                entity.getDeletedAt(),
                entity.getSalt(),
                ownerName,
                isShared,
                entity.isStarred(),
                entity.getParent() != null ? entity.getParent().getId() : null
        );
    }
}