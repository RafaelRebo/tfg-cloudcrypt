package com.example.service;

import com.example.config.StorageConfig;
import com.example.dto.FileDto;
import com.example.exceptions.InstanceNotFoundException;
import com.example.exceptions.InternalStorageException;
import com.example.mapper.FileMapper;
import com.example.model.FileEntity;
import com.example.repository.file.FileRepository;
import com.example.repository.file.FileStorageRepository;
import com.example.util.CryptoUtils;
import com.example.util.HashUtils;
import com.example.util.QuotaUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileService {

    @Autowired private FileRepository fileRepository;
    @Autowired private FileStorageRepository fileStorageRepository;
    @Autowired private CryptoUtils cryptoUtils;
    @Autowired private HashUtils hashUtils;
    @Autowired private QuotaUtils quotaUtils;
    @Autowired private UserService userService;
    @Autowired private FileMapper fileMapper;
    @Autowired private StorageConfig storageConfig;

    @Transactional
    public FileDto uploadFile(MultipartFile file, String username, String rawPassword, String folderPath, String fileName) throws Exception {
        userService.authenticate(username, rawPassword);
        quotaUtils.checkQuota(username, file.getSize());

        String fileChecksum = hashUtils.calculateChecksum(file.getInputStream());

        String physicalFolder = username + "/" + folderPath.replaceAll("^/|/$", "");
        String storageName = UUID.randomUUID().toString();
        String finalStoragePath = physicalFolder + "/" + storageName;

        ensureFolderExists(username, folderPath);

        fileStorageRepository.save(
                file.getInputStream(),
                physicalFolder,
                storageName,
                cryptoUtils.getCipher(Cipher.ENCRYPT_MODE, rawPassword)
        );

        FileEntity entity = fileRepository.createFile(
                fileName,
                folderPath,
                file.getContentType(),
                file.getSize(),
                fileChecksum,
                finalStoragePath,
                username
        );

        return fileMapper.toDto(entity);
    }

    private void ensureFolderExists(String username, String folderPath) {
        if (folderPath == null || folderPath.equals("/")) return;

        String[] parts = folderPath.split("/");
        String currentPath = "/";

        for (String part : parts) {
            if (part.isEmpty()) continue;

            // Comprobar si esta carpeta ya existe para el usuario
            boolean exists = fileRepository.existsByOwner_UsernameAndFileNameAndFolderPathAndFileType(
                    username, part, currentPath, "application/x-directory");

            if (!exists) {
                fileRepository.createFolder(part, currentPath, username);
            }

            // Avanzar al siguiente nivel
            currentPath = (currentPath.equals("/") ? "" : currentPath) + "/" + part;
        }
    }

    @Transactional
    public FileDto createFolder(String folderName, String username, String rawPassword, String currentFolderPath) throws Exception {
        userService.authenticate(username, rawPassword);

        quotaUtils.checkQuota(username, 0);

        FileEntity entity = fileRepository.createFolder(folderName, currentFolderPath, username);

        return fileMapper.toDto(entity);
    }

    public Page<FileDto> getFilesByFolder(String username, String folder, boolean all, Pageable pageable) {
        Page<FileEntity> entities;

        if (all) {
            entities = fileRepository.findByOwner_Username(username, pageable);
        } else {
            entities = fileRepository.findByOwner_UsernameAndFolderPathAndDeletedAtIsNull(username, folder, pageable);
        }

        // Transformamos Page<FileEntity> a Page<FileDto> usando el mapper
        return entities.map(fileMapper::toDto);
    }

    public InputStream getFileDownloadStream(Long fileId, String rawPassword)
            throws InstanceNotFoundException, InternalStorageException {

        FileEntity entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new InstanceNotFoundException("Fichero no encontrado en la base de datos"));

        if ("application/x-directory".equals(entity.getFileType())) {
            throw new InternalStorageException("No se puede descargar un directorio directamente.");
        }

        // Verificación de integridad física
        if (!fileStorageRepository.exists(entity.getStoragePath())) {
            // fileRepository.delete(entity);
            throw new InternalStorageException("El archivo físico ha sido eliminado o movido del repositorio.");
        }

        try {
            Cipher decryptCipher = cryptoUtils.getCipher(Cipher.DECRYPT_MODE, rawPassword);
            return fileStorageRepository.loadDecryptedStream(entity.getStoragePath(), decryptCipher);
        } catch (Exception e) {
            throw new InternalStorageException("Error al descifrar el archivo");
        }
    }

    public FileDto getFileById(Long id) throws InstanceNotFoundException {
        return fileRepository.findById(id)
                .map(fileMapper::toDto)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado con ID: " + id));
    }

    @Transactional
    public void deleteFile(Long id) throws Exception {
        FileEntity entity = fileRepository.findById(id)
                .orElseThrow(() -> new InstanceNotFoundException("Fichero no encontrado"));

        // La ruta que identifica a los hijos es: folderPath actual + / + nombre
        // Ej: folderPath "/" + fileName "Carpeta" = "/Carpeta"
        String childrenPath = (entity.getFolderPath().endsWith("/") ?
                entity.getFolderPath() : entity.getFolderPath() + "/")
                + entity.getFileName();

        if (entity.getDeletedAt() == null) {
            // --- BORRADO LÓGICO ---
            fileRepository.markAsDeleted(id);

            if ("application/x-directory".equals(entity.getFileType())) {
                // Buscamos todos los hijos usando la nueva Query
                List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePath(
                        entity.getOwner().getUsername(), childrenPath);

                for (FileEntity child : children) {
                    fileRepository.markAsDeleted(child.getId());
                }
            }
        } else {
            // --- BORRADO FÍSICO ---
            if ("application/x-directory".equals(entity.getFileType())) {
                List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePath(
                        entity.getOwner().getUsername(), childrenPath);

                for (FileEntity child : children) {
                    // Borrar archivo físico si existe
                    if (child.getStoragePath() != null) {
                        fileStorageRepository.delete(child.getStoragePath());
                    }
                    fileRepository.hardDelete(child.getId());
                }
            } else {
                if (entity.getStoragePath() != null) {
                    fileStorageRepository.delete(entity.getStoragePath());
                }
            }
            fileRepository.hardDelete(id);
        }
    }

    @Transactional
    public FileDto restoreFile(Long id) throws InstanceNotFoundException {
        FileEntity entity = fileRepository.findById(id)
                .orElseThrow(() -> new InstanceNotFoundException("Fichero no encontrado"));

        // 1. Restauramos los padres (si restauro /a/b/c, activo 'a' y 'b' automáticamente)
        restoreParentHierarchy(entity.getOwner().getUsername(), entity.getFolderPath());

        // 2. Restauramos el elemento actual
        fileRepository.restoreFile(id);

        // 3. Si es carpeta, restauramos TODO lo de dentro (recursivo)
        if ("application/x-directory".equals(entity.getFileType())) {
            String childrenPath = (entity.getFolderPath().endsWith("/") ?
                    entity.getFolderPath() : entity.getFolderPath() + "/")
                    + entity.getFileName();

            List<FileEntity> children = fileRepository.findAllByOwnerAndRecursivePath(
                    entity.getOwner().getUsername(), childrenPath);

            for (FileEntity child : children) {
                fileRepository.restoreFile(child.getId());
            }
        }

        return fileMapper.toDto(entity);
    }

    /**
     * Método auxiliar para asegurar que toda la ruta del padre esté activa
     */
    private void restoreParentHierarchy(String username, String folderPath) {
        if (folderPath == null || folderPath.equals("/")) return;

        String[] parts = folderPath.split("/");
        String currentPath = "/";

        for (String part : parts) {
            if (part.isEmpty()) continue;

            // Buscamos la carpeta padre en la base de datos (esté borrada o no)
            fileRepository.findByOwner_UsernameAndFileNameAndFolderPathAndFileType(
                    username, part, currentPath, "application/x-directory"
            ).ifPresent(parent -> {
                if (parent.getDeletedAt() != null) {
                    fileRepository.restoreFile(parent.getId());
                }
            });

            currentPath = (currentPath.equals("/") ? "" : currentPath) + "/" + part;
        }
    }

    public Map<String, Object> getUserStats(String username) {
        long totalSize = fileRepository.getTotalUsageByUser(username);
        long fileCount = fileRepository.countFilesByUser(username);
        long maxQuota = storageConfig.getMaxQuota();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", fileCount);
        stats.put("maxQuota", maxQuota);

        double usagePercentage = maxQuota > 0 ? (double) totalSize / maxQuota * 100 : 0;
        stats.put("usagePercentage", Math.round(usagePercentage * 100.0) / 100.0);

        return stats;
    }
}