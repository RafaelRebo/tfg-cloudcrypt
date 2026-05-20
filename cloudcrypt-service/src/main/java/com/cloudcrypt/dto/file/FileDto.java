package com.cloudcrypt.dto.file;

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
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private String ownerUsername;
    private String ownerFullName;
    private String ownerAvatarUrl;
    private boolean isShared;
    private boolean isStarred;
    private Long parentId;
}

