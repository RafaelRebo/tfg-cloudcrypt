package com.cloudcrypt.repository.file;

import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.UserEntity;

public interface FileRepositoryCustom {
    FileEntity createFile(String name, String folderPath, String type, long size,
                          String checksum, String storagePath, UserEntity username, String salt);

    FileEntity createFolder(String name, String folderPath, String username);

    long getTotalUsageByUser(String username);

    long countFilesByUser(String username);

    void markAsDeleted(Long id);

    void restoreFile(Long id);

    void hardDelete(Long id);
}