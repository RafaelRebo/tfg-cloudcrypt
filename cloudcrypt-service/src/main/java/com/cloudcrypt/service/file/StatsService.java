package com.cloudcrypt.service.file;

import com.cloudcrypt.config.StorageConfig;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.model.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.Map;

@Service
public class StatsService {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);

    private final FileRepository fileRepository;
    private final StorageConfig storageConfig;
    private final UserRepository userRepository;

    public StatsService(FileRepository fileRepository, StorageConfig storageConfig, UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.storageConfig = storageConfig;
        this.userRepository = userRepository;
    }

    public Map<String, Object> getUserStats(String username) {
        log.debug("OPERACIÓN: Consultando ocupación en disco para el usuario: @{}", username);
        long totalSize = fileRepository.getTotalUsageByUser(username);
        long fileCount = fileRepository.countFilesByUser(username);

        UserEntity user = userRepository.findByUsername(username);

        long maxQuota = (user != null && user.getQuotaBytes() != null)
                ? user.getQuotaBytes()
                : storageConfig.getMaxQuota();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", fileCount);
        stats.put("maxQuota", maxQuota);

        double usagePercentage = maxQuota > 0 ? (double) totalSize / maxQuota * 100 : 0;
        stats.put("usagePercentage", Math.round(usagePercentage * 100.0) / 100.0);

        return stats;
    }
}