package com.example.service.file;

import com.example.exceptions.InstanceNotFoundException;
import com.example.model.FileEntity;
import com.example.repository.file.FileRepository;
import com.example.repository.keys.FileKeyRepository;
import com.example.util.PathUtils;
import com.example.util.StorageUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.List;

@Service
public class TrashService {
    private final FileRepository fileRepository;
    private final FileKeyRepository fileKeyRepository;
    private final StorageUtils storageUtils;
    private final PathUtils pathUtils;
    private final FolderService folderService;

    public TrashService(FileRepository fileRepository, FileKeyRepository fileKeyRepository,
                        StorageUtils storageUtils, PathUtils pathUtils, FolderService folderService) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.storageUtils = storageUtils;
        this.pathUtils = pathUtils;
        this.folderService = folderService;
    }

    @Transactional
    public void deleteFile(Long id, String username) throws Exception {
        var entityOpt = fileRepository.findByIdAndOwner_Username(id, username);

        if (entityOpt.isPresent()) {
            FileEntity entity = entityOpt.get();
            if (entity.getDeletedAt() == null) processLogicalDelete(entity);
            else processPhysicalDelete(entity);
        } else {
            // Caso Invitado: Solo borra su acceso
            fileKeyRepository.deleteByFileIdAndUser_Username(id, username);
        }
    }

    @Transactional
    public void restoreFile(Long id, String username) throws InstanceNotFoundException {
        FileEntity entity = fileRepository.findByIdAndHasAccess(id, username).orElseThrow();
        folderService.restoreParentHierarchy(entity.getOwner().getUsername(), entity.getFolderPath());
        fileRepository.restoreFile(entity.getId());

        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(username, subPath, entity.getId());
            children.forEach(c -> fileRepository.restoreFile(c.getId()));
        }
    }

    private void processLogicalDelete(FileEntity entity) {
        fileRepository.markAsDeleted(entity.getId());
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(entity.getOwner().getUsername(), subPath, entity.getId());
            children.forEach(c -> fileRepository.markAsDeleted(c.getId()));
        }
    }

    private void processPhysicalDelete(FileEntity entity) {
        if (entity.getStoragePath() != null && !"application/x-directory".equals(entity.getFileType())) {
            try { storageUtils.deletePhysicalFile(entity.getStoragePath()); } catch (IOException ignored) {}
        }
        fileRepository.delete(entity);
    }
}