package com.cloudcrypt.service.file;

import com.cloudcrypt.dto.file.ShareRequestDto;
import com.cloudcrypt.exceptions.FileAccessDeniedException;
import com.cloudcrypt.exceptions.InputValidationException;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.*;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.PathUtils;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ShareService {
    private final FileRepository fileRepository;
    private final FileKeyRepository fileKeyRepository;
    private final UserRepository userRepository;
    private final PathUtils pathUtils;
    private final EntityManager entityManager;

    public ShareService(FileRepository fileRepository, FileKeyRepository fileKeyRepository,
                        UserRepository userRepository, PathUtils pathUtils, EntityManager entityManager) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.userRepository = userRepository;
        this.pathUtils = pathUtils;
        this.entityManager = entityManager;
    }

    @Transactional
    public void shareFile(Long fileId, List<ShareRequestDto> requests, String ownerUsername){
        FileEntity file = fileRepository.findByIdAndOwner_Username(fileId, ownerUsername)
                .orElseThrow(() -> new FileAccessDeniedException("No tienes permisos sobre este recurso."));
        ensureOwnerHasKey(file, ownerUsername);

        for (ShareRequestDto req : requests) {
            UserEntity target = userRepository.findByUsername(req.getTargetUsername());
            if (target == null) continue;

            FileKeyEntity fileKey = fileKeyRepository.findByFileIdAndUser_Username(fileId, target.getUsername())
                    .orElse(new FileKeyEntity());
            fileKey.setFile(file);
            fileKey.setUser(target);
            fileKey.setEncryptedKey(req.getEncryptedKey());
            fileKeyRepository.save(fileKey);
        }
        fileKeyRepository.flush();
        entityManager.refresh(file);
    }

    @Transactional
    public void shareBatch(List<ShareRequestDto> requests, String ownerUsername){
        List<FileKeyEntity> keysToSave = new ArrayList<>();

        for (ShareRequestDto req : requests) {
            if (req.getTargetUsername().equals(ownerUsername)) {
                throw new InputValidationException("No puedes compartir elementos contigo mismo.");
            }

            FileEntity file = fileRepository.findById(req.getFileId())
                    .orElseThrow(() -> new InstanceNotFoundException("Elemento no encontrado"));

            if (!file.getOwner().getUsername().equals(ownerUsername)) {
                throw new FileAccessDeniedException("Acceso denegado a este elemento");
            }

            UserEntity targetUser = userRepository.findByUsername(req.getTargetUsername());
            if (targetUser == null) continue;

            FileKeyEntity fileKey = fileKeyRepository.findByFileIdAndUser_Username(req.getFileId(), req.getTargetUsername())
                    .orElse(new FileKeyEntity());

            fileKey.setFile(file);
            fileKey.setUser(targetUser);
            fileKey.setEncryptedKey(req.getEncryptedKey());

            keysToSave.add(fileKey);
        }

        if (!keysToSave.isEmpty()) {
            fileKeyRepository.saveAll(keysToSave);
        }
    }

    @Transactional
    public void revokeAccess(Long fileId, String targetUsername, String ownerUsername){
        FileEntity file = fileRepository.findByIdAndOwner_Username(fileId, ownerUsername)
                .orElseThrow(() -> new FileAccessDeniedException("Acceso denegado a este elemento"));

        fileKeyRepository.deleteByFileIdAndUser_Username(fileId, targetUsername);

        if ("application/x-directory".equals(file.getFileType())) {
            String subPath = pathUtils.join(file.getFolderPath(), file.getFileName());
            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(ownerUsername, subPath, fileId);
            descendants.forEach(c -> fileKeyRepository.deleteByFileIdAndUser_Username(c.getId(), targetUsername));
        }
    }

    public List<String> getSharedUsernames(Long fileId, String requesterUsername){
        fileRepository.findByIdAndOwner_Username(fileId, requesterUsername)
                .orElseThrow(() -> new FileAccessDeniedException("Acceso denegado a este elemento"));
        return fileKeyRepository.findUsernamesByFileId(fileId).stream()
                .filter(name -> !name.equals(requesterUsername)).collect(Collectors.toList());
    }

    private void ensureOwnerHasKey(FileEntity file, String ownerUsername) {
        boolean hasKey = file.getFileKeys().stream().anyMatch(k -> k.getUser().getUsername().equals(ownerUsername));
        if (!hasKey) {
            FileKeyEntity ownerKey = new FileKeyEntity();
            ownerKey.setFile(file);
            ownerKey.setUser(file.getOwner());
            ownerKey.setEncryptedKey("FOLDER_PERMISSION");
            fileKeyRepository.save(ownerKey);
        }
    }
}