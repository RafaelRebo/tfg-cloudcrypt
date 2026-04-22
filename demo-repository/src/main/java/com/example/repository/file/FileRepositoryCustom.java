package com.example.repository.file;

import com.example.model.FileEntity;

public interface FileRepositoryCustom {
    FileEntity createFile(String name, String folderPath, String type, long size,
                          String checksum, String storagePath, String username);

    long getTotalUsageByUser(String username);

    long countFilesByUser(String username);
}