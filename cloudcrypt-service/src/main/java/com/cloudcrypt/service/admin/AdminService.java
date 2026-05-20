package com.cloudcrypt.service.admin;

import com.cloudcrypt.dto.admin.AdminStatsDto;
import com.cloudcrypt.dto.admin.UserDiskMetricDto;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.repository.file.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    public AdminService(UserRepository userRepository, FileRepository fileRepository) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getSystemVolumeStats() {
        log.debug("ADMIN: Recolectando métricas globales de almacenamiento...");
        List<UserEntity> allUsers = userRepository.findAll();
        List<UserDiskMetricDto> metrics = new ArrayList<>();
        long totalGlobalBytes = 0;

        for (UserEntity user : allUsers) {
            long bytesUsed = fileRepository.getTotalUsageByUser(user.getUsername());
            long filesOwned = fileRepository.countFilesByUser(user.getUsername());
            totalGlobalBytes += bytesUsed;

            metrics.add(new UserDiskMetricDto(
                    user.getId(),
                    user.getUsername(),
                    user.getFullName(),
                    filesOwned,
                    bytesUsed,
                    user.getQuotaBytes(),
                    user.getRole(),
                    user.getAvatarUrl()
            ));
        }

        log.info("ADMIN: Espacio total ocupado en disco: {} bytes entre {} usuarios.", totalGlobalBytes, allUsers.size());
        return new AdminStatsDto(totalGlobalBytes, metrics);
    }

    @Transactional
    public void updateUserParameters(Long id, Long quotaBytes, String role, String requesterUsername) {
        UserEntity target = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("ADMIN: Intento de actualizar un usuario inexistente (ID: {}).", id);
                    return new com.cloudcrypt.exceptions.InstanceNotFoundException("Usuario no encontrado en el búnker.");
                });

        if (target.getUsername().equalsIgnoreCase(requesterUsername)) {
            log.warn("ADMIN: El administrador [{}] intentó alterar sus propios privilegios.", requesterUsername);
            throw new com.cloudcrypt.exceptions.InputValidationException("No puedes alterar tus propios privilegios.");
        }

        log.info("ADMIN: Modificando políticas del usuario [{}]. Nueva cuota: {} bytes, Rol asignado: {}.",
                target.getUsername(), quotaBytes, role.toUpperCase());

        target.setQuotaBytes(quotaBytes);
        target.setRole(role.toUpperCase());
        userRepository.save(target);
    }
}