package com.example.service;

import com.example.repository.file.FileRepository;
import com.example.util.PathUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private final FileRepository fileRepository;
    private final PathUtils pathUtils;

    public FolderService(FileRepository fileRepository, PathUtils pathUtils) {
        this.fileRepository = fileRepository;
        this.pathUtils = pathUtils;
    }

    @Transactional
    public void ensureExists(String username, String folderPath) {
        if (folderPath == null || folderPath.equals("/")) return;

        String[] parts = folderPath.split("/");
        String currentPath = "/";

        for (String part : parts) {
            if (part.isEmpty()) continue;

            boolean exists = fileRepository.existsByOwner_UsernameAndFileNameAndFolderPathAndFileType(
                    username, part, currentPath, "application/x-directory");

            if (!exists) {
                fileRepository.createFolder(part, currentPath, username);
            }
            currentPath = pathUtils.join(currentPath, part);
        }
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