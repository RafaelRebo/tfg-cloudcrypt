package com.example.controller.file;

import com.example.dto.file.FileUploadRequestDto;
import com.example.service.file.FileWriteService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileWriteController {
    private final FileWriteService fileWriteService;

    public FileWriteController(FileWriteService fileWriteService) {
        this.fileWriteService = fileWriteService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(Authentication auth, @ModelAttribute FileUploadRequestDto requestDto) throws Exception {
        return ResponseEntity.ok(fileWriteService.uploadFile(requestDto, auth.getName()));
    }

    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(Authentication auth,
                                       @RequestParam List<Long> fileIds,
                                       @RequestParam(required = false) Long targetParentId) {
        fileWriteService.moveFiles(fileIds, targetParentId, auth.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/star")
    public ResponseEntity<?> toggleStar(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(fileWriteService.toggleStar(id, auth.getName()));
    }

    @PostMapping("/{id}/rename")
    public ResponseEntity<?> renameFile(Authentication auth, @PathVariable Long id, @RequestParam("name") String newName) {
        fileWriteService.renameFile(id, newName, auth.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/copy")
    public ResponseEntity<?> copyFiles(Authentication auth,
                                       @RequestParam("fileIds") List<Long> fileIds,
                                       @RequestParam(value = "targetParentId", required = false) Long targetParentId,
                                       @RequestParam(value = "newName", defaultValue = "") String newName) {
        fileWriteService.copyFiles(fileIds, targetParentId, newName, auth.getName());
        return ResponseEntity.ok().build();
    }
}