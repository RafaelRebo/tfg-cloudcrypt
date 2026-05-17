// com/example/service/file/FileQueryService.java
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

import java.io.IOException;
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
        Page<FileEntity> entities;
        if ("trash".equals(category) && parentId == null) entities = fileRepository.findTrashRoot(username, pageable);
        else if ("shared".equals(category)) entities = fileRepository.findSharedWithMe(username, parentId, pageable);
        else if ("starred".equals(category)) entities = fileRepository.findStarred(username, pageable);
        else if (parentId != null) entities = fileRepository.findByOwner_UsernameAndParentId(username, parentId, pageable);
        else {
            String mimePattern = getMimePattern(category);
            entities = (mimePattern != null)
                    ? fileRepository.findByCategory(username, mimePattern, pageable)
                    : fileRepository.findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull(username, pageable);
        }
        return entities.map(f -> fileMapper.toDto(f, username));
    }

    public FileDto getFileById(Long id, String username) {
        return fileRepository.findByIdAndHasAccess(id, username)
                .map(f -> fileMapper.toDto(f, username))
                .orElseThrow(() -> new InstanceNotFoundException("Archivo no encontrado o acceso denegado."));
    }

    public String getEncryptedFileKey(Long fileId, String username) {
        return fileKeyRepository.findByFileIdAndUser_Username(fileId, username)
                .map(FileKeyEntity::getEncryptedKey)
                .orElseThrow(() -> new InstanceNotFoundException("Sin acceso a la llave criptográfica."));
    }

    public InputStream getFileDownloadStream(Long id, String username) {
        FileEntity entity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Elemento no encontrado."));

        if ("application/x-directory".equals(entity.getFileType())) {
            throw new IllegalArgumentException("No se puede descargar un directorio como flujo de datos plano.");
        }

        if (entity.getStoragePath() == null || !storageUtils.exists(entity.getStoragePath())) {
            throw new InternalStorageException("Error: El archivo físico no existe en el almacenamiento.");
        }

        try {
            return storageUtils.getRawStream(entity.getStoragePath());
        } catch (IOException e) {
            throw new InternalStorageException("Error de lectura en el disco del servidor.");
        }
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

    public List<FileDto> getRecursiveFilesForSharing(Long folderId, String username) {
        FileEntity folder = fileRepository.findByIdAndOwner_Username(folderId, username)
                .orElseThrow(() -> new InstanceNotFoundException("La carpeta solicitada no existe o no tienes acceso."));
        String fullPath = folder.getFolderPath().equals("/") ? "/" + folder.getFileName() : folder.getFolderPath() + "/" + folder.getFileName();
        return fileRepository.findAllByOwnerAndRecursivePathList(username, fullPath, folderId).stream()
                .map(f -> fileMapper.toDto(f, username)).collect(Collectors.toList());
    }

    public Page<FileDto> searchFiles(String username, String query, Pageable pageable) {
        if (query == null || query.isBlank()) return Page.empty();
        return fileRepository.searchByName(username, query.trim(), pageable).map(f -> fileMapper.toDto(f, username));
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