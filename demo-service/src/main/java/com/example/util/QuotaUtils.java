package com.example.util;

import com.example.config.StorageConfig;
import com.example.repository.file.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QuotaUtils {

    @Autowired private FileRepository fileRepository;
    @Autowired private StorageConfig storageConfig;

    public void checkQuota(String username, long newFileSize) {
        long currentUsage = fileRepository.getTotalUsageByUser(username);
        if (currentUsage + newFileSize > storageConfig.getMaxQuota()) {
            long disponible = storageConfig.getMaxQuota() - currentUsage;
            throw new RuntimeException("Cuota excedida. Disponible: " + (disponible / (1024 * 1024)) + " MB");
        }
    }
}