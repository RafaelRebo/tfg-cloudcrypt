package com.cloudcrypt.service.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.exceptions.InputValidationException;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.exceptions.InternalStorageException;
import com.cloudcrypt.mapper.FileMapper;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.util.StorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FileQueryService {

    private static final Logger log = LoggerFactory.getLogger(FileQueryService.class);

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
        log.debug("OPERACIÓN: Obteniendo elementos para el usuario [{}] en la categoría '{}' y carpeta: {}.", username, category, parentId);
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
        log.debug("OPERACIÓN: Verificando permisos de lectura del recurso ID: {} para el usuario [{}].", id, username);
        return fileRepository.findByIdAndHasAccess(id, username)
                .map(f -> fileMapper.toDto(f, username))
                .orElseThrow(() -> {
                    log.warn("OPERACIÓN: El usuario [{}] intentó leer el recurso restringido ID: {}.", username, id);
                    return new InstanceNotFoundException("Archivo no encontrado o acceso denegado.");
                });
    }

    public String getEncryptedFileKey(Long fileId, String username) {
        log.debug("OPERACIÓN: Recuperando clave simétrica para recurso ID: {} (Usuario: [{}]).", fileId, username);
        return fileKeyRepository.findByFileIdAndUser_Username(fileId, username)
                .map(FileKeyEntity::getEncryptedKey)
                .orElseThrow(() -> {
                    log.warn("OPERACIÓN: Violación de acceso para el recurso ID: {} por [@{}].", fileId, username);
                    return new InstanceNotFoundException("Sin acceso a la llave criptográfica.");
                });
    }

    public Map<Long, String> getEncryptedFileKeysBatch(List<Long> fileIds, String username) {
        if (fileIds == null || fileIds.isEmpty()) {
            return Collections.emptyMap();
        }
        log.debug("OPERACIÓN: Recuperando claves en bloque para {} elementos del usuario [{}].", fileIds.size(), username);
        List<FileKeyEntity> keys = fileKeyRepository.findByFileIdInAndUser_Username(fileIds, username);
        return keys.stream().collect(Collectors.toMap(
                k -> k.getFile().getId(),
                FileKeyEntity::getEncryptedKey
        ));
    }

    public InputStream getFileDownloadStream(Long id, String username) {
        FileEntity entity = fileRepository.findByIdAndHasAccess(id, username)
                .orElseThrow(() -> new InstanceNotFoundException("Elemento no encontrado."));

        if ("application/x-directory".equals(entity.getFileType())) {
            throw new InputValidationException("No se puede descargar un directorio como flujo de datos plano.");
        }

        if (entity.getStoragePath() == null || !storageUtils.exists(entity.getStoragePath())) {
            log.error("OPERACIÓN: El ID {} existe, pero su archivo físico ha desaparecido de '{}'.", id, entity.getStoragePath());
            throw new InternalStorageException("Error: El archivo físico no existe en el almacenamiento.");
        }

        try {
            return storageUtils.getRawStream(entity.getStoragePath());
        } catch (IOException e) {
            log.error("OPERACIÓN: Excepción de I/O crítica al leer el fichero físico en: {}", entity.getStoragePath());
            throw new InternalStorageException("Error de lectura en el disco del servidor.");
        }
    }

    public Map<String, Object> checkExistsById(String username, String fileName, Long parentId) {
        log.debug("OPERACIÓN: Comprobando colisión de nombres de archivo para '{}' en {}.", fileName, parentId);
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