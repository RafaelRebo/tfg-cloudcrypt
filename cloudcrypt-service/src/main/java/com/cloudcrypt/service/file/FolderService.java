package com.cloudcrypt.service.file;

import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.PathUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FolderService {

    private final FileRepository fileRepository;
    private final FileKeyRepository fileKeyRepository;
    private final UserRepository userRepository;
    private final PathUtils pathUtils;

    public FolderService(FileRepository fileRepository, FileKeyRepository fileKeyRepository,
                         UserRepository userRepository, PathUtils pathUtils) {
        this.fileRepository = fileRepository;
        this.fileKeyRepository = fileKeyRepository;
        this.userRepository = userRepository;
        this.pathUtils = pathUtils;
    }


    @Transactional
    public FileEntity ensureExists(String username, String folderName, FileEntity parent) {
        UserEntity owner = userRepository.findByUsername(username);

        return fileRepository.findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull(username, folderName, parent)
            .orElseGet(() -> {

                FileEntity newFolder = new FileEntity();
                newFolder.setFileName(folderName);
                newFolder.setFileType("application/x-directory");
                newFolder.setOwner(owner);
                newFolder.setParent(parent);
                newFolder.setFileSize(0L);

                String path = (parent == null) ? "/" :
                        (parent.getFolderPath().equals("/") ? "/" + parent.getFileName() : parent.getFolderPath() + "/" + parent.getFileName());
                newFolder.setFolderPath(path);

                FileEntity savedFolder = fileRepository.save(newFolder);


                FileKeyEntity folderKey = new FileKeyEntity();
                folderKey.setFile(savedFolder);
                folderKey.setUser(owner);
                folderKey.setEncryptedKey("FOLDER_PERMISSION");
                folderKey.setStarred(false);
                fileKeyRepository.save(folderKey);

                return savedFolder;
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