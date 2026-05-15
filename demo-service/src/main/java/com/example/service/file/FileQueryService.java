package com.example.service.file;

import com.example.dto.file.FileDto;
import com.example.exceptions.InstanceNotFoundException;
import com.example.exceptions.InternalStorageException;
import com.example.mapper.FileMapper;
import com.example.model.FileEntity;
import com.example.model.FileKeyEntity;
import com.example.repository.file.FileRepository;
import com.example.repository.keys.FileKeyRepository;
import com.example.util.StorageUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FileQueryService {
    private final FileRepository fileRepository;
    private final FileKeyRepository fileKeyRepository;
    private final FileMapper fileMapper;
    private final StorageUtils storageUtils;

    public FileQueryService(FileRepository fileRepository, FileKeyRepository fileKeyRepository,
                            FileMapper fileMapper, StorageUtils storageUtils) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.fileMapper = fileMapper;
        this.storageUtils = storageUtils;
    }

    public Page<FileDto> getFilesByFolder(String username, Long parentId, String category, Pageable pageable) {
        if ("trash".equals(category) && parentId == null) return fileRepository.findTrashRoot(username, pageable).map(fileMapper::toDto);
        if ("shared".equals(category)) return fileRepository.findSharedWithMe(username, parentId, pageable).map(fileMapper::toDto);
        if ("starred".equals(category)) return fileRepository.findStarred(username, pageable).map(fileMapper::toDto);
        if (parentId != null) return fileRepository.findByOwner_UsernameAndParentId(username, parentId, pageable).map(fileMapper::toDto);

        String mimePattern = getMimePattern(category);
        if (mimePattern != null) return fileRepository.findByCategory(username, mimePattern, pageable).map(fileMapper::toDto);

        return fileRepository.findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull(username, pageable).map(fileMapper::toDto);
    }

    public FileDto getFileById(Long id, String username) throws InstanceNotFoundException {
        return fileRepository.findByIdAndHasAccess(id, username)
                .map(fileMapper::toDto)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado"));
    }

    public String getEncryptedFileKey(Long fileId, String username) throws InstanceNotFoundException {
        return fileKeyRepository.findByFileIdAndUser_Username(fileId, username)
                .map(FileKeyEntity::getEncryptedKey)
                .orElseThrow(() -> new InstanceNotFoundException("Sin acceso a la llave"));
    }

    public InputStream getFileDownloadStream(Long id, String username) throws Exception {
        FileEntity entity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado"));

        if (!storageUtils.exists(entity.getStoragePath())) throw new InternalStorageException("Archivo no encontrado en disco");
        return storageUtils.getRawStream(entity.getStoragePath());
    }

    public Map<String, Object> checkExistsById(String username, String fileName, Long parentId) {
        Optional<FileEntity> existing = (parentId == null)
                ? fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(username, fileName)
                : fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull(username, fileName, parentId);

        var response = new java.util.HashMap<String, Object>();
        response.put("exists", existing.isPresent());
        existing.ifPresent(f -> response.put("existingId", f.getId()));
        return response;
    }

    public List<FileDto> getRecursiveFilesForSharing(Long folderId, String username) throws Exception {
        FileEntity folder = fileRepository.findByIdAndOwner_Username(folderId, username).orElseThrow();
        String fullPath = folder.getFolderPath().equals("/") ? "/" + folder.getFileName() : folder.getFolderPath() + "/" + folder.getFileName();
        return fileRepository.findAllByOwnerAndRecursivePathList(username, fullPath, folderId).stream().map(fileMapper::toDto).collect(Collectors.toList());
    }

    public Page<FileDto> searchFiles(String username, String query, Pageable pageable) {
        if (query == null || query.isBlank()) return Page.empty();
        return fileRepository.searchByName(username, query.trim(), pageable).map(fileMapper::toDto);
    }

    private String getMimePattern(String category) {
        return switch (category != null ? category : "all") {
            case "image" -> "image/%";
            case "video" -> "video/%";
            case "audio" -> "audio/%";
            case "document" -> "%pdf%";
            default -> null;
        };
    }
}