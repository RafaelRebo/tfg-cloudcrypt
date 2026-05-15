package com.example.service.file;

import com.example.dto.file.ShareRequestDto;
import com.example.model.*;
import com.example.repository.file.FileRepository;
import com.example.repository.keys.FileKeyRepository;
import com.example.repository.user.UserRepository;
import com.example.util.PathUtils;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public void shareFile(Long fileId, List<ShareRequestDto> requests, String ownerUsername) throws Exception {
        FileEntity file = fileRepository.findById(fileId).orElseThrow();
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
    public void revokeAccess(Long fileId, String targetUsername, String ownerUsername) throws Exception {
        FileEntity file = fileRepository.findByIdAndOwner_Username(fileId, ownerUsername).orElseThrow();
        fileKeyRepository.deleteByFileIdAndUser_Username(fileId, targetUsername);

        if ("application/x-directory".equals(file.getFileType())) {
            String subPath = pathUtils.join(file.getFolderPath(), file.getFileName());
            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(ownerUsername, subPath, fileId);
            descendants.forEach(c -> fileKeyRepository.deleteByFileIdAndUser_Username(c.getId(), targetUsername));
        }
    }

    public List<String> getSharedUsernames(Long fileId, String requesterUsername) throws Exception {
        fileRepository.findByIdAndOwner_Username(fileId, requesterUsername).orElseThrow();
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