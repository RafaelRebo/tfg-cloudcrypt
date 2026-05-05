package com.example.service;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import com.example.repository.file.FileRepository;
import com.example.repository.user.UserRepository;
import com.example.util.PathUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FolderService {

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final PathUtils pathUtils;

    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public FolderService(FileRepository fileRepository, UserRepository userRepository, PathUtils pathUtils) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.pathUtils = pathUtils;
    }


    @Transactional
    public FileEntity ensureExists(String username, String folderName, FileEntity parent) {
        UserEntity owner = userRepository.findByUsername(username);

        // IMPORTANTE: Buscamos si existe la carpeta dentro de ESE padre específico
        return fileRepository.findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull(username, folderName, parent)
                .orElseGet(() -> {
                    FileEntity newFolder = new FileEntity();
                    newFolder.setFileName(folderName);
                    newFolder.setFileType("application/x-directory");
                    newFolder.setOwner(owner);
                    newFolder.setParent(parent);
                    newFolder.setFileSize(0L);
                    // El folderPath lo construimos solo para el breadcrumb
                    String path = (parent == null) ? "/" : (parent.getFolderPath().equals("/") ? "/" + parent.getFileName() : parent.getFolderPath() + "/" + parent.getFileName());
                    newFolder.setFolderPath(path);
                    return fileRepository.save(newFolder);
                });
    }

    @Transactional
    public void restoreParentHierarchy(String username, String folderPath) {
        if (folderPath == null || folderPath.equals("/")) return;

        String[] parts = folderPath.split("/");
        String currentPath = "/";

        for (String part : parts) {
            if (part.isEmpty()) continue;

            fileRepository.findByOwner_UsernameAndFileNameAndFolderPathAndFileType(
                    username, part, currentPath, "application/x-directory"
            ).ifPresent(parent -> {
                if (parent.getDeletedAt() != null) {
                    fileRepository.restoreFile(parent.getId());
                }
            });

            currentPath = pathUtils.join(currentPath, part);
        }
    }
}