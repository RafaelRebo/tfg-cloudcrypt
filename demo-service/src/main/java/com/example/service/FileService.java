package com.example.service;

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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileService {

    @Autowired private FileRepository fileRepository;
    @Autowired private FileStorageRepository fileStorageRepository;
    @Autowired private CryptoUtils cryptoUtils;
    @Autowired private HashUtils hashUtils;
    @Autowired private QuotaUtils quotaUtils;
    @Autowired private UserService userService;
    @Autowired private FileMapper fileMapper;

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

    public List<FileDto> getFilesByFolder(String username, String folder, boolean all) {
        return fileRepository.findByOwner_Username(username).stream()
                .filter(f -> all || (f.getFolderPath() != null ? f.getFolderPath() : "/").equals(folder))
                .map(fileMapper::toDto)
                .collect(Collectors.toList());
    }

    public InputStream getFileDownloadStream(Long fileId, String rawPassword)
            throws InstanceNotFoundException, InternalStorageException {

        FileEntity entity = fileRepository.findById(fileId)
                .orElseThrow(() -> new InstanceNotFoundException("Fichero no encontrado"));

        try {
            Cipher decryptCipher = cryptoUtils.getCipher(Cipher.DECRYPT_MODE, rawPassword);
            return fileStorageRepository.loadDecryptedStream(entity.getStoragePath(), decryptCipher);
        } catch (Exception e) {
            throw new InternalStorageException("Error al procesar el archivo");
        }
    }

    public FileDto getFileById(Long id) throws InstanceNotFoundException {
        return fileRepository.findById(id)
                .map(fileMapper::toDto)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado con ID: " + id));
    }

    public void deleteFile(Long id) throws Exception {
        FileEntity entity = fileRepository.findById(id)
                .orElseThrow(() -> new InstanceNotFoundException("Fichero no encontrado"));

        fileStorageRepository.delete(entity.getStoragePath());
        fileRepository.delete(entity);
    }

    public List<FileDto> getFilesByUser(String username) {
        return fileRepository.findByOwner_Username(username).stream()
                .map(fileMapper::toDto)
                .collect(Collectors.toList());
    }
}