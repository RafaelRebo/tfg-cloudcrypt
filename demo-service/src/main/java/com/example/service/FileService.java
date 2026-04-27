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

        if (entity.getDeletedAt() == null) {
            fileRepository.markAsDeleted(id);
        } else {
            fileStorageRepository.delete(entity.getStoragePath());
            fileRepository.hardDelete(id);
        }
    }

    @Transactional
    public FileDto restoreFile(Long id) throws InstanceNotFoundException {
        fileRepository.restoreFile(id);

        FileEntity entity = fileRepository.findById(id)
                .orElseThrow(() -> new InstanceNotFoundException("Fichero no encontrado"));
        return fileMapper.toDto(entity);
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