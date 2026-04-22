package com.example.mapper;

import com.example.dto.FileDto;
import com.example.model.FileEntity;
import org.springframework.stereotype.Component;

@Component
public class FileMapper {
    public FileDto toDto(FileEntity entity) {
        if (entity == null) return null;
        return new FileDto(
                entity.getId(), entity.getFileName(), entity.getFileType(),
                entity.getFileSize(), entity.getFolderPath(), entity.getChecksum()
        );
    }
}