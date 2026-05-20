package com.cloudcrypt.controller.file;

import com.cloudcrypt.service.file.FileWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FolderController {

    private static final Logger log = LoggerFactory.getLogger(FolderController.class);
    private final FileWriteService fileWriteService;

    public FolderController(FileWriteService fileWriteService) {
        this.fileWriteService = fileWriteService;
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(Authentication auth,
                                          @RequestParam String folderName,
                                          @RequestParam(required = false) Long parentId) {
        log.info("OPERACIÓN: El usuario [{}] creó una carpeta '{}' en {}.", auth.getName(), folderName, parentId);
        return ResponseEntity.ok(fileWriteService.createFolder(folderName, auth.getName(), parentId));
    }

    @PostMapping("/folder/sync")
    public ResponseEntity<?> createFolderSync(Authentication auth,
                                              @RequestParam("folderName") String folderName,
                                              @RequestParam(value = "parentId", required = false) Long parentId) {
        return ResponseEntity.ok(fileWriteService.ensureFolderSync(auth.getName(), folderName, parentId));
    }
}