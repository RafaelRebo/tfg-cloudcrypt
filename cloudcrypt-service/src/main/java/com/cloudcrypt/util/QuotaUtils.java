package com.cloudcrypt.util;

import com.cloudcrypt.config.StorageConfig;
import com.cloudcrypt.exceptions.QuotaExceededException;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.user.UserRepository; // ⚡ NUEVO IMPORT
import com.cloudcrypt.model.UserEntity; // ⚡ NUEVO IMPORT
import org.springframework.stereotype.Service;

@Service
public class QuotaUtils {

    private final FileRepository fileRepository;
    private final StorageConfig storageConfig;
    private final UserRepository userRepository;

    public QuotaUtils(FileRepository fileRepository, StorageConfig storageConfig, UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.storageConfig = storageConfig;
        this.userRepository = userRepository;
    }

    public void checkQuota(String username, long newFileSize){
        UserEntity user = userRepository.findByUsername(username);

        long maxQuotaForUser = (user != null && user.getQuotaBytes() != null)
                ? user.getQuotaBytes()
                : storageConfig.getMaxQuota();

        long currentUsage = fileRepository.getTotalUsageByUser(username);

        if (currentUsage + newFileSize > maxQuotaForUser) {
            long disponible = (maxQuotaForUser > currentUsage) ? (maxQuotaForUser - currentUsage) : 0L;

            throw new QuotaExceededException("Cuota excedida en el búnker. Disponible para nuevas subidas: "
                    + (disponible / (1024 * 1024)) + " MB");
        }
    }
}