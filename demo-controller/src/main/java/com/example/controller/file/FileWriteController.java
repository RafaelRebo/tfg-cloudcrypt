package com.example.controller.file;

import com.example.dto.file.FileUploadRequestDto;
import com.example.service.file.FileWriteService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileWriteController {
    private final FileWriteService fileWriteService;

    public FileWriteController(FileWriteService fileWriteService) {
        this.fileWriteService = fileWriteService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(Authentication auth, @ModelAttribute FileUploadRequestDto requestDto) {
        try {
            return ResponseEntity.ok(fileWriteService.uploadFile(requestDto, auth.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(Authentication auth,
                                       @RequestParam List<Long> fileIds,
                                       @RequestParam(required = false) Long targetParentId) {
        try {
            fileWriteService.moveFiles(fileIds, targetParentId, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/star")
    public ResponseEntity<?> toggleStar(@PathVariable Long id, Authentication auth) {
        try {
            return ResponseEntity.ok(fileWriteService.toggleStar(id, auth.getName()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}