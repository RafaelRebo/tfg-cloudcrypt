package com.cloudcrypt.service.file;

import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.util.PathUtils;
import com.cloudcrypt.util.StorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrashService {

    private static final Logger log = LoggerFactory.getLogger(TrashService.class);

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

    public void deleteFile(Long id, String username, boolean forcePermanent){
        log.debug("OPERACIÓN: Evaluando solicitud de borrado para recurso ID: {} (Solicitante: {}, Forzar Permanente: {})", id, username, forcePermanent);
        var entityOpt = fileRepository.findByIdAndOwner_Username(id, username);

        if (entityOpt.isPresent()) {
            FileEntity entity = entityOpt.get();

            if (forcePermanent || entity.getDeletedAt() != null) {
                log.info("OPERACIÓN: Borrando definitivamente para '{}' el elemento {}", entity.getFileName(), entity.getId());
                List<FileEntity> descendants = new ArrayList<>();
                List<String> pathsToDelete = new ArrayList<>();

                if ("application/x-directory".equals(entity.getFileType())) {
                    String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
                    descendants = fileRepository.findAllByOwnerAndRecursivePathList(
                            entity.getOwner().getUsername(), subPath, entity.getId());

                    for (FileEntity child : descendants) {
                        if (child.getStoragePath() != null && !"application/x-directory".equals(child.getFileType())) {
                            pathsToDelete.add(child.getStoragePath());
                        }
                    }
                } else {
                    if (entity.getStoragePath() != null) {
                        pathsToDelete.add(entity.getStoragePath());
                    }
                }

                executePhysicalDeleteTransaction(entity, descendants);

                int diskPurgeCounter = 0;
                for (String storagePath : pathsToDelete) {
                    try {
                        storageUtils.deletePhysicalFile(storagePath);
                        diskPurgeCounter++;
                    } catch (IOException e) {
                        log.error("OPERACIÓN: Archivo físico inamovible en: {}. Operación omitida.", storagePath);
                    }
                }
                log.info("OPERACIÓN: Borrado finalizado. Eliminados exitosamente {} ficheros del disco.", diskPurgeCounter);
            } else {
                log.info("OPERACIÓN: Marcando recurso '{}' (ID: {}) como movido a papelera.", entity.getFileName(), entity.getId());
                executeLogicalDeleteTransaction(entity);
            }
            return;
        }

        executeSharedRevocationTransaction(id, username);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restoreFile(Long id, String username){
        FileEntity entity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("No se encontró el recurso para restaurar."));

        log.info("OPERACIÓN: Solicitud de rescate de papelera para '{}' (Propietario: [@{}])", entity.getFileName(), username);
        folderService.restoreParentHierarchy(entity.getOwner().getUsername(), entity.getFolderPath());
        fileRepository.restoreFile(entity.getId());

        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(username, subPath, entity.getId());
            children.forEach(c -> fileRepository.restoreFile(c.getId()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void executeLogicalDeleteTransaction(FileEntity entity) {
        fileRepository.markAsDeleted(entity.getId());
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(
                    entity.getOwner().getUsername(), subPath, entity.getId());
            children.forEach(c -> fileRepository.markAsDeleted(c.getId()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void executePhysicalDeleteTransaction(FileEntity entity, List<FileEntity> descendants) {
        if (!descendants.isEmpty()) {
            fileRepository.deleteAllInBatch(descendants);
        }
        fileRepository.delete(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    protected void executeSharedRevocationTransaction(Long id, String username) {
        FileEntity sharedEntity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado o acceso denegado"));

        log.info("OPERACIÓN: El usuario invitado [{}] renunció a sus privilegios sobre el recurso ID: {}.", username, id);
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
}