package com.cloudcrypt.service.admin;

import com.cloudcrypt.dto.admin.AdminStatsDto;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.repository.file.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final FileRepository fileRepository;

    public AdminService(UserRepository userRepository, FileRepository fileRepository) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getSystemVolumeStats() {
        List<UserEntity> allUsers = userRepository.findAll();
        List<AdminStatsDto.UserDiskMetric> metrics = new ArrayList<>();
        long totalGlobalBytes = 0;

        for (UserEntity user : allUsers) {
            long bytesUsed = fileRepository.getTotalUsageByUser(user.getUsername());
            long filesOwned = fileRepository.countFilesByUser(user.getUsername());
            totalGlobalBytes += bytesUsed;

            metrics.add(new AdminStatsDto.UserDiskMetric(
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

        return new AdminStatsDto(totalGlobalBytes, metrics);
    }

    @Transactional
    public void updateUserParameters(Long id, Long quotaBytes, String role, String requesterUsername) {
        UserEntity target = userRepository.findById(id)
                .orElseThrow(() -> new com.cloudcrypt.exceptions.InstanceNotFoundException("Usuario no encontrado en el búnker."));

        if (target.getUsername().equalsIgnoreCase(requesterUsername)) {
            throw new com.cloudcrypt.exceptions.InputValidationException("No puedes alterar tus propios privilegios.");
        }

        target.setQuotaBytes(quotaBytes);
        target.setRole(role.toUpperCase());
        userRepository.save(target);
    }
}