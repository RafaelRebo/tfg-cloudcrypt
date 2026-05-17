package com.example.util;

import com.example.config.StorageConfig;
import com.example.exceptions.QuotaExceededException;
import com.example.repository.file.FileRepository;
import org.springframework.stereotype.Service;

@Service
public class QuotaUtils {

    private final FileRepository fileRepository;
    private final StorageConfig storageConfig;

    public QuotaUtils(FileRepository fileRepository, StorageConfig storageConfig) {
        this.fileRepository = fileRepository;
        this.storageConfig = storageConfig;
    }

    public void checkQuota(String username, long newFileSize){
        long currentUsage = fileRepository.getTotalUsageByUser(username);
        if (currentUsage + newFileSize > storageConfig.getMaxQuota()) {
            long disponible = storageConfig.getMaxQuota() - currentUsage;
            throw new QuotaExceededException("Cuota excedida. Disponible: " + (disponible / (1024 * 1024)) + " MB");
        }
    }
}