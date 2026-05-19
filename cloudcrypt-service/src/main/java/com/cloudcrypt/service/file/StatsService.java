package com.cloudcrypt.service.file;

import com.cloudcrypt.config.StorageConfig;
import com.cloudcrypt.repository.file.FileRepository;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class StatsService {

    private final FileRepository fileRepository;
    private final StorageConfig storageConfig;

    public StatsService(FileRepository fileRepository, StorageConfig storageConfig) {
        this.fileRepository = fileRepository;
        this.storageConfig = storageConfig;
    }

    public Map<String, Object> getUserStats(String username) {
        long totalSize = fileRepository.getTotalUsageByUser(username);
        long fileCount = fileRepository.countFilesByUser(username);
        long maxQuota = storageConfig.getMaxQuota();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", fileCount);
        stats.put("maxQuota", maxQuota);

        double usagePercentage = maxQuota > 0 ? (double) totalSize / maxQuota * 100 : 0;
        stats.put("usagePercentage", Math.round(usagePercentage * 100.0) / 100.0);

        return stats;
    }
}