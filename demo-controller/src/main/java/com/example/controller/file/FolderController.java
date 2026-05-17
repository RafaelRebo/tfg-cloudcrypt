package com.example.controller.file;

import com.example.service.file.FileWriteService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class FolderController {

    private final FileWriteService fileWriteService;

    public FolderController(FileWriteService fileWriteService) {
        this.fileWriteService = fileWriteService;
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(Authentication auth,
          @RequestParam String folderName,
          @RequestParam(required = false) Long parentId) {
            return ResponseEntity.ok(fileWriteService.createFolder(folderName, auth.getName(), parentId));
    }

    @PostMapping("/folder/sync")
    public ResponseEntity<?> createFolderSync(Authentication auth,
          @RequestParam("folderName") String folderName,
          @RequestParam(value = "parentId", required = false) Long parentId) {
            return ResponseEntity.ok(fileWriteService.ensureFolderSync(auth.getName(), folderName, parentId));
    }
}