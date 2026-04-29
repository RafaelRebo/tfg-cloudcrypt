package com.example.service;

import com.example.dto.FileDto;
import com.example.dto.UserDto;
import com.example.exceptions.InstanceNotFoundException;
import com.example.exceptions.InternalStorageException;
import com.example.mapper.FileMapper;
import com.example.model.FileEntity;
import com.example.model.UserEntity;
import com.example.repository.file.FileRepository;
import com.example.util.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
public class FileService {

    private final FileRepository fileRepository;
    private final StorageUtils storageUtils;
    private final QuotaUtils quotaUtils;
    private final UserService userService;
    private final FolderService folderService;
    private final StatsService statsService;
    private final PathUtils pathUtils;
    private final FileMapper fileMapper;

    @PersistenceContext
    private EntityManager entityManager;

    public FileService(FileRepository fileRepository, StorageUtils storageUtils,
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
    }

    @Transactional
    public FileDto uploadFile(MultipartFile file, String username, String rawPassword, String folderPath, String fileName) throws Exception {
        String cleanPath = pathUtils.sanitize(folderPath);
        UserDto user = userService.authenticate(username, rawPassword);
        UserEntity owner = entityManager.getReference(UserEntity.class, user.getId());

        quotaUtils.checkQuota(username, file.getSize());
        folderService.ensureExists(username, cleanPath);

        Map<String, String> storage = storageUtils.encryptAndSave(file.getInputStream(), username, cleanPath, rawPassword);

        FileEntity entity = fileRepository.createFile(
                fileName,
                cleanPath,
                file.getContentType(),
                file.getSize(),
                storage.get("checksum"), // Usamos el checksum generado por StorageUtils
                storage.get("storagePath"),
                owner,
                storage.get("salt")
        );

        return fileMapper.toDto(entity);
    }

    @Transactional
    public FileDto createFolder(String name, String username, String password, String path) throws Exception {
        userService.authenticate(username, password);
        quotaUtils.checkQuota(username, 0);
        return fileMapper.toDto(fileRepository.createFolder(name, pathUtils.sanitize(path), username));
    }

    public Page<FileDto> getFilesByFolder(String username, String folder, String category, Pageable pageable) {
        String cleanPath = pathUtils.sanitize(folder);

        if ("trash".equals(category)) {
            return ("/".equals(cleanPath)
                    ? fileRepository.findTrash(username, pageable)
                    : fileRepository.findByOwner_UsernameAndFolderPathAndDeletedAtIsNotNull(username, cleanPath, pageable))
                    .map(fileMapper::toDto);
        }

        String pattern = getMimePattern(category);
        return (pattern != null
                ? fileRepository.findByCategory(username, pattern, pageable)
                : fileRepository.findByOwner_UsernameAndFolderPathAndDeletedAtIsNull(username, cleanPath, pageable))
                .map(fileMapper::toDto);
    }

    @Transactional
    public void deleteFile(Long id) throws Exception {
        FileEntity entity = findOrThrow(id);
        if (entity.getDeletedAt() == null) {
            processLogicalDelete(entity);
        } else {
            processPhysicalDelete(entity);
        }
    }

    @Transactional
    public FileDto restoreFile(Long id) throws InstanceNotFoundException {
        FileEntity entity = findOrThrow(id);
        folderService.restoreParentHierarchy(entity.getOwner().getUsername(), entity.getFolderPath());

        applyRecursiveAction(entity, fileRepository::restoreFile);
        return fileMapper.toDto(entity);
    }

    public InputStream getFileDownloadStream(Long id, String password) throws Exception {
        FileEntity entity = findOrThrow(id);
        if ("application/x-directory".equals(entity.getFileType())) throw new InternalStorageException("No es descargable");
        if (!storageUtils.exists(entity.getStoragePath())) throw new InternalStorageException("Archivo físico no encontrado");

        return storageUtils.getDecryptedStream(entity.getStoragePath(), password, entity.getSalt());
    }

    public Map<String, Object> getUserStats(String username) {
        return statsService.getUserStats(username);
    }

    public FileDto getFileById(Long id) throws InstanceNotFoundException {
        return fileMapper.toDto(findOrThrow(id));
    }

    // --- Métodos Privados de Soporte ---

    private FileEntity findOrThrow(Long id) throws InstanceNotFoundException {
        return fileRepository.findById(id).orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado"));
    }

    private void processLogicalDelete(FileEntity entity) {
        applyRecursiveAction(entity, fileRepository::markAsDeleted);
    }

    private void processPhysicalDelete(FileEntity entity) {
        // Usamos el objeto directamente en lugar de solo el ID para evitar findById extras
        applyRecursiveActionWithEntity(entity, f -> {
            if (f.getStoragePath() != null) {
                try {
                    storageUtils.deletePhysicalFile(f.getStoragePath());
                } catch (IOException e) {
                    throw new RuntimeException("Error borrando archivo físico: " + f.getFileName(), e);
                }
            }
            fileRepository.hardDelete(f.getId());
        });
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