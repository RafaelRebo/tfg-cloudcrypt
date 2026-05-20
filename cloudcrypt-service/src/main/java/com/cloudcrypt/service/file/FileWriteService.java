package com.cloudcrypt.service.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.dto.file.FileUploadRequestDto;
import com.cloudcrypt.exceptions.*;
import com.cloudcrypt.mapper.FileMapper;
import com.cloudcrypt.model.*;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.util.*;

@Service
public class FileWriteService {

    private static final Logger log = LoggerFactory.getLogger(FileWriteService.class);
    private final FileRepository fileRepository;
    private final FileKeyRepository fileKeyRepository;
    private final UserRepository userRepository;
    private final StorageUtils storageUtils;
    private final QuotaUtils quotaUtils;
    private final FileMapper fileMapper;
    private final FolderService folderService;
    private final EntityManager entityManager;

    public FileWriteService(FileRepository fileRepository, FileKeyRepository fileKeyRepository,
                            UserRepository userRepository, StorageUtils storageUtils,
                            QuotaUtils quotaUtils, FileMapper fileMapper, FolderService folderService,
                            EntityManager entityManager) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.userRepository = userRepository;
        this.storageUtils = storageUtils;
        this.quotaUtils = quotaUtils;
        this.fileMapper = fileMapper;
        this.folderService = folderService;
        this.entityManager = entityManager;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileDto uploadFile(FileUploadRequestDto request, String username) {
        UserEntity owner = userRepository.findByUsername(username);
        log.debug("OPERACIÓN: Evaluando restricciones de cuota para el usuario [{}]. Subida entrante: {} bytes.", username, request.getFile().getSize());
        quotaUtils.checkQuota(username, request.getFile().getSize());

        FileEntity parent = null;
        if (request.getParentId() != null) {
            parent = fileRepository.findById(request.getParentId())
                    .orElseThrow(() -> new InstanceNotFoundException("La carpeta de destino seleccionada ya no existe."));
        }
        String logicalPath = (parent == null) ? "/" : buildPath(parent);
        String storagePathCancel = null;

        try (InputStream is = request.getFile().getInputStream()) {
            var storage = storageUtils.saveEncryptedPackage(is, owner.getId(), logicalPath);
            storagePathCancel = storage.get("storagePath");

            FileEntity file = new FileEntity();
            file.setFileName(request.getFileName());
            file.setFileType(request.getFile().getContentType());
            file.setFileSize(request.getFile().getSize());
            file.setOwner(owner);
            file.setParent(parent);
            file.setFolderPath(logicalPath);
            file.setStoragePath(storagePathCancel);
            file.setChecksum(storage.get("checksum"));
            FileEntity saved = fileRepository.save(file);

            FileKeyEntity key = new FileKeyEntity();
            key.setFile(saved);
            key.setUser(owner);
            key.setEncryptedKey(request.getEncryptedFileKey());
            fileKeyRepository.save(key);

            log.info("OPERACIÓN: Fichero de datos guardado. ID Asignado: {}, Checksum: {}.", saved.getId(), file.getChecksum());
            return fileMapper.toDto(saved, username);
        } catch (Exception e) {
            if (storagePathCancel != null) {
                log.error("OPERACIÓN: Abortando operación y purgando: {}", storagePathCancel);
                try { storageUtils.deletePhysicalFile(storagePathCancel); } catch (Exception ignored) {}
            }
            throw new InternalStorageException("Fallo crítico al empaquetar y cifrar el flujo del archivo.");
        }
    }

    @Transactional
    public FileDto createFolder(String name, String username, Long parentId) {
        if (name == null || name.isBlank() || name.contains("/") || name.contains("..")) {
            throw new InputValidationException("El nombre de la carpeta es inválido.");
        }

        log.info("OPERACIÓN: Solicitud de creación de directorio para [{}]. Nombre: '{}'.", username, name.trim());
        FileEntity parent = null;
        if (parentId != null) {
            parent = fileRepository.findByIdAndOwner_Username(parentId, username)
                    .orElseThrow(() -> new FileAccessDeniedException("Acceso denegado al directorio contenedor."));
        }

        FileEntity folder = folderService.ensureExists(username, name.trim(), parent);
        return fileMapper.toDto(folder, username);
    }

    @Transactional
    public FileDto ensureFolderSync(String username, String folderName, Long parentId) {
        FileEntity parent = null;
        if (parentId != null) {
            parent = fileRepository.findByIdAndOwner_Username(parentId, username)
                    .orElseThrow(() -> new FileAccessDeniedException("Acceso denegado en la sincronización del lote."));
        }
        return fileMapper.toDto(folderService.ensureExists(username, folderName, parent), username);
    }

    @Transactional
    public FileDto toggleStar(Long id, String username){
        log.debug("OPERACIÓN: Cambiando estado destacado del recurso ID: {} para [{}].", id, username);
        FileKeyEntity fileKey = fileKeyRepository.findByFileIdAndUser_Username(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Acceso denegado al metadato privado."));
        fileKey.setStarred(!fileKey.isStarred());
        return fileMapper.toDto(fileKeyRepository.save(fileKey).getFile(), username);
    }

    @Transactional
    public void moveFiles(List<Long> fileIds, Long targetParentId, String username){
        FileEntity newParent = null;
        log.info("OPERACIÓN: Moviendo {} recursos hacia {}.", fileIds.size(), targetParentId);

        if (targetParentId != null) {
            newParent = fileRepository.findByIdAndOwner_Username(targetParentId, username)
                    .orElseThrow(() -> new FileAccessDeniedException("No tienes permisos sobre la carpeta de destino."));

            if (!"application/x-directory".equals(newParent.getFileType())) {
                throw new InputValidationException("El destino debe ser un directorio.");
            }
        }

        for (Long id : fileIds) {
            FileEntity entity = fileRepository.findByIdAndOwner_Username(id, username)
                    .orElseThrow(() -> new InstanceNotFoundException("Elemento a mover no encontrado."));

            if (newParent != null && "application/x-directory".equals(entity.getFileType())) {
                if (newParent.getId().equals(entity.getId()) || newParent.getFolderPath().startsWith(buildPath(entity))) {
                    throw new InputValidationException("Operación inválida: No puedes anidar un directorio dentro de sí mismo.");
                }
            }

            entity.setParent(newParent);
            entity.setFolderPath((newParent == null) ? "/" : buildPath(newParent));
            fileRepository.save(entity);
        }
        log.info("OPERACIÓN: Elementos movidos.");
    }

    @Transactional
    public FileEntity renameFile(Long id, String newName, String username){
        FileEntity file = fileRepository.findByIdAndOwner_Username(id, username)
                .orElseThrow(() -> new InputValidationException("Elemento no encontrado o acceso denegado."));

        if (file.getDeletedAt() != null) {
            throw new InputValidationException("No se puede renombrar un elemento que está en la papelera.");
        }

        log.info("OPERACIÓN: Solicitud de renombramiento para ID {}. Nombre anterior: '{}', Nombre nuevo: '{}'.", id, file.getFileName(), newName);
        Optional<FileEntity> conflict = (file.getParent() == null)
                ? fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(username, newName)
                : fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(username, newName, file.getParent().getId());

        if (conflict.isPresent() && !conflict.get().getId().equals(id)) {
            throw new InputValidationException("Ya existe un elemento con ese nombre en la ruta actual.");
        }

        String oldName = file.getFileName();

        if ("application/x-directory".equals(file.getFileType())) {
            String oldParentFullPath = file.getFolderPath().equals("/") ? "/" + oldName : file.getFolderPath() + "/" + oldName;
            String newParentFullPath = file.getFolderPath().equals("/") ? "/" + newName : file.getFolderPath() + "/" + newName;

            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(username, oldParentFullPath, file.getId());

            for (FileEntity child : descendants) {
                if (!child.getId().equals(file.getId())) {
                    String currentChildPath = child.getFolderPath();
                    if (currentChildPath.startsWith(oldParentFullPath)) {
                        String updatedPath = newParentFullPath + currentChildPath.substring(oldParentFullPath.length());
                        child.setFolderPath(updatedPath);
                        fileRepository.save(child);
                    }
                }
            }
        }

        file.setFileName(newName);
        return fileRepository.save(file);
    }

    @Transactional(rollbackFor = Exception.class)
    public void copyFiles(List<Long> fileIds, Long targetParentId, String newName, String username){
        UserEntity owner = userRepository.findByUsername(username);
        FileEntity targetParent = (targetParentId != null) ? fileRepository.findById(targetParentId).orElse(null) : null;

        String targetFolderPath = "/";
        if (targetParent != null) {
            targetFolderPath = targetParent.getFolderPath().equals("/")
                    ? "/" + targetParent.getFileName()
                    : targetParent.getFolderPath() + "/" + targetParent.getFileName();
        }

        List<FileEntity> allClonesToSave = new ArrayList<>();
        List<FileKeyEntity> allKeysToSave = new ArrayList<>();

        log.info("OPERACIÓN: Iniciando copiado masivo para el usuario [{}].", username);

        for (Long id : fileIds) {
            FileEntity source = fileRepository.findByIdAndOwner_Username(id, username)
                    .orElseThrow(() -> new InputValidationException("Elemento de origen no encontrado."));

            List<FileEntity> sourceDescendants = new ArrayList<>();
            if ("application/x-directory".equals(source.getFileType())) {
                String sourceFullPath = source.getFolderPath().equals("/") ? "/" + source.getFileName() : source.getFolderPath() + "/" + source.getFileName();
                sourceDescendants = fileRepository.findAllByOwnerAndRecursivePathList(username, sourceFullPath, source.getId());
            }

            Map<Long, FileEntity> uniqueNodesMap = new LinkedHashMap<>();
            uniqueNodesMap.put(source.getId(), source);
            for (FileEntity desc : sourceDescendants) {
                uniqueNodesMap.put(desc.getId(), desc);
            }
            Collection<FileEntity> sourceTree = uniqueNodesMap.values();

            Map<Long, FileEntity> oldIdToNewCloneMap = new HashMap<>();
            long totalBatchSizeEstimator = 0;

            for (FileEntity srcNode : sourceTree) {
                FileEntity clone = new FileEntity();
                String finalName = (srcNode.getId().equals(source.getId()) && newName != null && !newName.isEmpty())
                        ? newName : srcNode.getFileName();

                clone.setFileName(finalName);
                clone.setFileType(srcNode.getFileType());
                clone.setFileSize(srcNode.getFileSize());
                clone.setStoragePath(srcNode.getStoragePath());
                clone.setChecksum(srcNode.getChecksum());
                clone.setOwner(owner);

                totalBatchSizeEstimator += clone.getFileSize();

                if (srcNode.getId().equals(source.getId())) {
                    clone.setParent(targetParent);
                    clone.setFolderPath(targetFolderPath);
                } else {
                    String sourceRootPath = source.getFolderPath().equals("/") ? "/" + source.getFileName() : source.getFolderPath() + "/" + source.getFileName();
                    String targetRootPath = targetFolderPath.equals("/") ? "/" + finalName : targetFolderPath + "/" + finalName;

                    String relativeSubPath = srcNode.getFolderPath().substring(sourceRootPath.length());
                    clone.setFolderPath(targetRootPath + relativeSubPath);
                }

                oldIdToNewCloneMap.put(srcNode.getId(), clone);
                allClonesToSave.add(clone);
            }

            quotaUtils.checkQuota(username, totalBatchSizeEstimator);

            for (FileEntity srcNode : sourceTree) {
                FileEntity currentClone = oldIdToNewCloneMap.get(srcNode.getId());
                if (!srcNode.getId().equals(source.getId()) && srcNode.getParent() != null) {
                    FileEntity newParentClone = oldIdToNewCloneMap.get(srcNode.getParent().getId());
                    if (newParentClone != null) {
                        currentClone.setParent(newParentClone);
                    }
                }

                final Long oldSourceId = srcNode.getId();
                fileKeyRepository.findByFileIdAndUser_Username(oldSourceId, username).ifPresent(oldKey -> {
                    FileKeyEntity newKey = new FileKeyEntity();
                    newKey.setFile(currentClone);
                    newKey.setUser(owner);
                    newKey.setEncryptedKey(oldKey.getEncryptedKey());
                    newKey.setStarred(false);
                    allKeysToSave.add(newKey);
                });
            }
        }

        log.info("OPERACIÓN: Guardando {} metadatos y {} claves.", allClonesToSave.size(), allKeysToSave.size());

        fileRepository.saveAll(allClonesToSave);
        fileKeyRepository.saveAll(allKeysToSave);

        log.info("OPERACIÓN: Copiado finalizado. Duplicados {} elementos.", allClonesToSave.size());
    }

    private String buildPath(FileEntity p) {
        return p.getFolderPath().equals("/") ? "/" + p.getFileName() : p.getFolderPath() + "/" + p.getFileName();
    }
}