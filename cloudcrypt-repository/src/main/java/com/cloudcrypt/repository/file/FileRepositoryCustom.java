package com.cloudcrypt.repository.file;

import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.UserEntity;

public interface FileRepositoryCustom {

    long getTotalUsageByUser(String username);

    long countFilesByUser(String username);

    void markAsDeleted(Long id);

    void restoreFile(Long id);
}