package com.cloudcrypt.service.user;

import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.UserKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.StorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserDeleteService {

    private static final Logger log = LoggerFactory.getLogger(UserDeleteService.class);

    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final UserKeyRepository userKeyRepository;
    private final StorageUtils storageUtils;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public UserDeleteService(UserRepository userRepository, FileRepository fileRepository,
                             UserKeyRepository userKeyRepository, StorageUtils storageUtils,
                             TransactionTemplate transactionTemplate) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.userKeyRepository = userKeyRepository;
        this.storageUtils = storageUtils;
        this.transactionTemplate = transactionTemplate;
    }

    public void purgeUserFully(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new InstanceNotFoundException("Usuario inexistente en la plataforma."));

        log.warn("OPERACIÓN: Borrando completamente usuario @{}.", user.getUsername());

        List<String> pathsToDelete = fileRepository.findByOwnerUsername(user.getUsername()).stream()
                .map(FileEntity::getStoragePath)
                .filter(path -> path != null)
                .collect(Collectors.toList());

        String avatarUrl = user.getAvatarUrl();

        transactionTemplate.executeWithoutResult(status -> {
            fileRepository.deleteByOwnerId(userId);
            userKeyRepository.deleteById(userId);
            userRepository.delete(user);
        });

        int filesPurgedCount = 0;
        for (String storagePath : pathsToDelete) {
            try {
                storageUtils.deletePhysicalFile(storagePath);
                filesPurgedCount++;
            } catch (Exception e) {
                log.error("OPERACIÓN: Bloque huérfano inamovible en: '{}'. Ignorado.", storagePath);
            }
        }
        log.info("OPERACIÓN: Borrados exitosamente {} ficheros del disco duro para el usuario destruido.", filesPurgedCount);

        if (avatarUrl != null && avatarUrl.startsWith("/static/avatars/")) {
            try {
                String filename = avatarUrl.substring(avatarUrl.lastIndexOf("/") + 1);
                Path avatarPath = Paths.get(uploadDir, "avatars", filename);
                if (Files.deleteIfExists(avatarPath)) {
                    log.info("OPERACIÓN: Imagen de avatar eliminada del disco: {}", avatarPath);
                }
            } catch (IOException e) {
                log.error("OPERACIÓN: No se pudo eliminar el avatar.");
            }
        }
    }
}