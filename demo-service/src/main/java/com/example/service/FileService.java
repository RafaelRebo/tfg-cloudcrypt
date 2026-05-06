package com.example.service;

import com.example.dto.FileDto;
import com.example.dto.UserDto;
import com.example.exceptions.InputValidationException;
import com.example.exceptions.InstanceNotFoundException;
import com.example.exceptions.InternalStorageException;
import com.example.mapper.FileMapper;
import com.example.model.FileEntity;
import com.example.model.UserEntity;
import com.example.repository.file.FileRepository;
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

@Service
public class FileService {

    private final FileRepository fileRepository;
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

    public FileService(FileRepository fileRepository, UserRepository userRepository, StorageUtils storageUtils,
                       QuotaUtils quotaUtils, UserService userService, FolderService folderService,
                       StatsService statsService, PathUtils pathUtils, FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.storageUtils = storageUtils;
        this.quotaUtils = quotaUtils;
        this.userService = userService;
        this.folderService = folderService;
        this.statsService = statsService;
        this.pathUtils = pathUtils;
        this.fileMapper = fileMapper;
        this.userRepository = userRepository;
    }

    // --- DENTRO DE FileService.java ---

    @Transactional(rollbackFor = Exception.class)
    public FileDto uploadFile(MultipartFile file, String username, String rawPassword,
                              Long parentId, String fileName, Long totalBatchSize) throws Exception {

        UserDto userDto = userService.authenticate(username, rawPassword);
        UserEntity owner = entityManager.getReference(UserEntity.class, userDto.getId());

        quotaUtils.checkQuota(username, totalBatchSize);

        FileEntity parent = (parentId != null)
                ? fileRepository.findById(parentId).orElseThrow(() -> new InstanceNotFoundException("Carpeta no encontrada"))
                : null;

        // REGLA 3: Reemplazo explícito.
        // Si el usuario eligió "Reemplazar", el Front envía el nombre EXACTO.
        // Solo borramos si existe un archivo con ese nombre EXACTO en ese padre.
        Optional<FileEntity> existing = (parent == null)
                ? fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(username, fileName)
                : fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(username, fileName, parentId);

        if (existing.isPresent()) {
            FileEntity oldFile = existing.get();
            // Si es una carpeta, no permitimos que un archivo la machaque (error de lógica)
            if ("application/x-directory".equals(oldFile.getFileType())) {
                throw new Exception("No puedes reemplazar una carpeta con un archivo.");
            }

            // FÍSICAMENTE: Borramos el viejo para dejar sitio al nuevo
            if (oldFile.getStoragePath() != null) {
                storageUtils.deletePhysicalFile(oldFile.getStoragePath());
            }
            fileRepository.delete(oldFile);
            fileRepository.flush(); // Limpiar espacio en BD para el nuevo insert
        }

        String logicalPath = (parent == null) ? "/" :
                (parent.getFolderPath().equals("/") ? "/" + parent.getFileName() : parent.getFolderPath() + "/" + parent.getFileName());

        String storagePathCancel = null;

        try {
            Map<String, String> storageResult = storageUtils.encryptAndSave(file.getInputStream(), username, logicalPath, rawPassword);
            storagePathCancel = storageResult.get("storagePath");
            FileEntity newFile = new FileEntity();
            newFile.setFileName(fileName);
            newFile.setFileType(file.getContentType());
            newFile.setFileSize(file.getSize());
            newFile.setOwner(owner);
            newFile.setParent(parent);
            newFile.setFolderPath(logicalPath);
            newFile.setStoragePath(storagePathCancel);
            newFile.setSalt(storageResult.get("salt"));
            newFile.setChecksum(storageResult.get("checksum"));

            return fileMapper.toDto(fileRepository.save(newFile));
        } catch (Exception e) {
            // Si hay un error de red (Aborted) o de escritura, borramos el archivo físico si se creó
            if (storagePathCancel != null) {
                try { storageUtils.deletePhysicalFile(storagePathCancel); } catch (IOException ignored) {}
            }
            throw e; // Relanzamos para que actúe @Transactional
        }
    }

    // REGLA 1: Crear carpeta manual permite coexistencia


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

    @Transactional
    public void deleteFile(Long id, String username) throws Exception {
        FileEntity entity = findOrThrow(id, username);
        if (entity.getDeletedAt() == null) {
            processLogicalDelete(entity);
        } else {
            processPhysicalDelete(entity);
        }
    }

    @Transactional
    public FileDto restoreFile(Long id, String username) throws InstanceNotFoundException {
        FileEntity entity = findOrThrow(id, username);
        folderService.restoreParentHierarchy(entity.getOwner().getUsername(), entity.getFolderPath());

        applyRecursiveAction(entity, fileRepository::restoreFile);
        return fileMapper.toDto(entity);
    }

    public InputStream getFileDownloadStream(Long id, String username, String password) throws Exception {
        FileEntity entity = findOrThrow(id, username);
        if ("application/x-directory".equals(entity.getFileType())) throw new InternalStorageException("No es descargable");
        if (!storageUtils.exists(entity.getStoragePath())) throw new InternalStorageException("Archivo físico no encontrado");

        return storageUtils.getDecryptedStream(entity.getStoragePath(), password, entity.getSalt());
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

    private FileEntity findOrThrow(Long id, String username) throws InstanceNotFoundException {
        return fileRepository.findByIdAndOwner_Username(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado o acceso denegado"));
    }

    private void processLogicalDelete(FileEntity entity) {
        applyRecursiveAction(entity, fileRepository::markAsDeleted);
    }

    @Transactional(rollbackFor = Exception.class)
    private void processPhysicalDelete(FileEntity entity) {
        // 1. Si es una carpeta, borramos primero recursivamente el contenido de los hijos
        if ("application/x-directory".equals(entity.getFileType())) {
            for (FileEntity child : entity.getChildren()) {
                processPhysicalDelete(child);
            }
        }

        // 2. Borramos el archivo físico del disco si no es una carpeta
        if (entity.getStoragePath() != null) {
            try {
                storageUtils.deletePhysicalFile(entity.getStoragePath());
            } catch (IOException e) {
                // Log de error pero continuamos para no bloquear la limpieza
            }
        }

        // 3. Al final, JPA borrará el registro de la tabla gracias a la cascada
        fileRepository.delete(entity);
    }

    private void applyRecursiveAction(FileEntity entity, java.util.function.Consumer<Long> action) {
        action.accept(entity.getId());
        if ("application/x-directory".equals(entity.getFileType())) {
            String subPath = pathUtils.join(entity.getFolderPath(), entity.getFileName());
            try (java.util.stream.Stream<FileEntity> childStream =
                         fileRepository.findAllByOwnerAndRecursivePath(entity.getOwner().getUsername(), subPath)) {
                childStream.forEach(child -> {
                    action.accept(child.getId());
                });
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