package com.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileDto {
    private Long id;
    private String fileName;
    private String fileType;
    private long fileSize;
    private String folderPath;
    private String checksum;
    private LocalDateTime deletedAt;
    private String salt;
    private String ownerUsername;
    private boolean isShared;
}

