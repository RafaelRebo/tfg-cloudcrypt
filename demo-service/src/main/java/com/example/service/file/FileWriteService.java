package com.example.service.file;

import com.example.dto.file.FileDto;
import com.example.dto.file.FileUploadRequestDto;
import com.example.exceptions.*;
import com.example.mapper.FileMapper;
import com.example.model.*;
import com.example.repository.file.FileRepository;
import com.example.repository.keys.FileKeyRepository;
import com.example.repository.user.UserRepository;
import com.example.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

@Service
public class FileWriteService {
    private final FileRepository fileRepository;
    private final FileKeyRepository fileKeyRepository;
    private final UserRepository userRepository;
    private final StorageUtils storageUtils;
    private final QuotaUtils quotaUtils;
    private final FileMapper fileMapper;
    private final FolderService folderService;

    public FileWriteService(FileRepository fileRepository, FileKeyRepository fileKeyRepository,
                            UserRepository userRepository, StorageUtils storageUtils,
                            QuotaUtils quotaUtils, FileMapper fileMapper, FolderService folderService) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.userRepository = userRepository;
        this.storageUtils = storageUtils;
        this.quotaUtils = quotaUtils;
        this.fileMapper = fileMapper;
        this.folderService = folderService;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileDto uploadFile(FileUploadRequestDto request, String username) throws Exception {
        UserEntity owner = userRepository.findByUsername(username);
        quotaUtils.checkQuota(username, request.getFile().getSize());

        FileEntity parent = (request.getParentId() != null) ? fileRepository.findById(request.getParentId()).orElseThrow() : null;
        String logicalPath = (parent == null) ? "/" : buildPath(parent);
        String storagePathCancel = null;

        try (InputStream is = request.getFile().getInputStream()) {
            var storage = storageUtils.saveEncryptedPackage(is, username, logicalPath);
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

            return fileMapper.toDto(saved);
        } catch (Exception e) {
            if (storagePathCancel != null) storageUtils.deletePhysicalFile(storagePathCancel);
            throw e;
        }
    }

    @Transactional
    public FileDto createFolder(String name, String username, Long parentId) throws InputValidationException {
        UserEntity owner = userRepository.findByUsername(username);
        FileEntity parent = (parentId != null) ? fileRepository.findById(parentId).orElse(null) : null;
        FileEntity folder = folderService.ensureExists(username, name, parent);
        return fileMapper.toDto(folder);
    }

    @Transactional
    public FileDto ensureFolderSync(String username, String folderName, Long parentId) {
        FileEntity parent = (parentId != null) ? fileRepository.findById(parentId).orElse(null) : null;
        return fileMapper.toDto(folderService.ensureExists(username, folderName, parent));
    }

    @Transactional
    public FileDto toggleStar(Long id, String username) throws InstanceNotFoundException {
        FileKeyEntity fileKey = fileKeyRepository.findByFileIdAndUser_Username(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Acceso denegado"));
        fileKey.setStarred(!fileKey.isStarred());
        return fileMapper.toDto(fileKeyRepository.save(fileKey).getFile());
    }

    @Transactional
    public void moveFiles(List<Long> fileIds, Long targetParentId, String username) throws Exception {
        FileEntity newParent = (targetParentId != null) ? fileRepository.findById(targetParentId).orElseThrow() : null;
        for (Long id : fileIds) {
            FileEntity entity = fileRepository.findByIdAndOwner_Username(id, username).orElseThrow();
            entity.setParent(newParent);
            entity.setFolderPath((newParent == null) ? "/" : buildPath(newParent));
            fileRepository.save(entity);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public FileEntity renameFile(Long id, String newName, String username) {
        // 1. Validamos que el archivo exista y pertenezca al usuario activo
        FileEntity file = fileRepository.findByIdAndOwner_Username(id, username)
                .orElseThrow(() -> new InputValidationException("Elemento no encontrado o acceso denegado"));

        if (file.getDeletedAt() != null) {
            throw new InputValidationException("No se puede renombrar un elemento que está en la papelera");
        }

        // 2. Verificamos que no cause un conflicto de duplicados en el mismo nivel
        Optional<FileEntity> conflict = (file.getParent() == null)
                ? fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(username, newName)
                : fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(username, newName, file.getParent().getId());

        if (conflict.isPresent() && !conflict.get().getId().equals(id)) {
            throw new InputValidationException("Ya existe un archivo o carpeta con ese nombre en este directorio");
        }

        String oldName = file.getFileName();

        if ("application/x-directory".equals(file.getFileType())) {
            String oldParentFullPath = file.getFolderPath().equals("/")
                    ? "/" + oldName
                    : file.getFolderPath() + "/" + oldName;

            String newParentFullPath = file.getFolderPath().equals("/")
                    ? "/" + newName
                    : file.getFolderPath() + "/" + newName;

            // Recuperamos todos los hijos recursivos usando la consulta limpia que reparamos antes
            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(
                    username, oldParentFullPath, file.getId());

            for (FileEntity child : descendants) {
                if (!child.getId().equals(file.getId())) {
                    String currentChildPath = child.getFolderPath();
                    // Reemplazamos el viejo prefijo de la carpeta por el nuevo nombre otorgado
                    if (currentChildPath.startsWith(oldParentFullPath)) {
                        String updatedPath = newParentFullPath + currentChildPath.substring(oldParentFullPath.length());
                        child.setFolderPath(updatedPath);
                        fileRepository.save(child);
                    }
                }
            }
        }

        // 4. Renombramos el elemento principal y consolidamos en la BD
        file.setFileName(newName);
        return fileRepository.save(file);
    }

    private String buildPath(FileEntity p) {
        return p.getFolderPath().equals("/") ? "/" + p.getFileName() : p.getFolderPath() + "/" + p.getFileName();
    }
}