package com.example.service;

import com.example.dto.FileDto;
import com.example.dto.FileUploadRequestDto;
import com.example.dto.ShareRequestDto;
import com.example.dto.UserDto;
import com.example.exceptions.InputValidationException;
import com.example.exceptions.InstanceNotFoundException;
import com.example.exceptions.InternalStorageException;
import com.example.mapper.FileMapper;
import com.example.model.FileEntity;
import com.example.model.FileKeyEntity;
import com.example.model.UserEntity;
import com.example.repository.file.FileRepository;
import com.example.repository.keys.FileKeyRepository;
import com.example.repository.user.UserRepository;
import com.example.util.*;
import jakarta.annotation.Nonnull;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final FileRepository fileRepository;

    private final FileKeyRepository fileKeyRepository;
    private final UserRepository userRepository;
    private final StorageUtils storageUtils;
    private final QuotaUtils quotaUtils;
    private final UserService userService;
    private final FolderService folderService;
    private final StatsService statsService;
    private final PathUtils pathUtils;
    private final FileMapper fileMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public FileService(FileRepository fileRepository, FileKeyRepository fileKeyRepository, UserRepository userRepository, StorageUtils storageUtils,
                       QuotaUtils quotaUtils, UserService userService, FolderService folderService,
                       StatsService statsService, PathUtils pathUtils, FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.storageUtils = storageUtils;
        this.quotaUtils = quotaUtils;
        this.userService = userService;
        this.folderService = folderService;
        this.statsService = statsService;
        this.pathUtils = pathUtils;
        this.fileMapper = fileMapper;
        this.userRepository = userRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public FileDto uploadFile(FileUploadRequestDto request, String username) throws Exception {

        // 1. Obtenemos el owner (Ya no autenticamos con password aquí,
        // asumimos que el filtro de seguridad ya validó el token)
        UserEntity owner = userRepository.findByUsername(username);
        if (owner == null) throw new InstanceNotFoundException("Usuario no encontrado");

        // 2. Verificamos cuota
        quotaUtils.checkQuota(username, request.getTotalBatchSize());

        // 3. Obtener carpeta padre
        FileEntity parent = (request.getParentId() != null)
                ? fileRepository.findById(request.getParentId())
                .orElseThrow(() -> new InstanceNotFoundException("Carpeta no encontrada"))
                : null;

        // 4. Lógica de reemplazo (Regla 3)
        Optional<FileEntity> existing = (parent == null)
                ? fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(username, request.getFileName())
                : fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(username, request.getFileName(), request.getParentId());

        if (existing.isPresent()) {
            FileEntity oldFile = existing.get();
            if ("application/x-directory".equals(oldFile.getFileType())) {
                throw new Exception("No puedes reemplazar una carpeta con un archivo.");
            }
            if (oldFile.getStoragePath() != null) {
                storageUtils.deletePhysicalFile(oldFile.getStoragePath());
            }
            fileRepository.delete(oldFile);
            fileRepository.flush();
        }

        // 5. Construcción de ruta lógica
        String logicalPath;
        if (parent == null) {
            logicalPath = "/";
        } else {
            String base = parent.getFolderPath();
            logicalPath = base.endsWith("/") ? base + parent.getFileName() : base + "/" + parent.getFileName();
        }

        String storagePathCancel = null;

        try {
            // --- CAMBIO CLAVE ---
            // Para que el servidor cifre, necesitamos la llave AES.
            // IMPORTANTE: Aquí deberías pasar la llave que el servidor usará para cifrar.
            // Si el cliente ya manda el archivo cifrado, storageUtils debería solo guardar.
            // Si el servidor cifra, el cliente debe mandar la 'rawFileKey' en el DTO (base64).

            Map<String, String> storageResult = storageUtils.saveEncryptedPackage(
                    request.getFile().getInputStream(),
                    username,
                    logicalPath
            );

            storagePathCancel = storageResult.get("storagePath");

            // 6. Creación de la entidad File
            FileEntity newFile = new FileEntity();
            newFile.setFileName(request.getFileName());
            newFile.setFileType(request.getFile().getContentType());
            newFile.setFileSize(request.getFile().getSize());
            newFile.setOwner(owner);
            newFile.setParent(parent);
            newFile.setFolderPath(logicalPath);
            newFile.setStoragePath(storageResult.get("storagePath"));
            newFile.setChecksum(storageResult.get("checksum"));
            FileEntity savedFile = fileRepository.save(newFile);

            // 2. GUARDAR LA LLAVE (Sobre Digital): Crucial para Zero-Knowledge
            FileKeyEntity fileKey = new FileKeyEntity();
            fileKey.setFile(savedFile);
            fileKey.setUser(owner);
            fileKey.setEncryptedKey(request.getEncryptedFileKey()); // Esta es la AES cifrada con RSA del cliente
            fileKeyRepository.save(fileKey);

            return fileMapper.toDto(savedFile);

        } catch (Exception e) {
            if (storagePathCancel != null) {
                try { storageUtils.deletePhysicalFile(storagePathCancel); } catch (IOException ignored) {}
            }
            throw e;
        }
    }

    // En FileService.java
    public String getEncryptedFileKey(Long fileId, String username) throws InstanceNotFoundException {
        // Buscamos la llave asociada a ese archivo Y a ese usuario específico
        return fileKeyRepository.findByFileIdAndUser_Username(fileId, username)
                .map(FileKeyEntity::getEncryptedKey)
                .orElseThrow(() -> new InstanceNotFoundException("No tienes acceso a la llave de este archivo"));
    }

    @Transactional
    public void shareFile(Long fileId, List<ShareRequestDto> requests, String ownerUsername) throws Exception {
        FileEntity file = fileRepository.findById(fileId)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado"));

        // Seguridad: Solo el dueño puede compartir
        if (!file.getOwner().getUsername().equals(ownerUsername)) {
            throw new Exception("No tienes permisos para compartir este archivo");
        }

        for (ShareRequestDto req : requests) {
            UserEntity targetUser = userRepository.findByUsername(req.getTargetUsername());
            if (targetUser == null) continue;

            // Si ya está compartido con él, actualizamos la llave o ignoramos
            FileKeyEntity fileKey = fileKeyRepository.findByFileIdAndUserId(fileId, targetUser.getId())
                    .orElse(new FileKeyEntity());

            fileKey.setFile(file);
            fileKey.setUser(targetUser);
            fileKey.setEncryptedKey(req.getEncryptedKey());

            fileKeyRepository.save(fileKey);
        }
    }

    // En FileService.java

    public List<FileDto> getRecursiveFilesForSharing(Long folderId, String username) throws Exception {
        FileEntity folder = fileRepository.findByIdAndOwner_Username(folderId, username)
                .orElseThrow(() -> new Exception("Carpeta no encontrada"));

        String fullPath = folder.getFolderPath().equals("/")
                ? "/" + folder.getFileName()
                : folder.getFolderPath() + "/" + folder.getFileName();

        // Pasamos el ID para incluir la carpeta base en el paquete de compartición
        List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(username, fullPath, folderId);

        return descendants.stream().map(fileMapper::toDto).collect(Collectors.toList());
    }


    public FileDto ensureFolderSync(String username, String folderName, Long parentId){
        FileEntity parent = (parentId != null)
                ? fileRepository.findById(parentId).orElse(null)
                : null;

        // El FolderService.ensureExists debe devolver la FileEntity creada o encontrada
        FileEntity folder = folderService.ensureExists(username, folderName, parent);
        return fileMapper.toDto(folder);
    }

    public Map<String, Object> checkExistsById(String username, String fileName, Long parentId) {
        Optional<FileEntity> existing = (parentId == null)
                ? fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(username, fileName)
                : fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(username, fileName, parentId);

        Map<String, Object> response = new java.util.HashMap<>();
        response.put("exists", existing.isPresent());

        if (existing.isPresent()) {
            response.put("existingId", existing.get().getId());
            response.put("suggestedName", generateUniqueName(fileName, existing.get().getFolderPath(), username));
        }
        return response;
    }

    @Transactional
    public FileDto createFolder(String name, String username, Long parentId) throws InputValidationException {
        if (name.contains("/") || name.contains("\\")) {
            throw new InputValidationException("Nombre de carpeta inválido: no puede contener /");
        }
        UserEntity owner = userRepository.findByUsername(username);
        FileEntity parent = (parentId != null) ? fileRepository.findById(parentId).orElse(null) : null;

        FileEntity newFolder = getFileEntity(name, owner, parent);

        // Guardado directo sin validaciones de nombre (las validaciones las hace el Front con el aviso)
        FileEntity saved = fileRepository.save(newFolder);
        return fileMapper.toDto(saved);
    }

    // En FileService.java
    @Transactional
    public void moveFiles(List<Long> fileIds, Long targetParentId, String username) throws Exception {
        FileEntity newParent = (targetParentId != null)
                ? fileRepository.findById(targetParentId).orElseThrow(() -> new Exception("Carpeta destino no encontrada"))
                : null;

        for (Long id : fileIds) {
            FileEntity entity = fileRepository.findByIdAndOwner_Username(id, username)
                    .orElseThrow(() -> new Exception("Archivo no encontrado"));

            // Evitar bucles infinitos (no mover carpeta dentro de sí misma)
            if (newParent != null && isChildOf(newParent, entity)) continue;

            entity.setParent(newParent);

            // Recalculamos la ruta base
            String newBasePath = (newParent == null) ? "/" :
                    (newParent.getFolderPath().equals("/") ? "/" + newParent.getFileName() : newParent.getFolderPath() + "/" + newParent.getFileName());

            entity.setFolderPath(newBasePath);
            fileRepository.save(entity);

            // Si es carpeta, actualizamos a todos los descendientes recursivamente
            if ("application/x-directory".equals(entity.getFileType())) {
                updateChildrenPaths(entity, newBasePath + "/" + entity.getFileName());
            }
        }
    }

    private void updateChildrenPaths(FileEntity folder, String newFolderPath) {
        for (FileEntity child : folder.getChildren()) {
            child.setFolderPath(newFolderPath);
            fileRepository.save(child);
            if ("application/x-directory".equals(child.getFileType())) {
                updateChildrenPaths(child, newFolderPath + "/" + child.getFileName());
            }
        }
    }

    private boolean isChildOf(FileEntity potentialParent, FileEntity folder) {
        FileEntity current = potentialParent;
        while (current != null) {
            if (current.getId().equals(folder.getId())) return true;
            current = current.getParent();
        }
        return false;
    }

    @Nonnull
    private static FileEntity getFileEntity(String name, UserEntity owner, FileEntity parent) {
        FileEntity newFolder = new FileEntity();
        newFolder.setFileName(name);
        newFolder.setFileType("application/x-directory");
        newFolder.setOwner(owner);
        newFolder.setParent(parent);
        newFolder.setFileSize(0L);

        String derivedPath = "/";
        if (parent != null) {
            String base = parent.getFolderPath();
            derivedPath = base.equals("/") ? "/" + parent.getFileName() : base + "/" + parent.getFileName();
        }
        newFolder.setFolderPath(derivedPath);
        return newFolder;
    }

    // Cambia el parámetro 'String folder' por 'Long parentId'
    public Page<FileDto> getFilesByFolder(String username, Long parentId, String category, Pageable pageable) {

        // 1. Si estamos en la RAIZ de la papelera (parentId es null)
        if ("trash".equals(category) && parentId == null) {
            return fileRepository.findTrashRoot(username, pageable).map(fileMapper::toDto);
        }
        // Dentro de getFilesByFolder...
        if ("shared".equals(category)) {
            return fileRepository.findSharedWithMe(username, parentId, pageable).map(fileMapper::toDto);
        }

        // 2. Si estamos DENTRO de una carpeta (parentId NO es null)
        if (parentId != null) {
            // IMPORTANTE: Aquí buscamos los hijos directos del ID.
            // No filtramos por deletedAt porque si la carpeta padre está borrada,
            // queremos ver sus hijos aunque también estén borrados.
            return fileRepository.findByOwner_UsernameAndParentId(username, parentId, pageable)
                    .map(fileMapper::toDto);
        }

        // 3. Lógica para categorías (fotos, etc) - Solo activos
        String pattern = getMimePattern(category);
        if (pattern != null) {
            return fileRepository.findByCategory(username, pattern, pageable).map(fileMapper::toDto);
        }

        // 4. Raíz de Mis Archivos (parentId null y category all)
        return fileRepository.findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull(username, pageable)
                .map(fileMapper::toDto);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteFile(Long id, String username) throws Exception {
        // 1. Intentamos buscar como dueño primero
        Optional<FileEntity> entityOpt = fileRepository.findByIdAndOwner_Username(id, username);

        if (entityOpt.isPresent()) {
            // --- CAMINO DEL DUEÑO ---
            FileEntity entity = entityOpt.get();
            if (entity.getDeletedAt() == null) {
                processLogicalDelete(entity);
            } else {
                processPhysicalDelete(entity);
            }
            return; // Fin del proceso para dueños
        }

        // 2. Si no es dueño, buscamos si tiene acceso (Invitado)
        FileEntity sharedEntity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado o acceso denegado"));

        // --- CAMBIO PARA INVITADO: BORRADO DIRECTO DE LLAVES ---
        // Borramos la llave del elemento principal
        fileKeyRepository.deleteByFileIdAndUser_Username(id, username);

        // Si es carpeta, borramos recursivamente las llaves de todos los descendientes para este invitado
        if ("application/x-directory".equals(sharedEntity.getFileType())) {
            String subPath = pathUtils.join(sharedEntity.getFolderPath(), sharedEntity.getFileName());

            // Obtenemos todos los descendientes basándonos en la propiedad del dueño real
            List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(
                    sharedEntity.getOwner().getUsername(), subPath, sharedEntity.getId());

            for (FileEntity child : descendants) {
                fileKeyRepository.deleteByFileIdAndUser_Username(child.getId(), username);
            }
        }
    }

    @Transactional
    public FileDto restoreFile(Long id, String username) throws InstanceNotFoundException {
        FileEntity entity = findOrThrow(id, username);
        folderService.restoreParentHierarchy(entity.getOwner().getUsername(), entity.getFolderPath());

        applyRecursiveAction(entity, fileRepository::restoreFile);
        return fileMapper.toDto(entity);
    }

    public InputStream getFileDownloadStream(Long id, String username) throws Exception {
        FileEntity entity = findOrThrow(id, username);

        if ("application/x-directory".equals(entity.getFileType())) {
            throw new InternalStorageException("No es descargable");
        }

        // Verificamos que el archivo físico existe
        if (!storageUtils.exists(entity.getStoragePath())) {
            throw new InternalStorageException("Archivo físico no encontrado en el servidor");
        }

        // Retornamos el chorro de bytes directamente desde el repositorio (sin Cipher)
        return storageUtils.getRawStream(entity.getStoragePath());
    }

    public Map<String, Object> getUserStats(String username) {
        return statsService.getUserStats(username);
    }

    public FileDto getFileById(Long id, String username) throws InstanceNotFoundException {
        return fileMapper.toDto(findOrThrow(id, username));
    }

    public Page<FileDto> searchFiles(String username, String query, Pageable pageable) {
        if (query == null || query.trim().isEmpty()) {
            return Page.empty();
        }

        return fileRepository.searchByName(username, query.trim(), pageable)
                .map(fileMapper::toDto);
    }

    // En FileService.java
    public String generateUniqueName(String fileName, String folderPath, String username) {
        String name = fileName;
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');

        if (lastDot > 0) {
            name = fileName.substring(0, lastDot);
            extension = fileName.substring(lastDot);
        }

        int counter = 1;
        String finalName = fileName;

        // Bucle para encontrar un nombre tipo "archivo (1).txt", "archivo (2).txt"...
        while (fileRepository.existsByOwner_UsernameAndFileNameAndFolderPathAndDeletedAtIsNull(username, finalName, folderPath)) {
            finalName = name + " (" + counter + ")" + extension;
            counter++;
        }
        return finalName;
    }

    // --- Métodos Privados de Soporte ---

    // Sustituye el método findOrThrow al final de FileService.java
    private FileEntity findOrThrow(Long id, String username) throws InstanceNotFoundException {
        return fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado o acceso denegado"));
    }

    private void processLogicalDelete(FileEntity entity) {
        // Marcamos el elemento actual
        fileRepository.markAsDeleted(entity.getId());

        // Si es carpeta, buscamos todos los hijos por su ruta lógica y los marcamos
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(
                    entity.getOwner().getUsername(), subPath, entity.getId());

            for (FileEntity child : children) {
                fileRepository.markAsDeleted(child.getId());
            }
        }
    }


    @Transactional(rollbackFor = Exception.class)
    private void processPhysicalDelete(FileEntity entity) {
        // 1. Borramos los archivos físicos del disco (solo si es un archivo real, no carpeta)
        // Usamos el método recursivo con entity que ya tienes para limpiar el almacenamiento físico
        applyRecursiveActionWithEntity(entity, e -> {
            if (e.getStoragePath() != null && !"application/x-directory".equals(e.getFileType())) {
                try {
                    storageUtils.deletePhysicalFile(e.getStoragePath());
                } catch (IOException ignored) {
                    // Si el archivo ya no estaba en disco, ignoramos para no bloquear el borrado en BD
                }
            }
        });

        // 2. Borramos de la base de datos
        // JPA se encargará de borrar todos los hijos de la tabla gracias al Cascade
        fileRepository.delete(entity);
        fileRepository.flush(); // Forzamos para que el error salte aquí si hay conflicto
    }

    private void removeGuestAccessRecursively(FileEntity folder, String username) {
        String subPath = pathUtils.join(folder.getFolderPath(), folder.getFileName());
        List<FileEntity> descendants = fileRepository.findAllByOwnerAndRecursivePathList(
                folder.getOwner().getUsername(),
                subPath,
                folder.getId()
        );

        for (FileEntity child : descendants) {
            fileKeyRepository.deleteByFileIdAndUser_Username(child.getId(), username);
        }
    }

    private void applyRecursiveAction(FileEntity entity, java.util.function.Consumer<Long> action) {
        if (entity == null) return;

        // Ejecutar acción en el padre
        action.accept(entity.getId());

        // Si es carpeta, ejecutar en los hijos
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            // Usamos una lista para evitar problemas con el Stream abierto durante la persistencia
            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePathList(
                    entity.getOwner().getUsername(),
                    subPath,
                    entity.getId()
            );

            for (FileEntity child : children) {
                if (!child.getId().equals(entity.getId())) { // Evitar procesar al padre dos veces
                    action.accept(child.getId());
                }
            }
        }
    }

    private void applyRecursiveActionWithEntity(FileEntity entity, java.util.function.Consumer<FileEntity> action) {
        action.accept(entity);
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            try (java.util.stream.Stream<FileEntity> childStream =
                         fileRepository.findAllByOwnerAndRecursivePath(entity.getOwner().getUsername(), subPath)) {
                childStream.forEach(action);
            }
        }
    }

    private String getMimePattern(String category) {
        return switch (category != null ? category : "all") {
            case "image" -> "image/%";
            case "video" -> "video/%";
            case "audio" -> "audio/%";
            case "document" -> "%pdf%";
            case "all" -> null;
            default -> "%";
        };
    }
}