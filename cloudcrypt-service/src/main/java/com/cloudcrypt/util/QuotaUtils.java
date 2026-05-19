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
    private final UserRepository userRepository; // ⚡ NUEVA INYECCIÓN

    public QuotaUtils(FileRepository fileRepository, StorageConfig storageConfig, UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.storageConfig = storageConfig;
        this.userRepository = userRepository;
    }

    public void checkQuota(String username, long newFileSize){
        // 1. Recuperamos la entidad del usuario para inspeccionar sus políticas personalizadas
        UserEntity user = userRepository.findByUsername(username);

        // 2. ⚡ PRIORIDAD DE GOBERNANZA: Si tiene cuota asignada en la BD, se usa esa. Si no, la global del config.
        long maxQuotaForUser = (user != null && user.getQuotaBytes() != null)
                ? user.getQuotaBytes()
                : storageConfig.getMaxQuota();

        long currentUsage = fileRepository.getTotalUsageByUser(username);

        // 3. Validación atómica de desborde por subida
        if (currentUsage + newFileSize > maxQuotaForUser) {
            // ⚡ BLINDAJE ANTI-NEGATIVOS: Si el admin le asignó menos espacio del que ya ocupa,
            // calculamos el disponible real como 0 para evitar divisiones o textos incoherentes.
            long disponible = (maxQuotaForUser > currentUsage) ? (maxQuotaForUser - currentUsage) : 0L;

            throw new QuotaExceededException("Cuota excedida en el búnker. Disponible para nuevas subidas: "
                    + (disponible / (1024 * 1024)) + " MB");
        }
    }
}