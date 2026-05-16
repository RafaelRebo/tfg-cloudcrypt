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

    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id, String username) throws Exception {
        // 1. Intentamos buscar como dueño
        var entityOpt = fileRepository.findByIdAndOwner_Username(id, username);

        if (entityOpt.isPresent()) {
            FileEntity entity = entityOpt.get();
            if (entity.getDeletedAt() == null) processLogicalDelete(entity);
            else processPhysicalDelete(entity);
            return;
        }

        FileEntity sharedEntity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado o acceso denegado"));

        fileKeyRepository.deleteByFileIdAndUser_Username(id, username);

        if ("application/x-directory".equals(sharedEntity.getFileType())) {
            String subPath = pathUtils.join(sharedEntity.getFolderPath(), sharedEntity.getFileName());

            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(
                    sharedEntity.getOwner().getUsername(), subPath, sharedEntity.getId());

            for (FileEntity child : descendants) {
                fileKeyRepository.deleteByFileIdAndUser_Username(child.getId(), username);
            }
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
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(
                    entity.getOwner().getUsername(), subPath, entity.getId());
            children.forEach(c -> fileRepository.markAsDeleted(c.getId()));
        }
    }

    private void processPhysicalDelete(FileEntity entity) {
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(
                    entity.getOwner().getUsername(), subPath, entity.getId());

            for (FileEntity child : descendants) {
                if (child.getStoragePath() != null && !"application/x-directory".equals(child.getFileType())) {
                    try {
                        storageUtils.deletePhysicalFile(child.getStoragePath());
                    } catch (IOException ignored) {}
                }
                fileRepository.delete(child);
            }
        } else {
            if (entity.getStoragePath() != null) {
                try {
                    storageUtils.deletePhysicalFile(entity.getStoragePath());
                } catch (IOException ignored) {}
            }
        }

        // 2. Finalmente, eliminamos el registro de la entidad principal de la BD
        fileRepository.delete(entity);
    }
}