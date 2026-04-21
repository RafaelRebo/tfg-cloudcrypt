package com.example.service;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import com.example.repository.FileRepository;
import com.example.repository.FileStorageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private FileStorageRepository fileStorageRepository;
    @Autowired
    private CryptoService cryptoService;

    @Value("${app.max-quota:104857600}")
    private long maxQuota;

    public FileEntity uploadFile(MultipartFile file, UserEntity owner, String rawPassword, String folderPath, String fileName) throws Exception {
        // 1. Cuota
        List<FileEntity> existingFiles = fileRepository.findByOwner(owner);
        long currentUsage = existingFiles.stream().mapToLong(FileEntity::getFileSize).sum();

        if (currentUsage + file.getSize() > maxQuota) {
            // Calculamos cuánto falta para que el mensaje de error sea útil
            long disponible = maxQuota - currentUsage;
            throw new RuntimeException("Cuota excedida. Espacio disponible: " + (disponible / (1024 * 1024)) + " MB");
        }

        // 2. Calcular Checksum (Integridad)
        String fileChecksum;
        try (InputStream is = file.getInputStream()) {
            fileChecksum = cryptoService.calculateChecksum(is);
        }

        // 3. Cifrado y Almacenamiento (Streaming directo al disco)
        String physicalPath = owner.getUsername() + "/" + folderPath.replaceAll("^/|/$", "");
        String storageName = UUID.randomUUID().toString();
        Path finalPath = fileStorageRepository.getTargetPath(physicalPath, storageName);

        try (InputStream is = file.getInputStream();
             OutputStream os = Files.newOutputStream(finalPath);
             CipherOutputStream cos = new CipherOutputStream(os, cryptoService.getCipher(Cipher.ENCRYPT_MODE, rawPassword))) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                cos.write(buffer, 0, bytesRead);
            }
        }

        // 4. Metadatos
        FileEntity entity = new FileEntity();
        entity.setFileName(fileName);
        entity.setFolderPath(folderPath);
        entity.setFileType(file.getContentType());
        entity.setFileSize(file.getSize());
        entity.setChecksum(fileChecksum);
        entity.setStoragePath(physicalPath + "/" + storageName);
        entity.setOwner(owner);

        return fileRepository.save(entity);
    }

    public InputStream getFileDownloadStream(Long fileId, String rawPassword) throws Exception {
        FileEntity entity = getFileById(fileId);
        InputStream encryptedIs = fileStorageRepository.loadStream(entity.getStoragePath());

        // Retornamos un CipherInputStream que descifra mientras el navegador descarga
        Cipher decryptCipher = cryptoService.getCipher(Cipher.DECRYPT_MODE, rawPassword);
        return new CipherInputStream(encryptedIs, decryptCipher);
    }

    // El resto de métodos (deleteFile, getFilesByFolder, etc.) se mantienen igual que en tu versión anterior
    public List<FileEntity> getFilesByFolder(UserEntity owner, String folder, boolean all) {
        List<FileEntity> userFiles = fileRepository.findByOwner(owner);
        if (all) return userFiles;
        return userFiles.stream()
                .filter(f -> {
                    String path = f.getFolderPath() != null ? f.getFolderPath() : "/";
                    return path.equals(folder);
                }).toList();
    }

    public void deleteFile(Long id) throws Exception {
        FileEntity entity = getFileById(id);
        fileStorageRepository.delete(entity.getStoragePath());
        fileRepository.delete(entity);
    }

    public FileEntity getFileById(Long id) {
        return fileRepository.findById(id).orElseThrow(() -> new RuntimeException("Fichero no encontrado"));
    }

    public List<FileEntity> getFilesByUser(UserEntity owner) {
        return fileRepository.findByOwner(owner);
    }
}