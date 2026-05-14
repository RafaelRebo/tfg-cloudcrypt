package com.example.mapper;

import com.example.dto.FileDto;
import com.example.model.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    public FileDto toDto(FileEntity entity) {
        if (entity == null) return null;

        String ownerName = (entity.getOwner() != null) ? entity.getOwner().getUsername() : "Desconocido";

        // CAMBIO: Verificamos tamaño con un nulo-safe
        // En FileMapper.java
// Asegúrate de que estamos contando correctamente todas las llaves
        // En FileMapper.java (Lógica más permisiva)
        boolean isShared = false;
        if (entity.getFileKeys() != null) {
            // Si hay alguna llave cuyo usuario NO sea el dueño, está compartido.
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
                isShared
        );
    }
}