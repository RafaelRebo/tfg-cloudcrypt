package com.example.controller;

import com.example.dto.FileDto;
import com.example.service.FileService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            String authenticatedUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password,
            @RequestParam(value = "folderPath", defaultValue = "/") String folderPath,
            @RequestParam(value = "fileName", required = false) String fileName) {

        try {
            FileDto savedFile = fileService.uploadFile(file, authenticatedUser, password, folderPath, fileName);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            String authenticatedUser,
            @RequestParam("folderName") String folderName,
            @RequestParam("password") String password,
            @RequestParam("folderPath") String folderPath) {

        try {
            FileDto savedFolder = fileService.createFolder(folderName, authenticatedUser, password, folderPath);
            return ResponseEntity.ok(savedFolder);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listFiles(
            String authenticatedUser,
            @RequestParam(defaultValue = "/") String folder,
            @RequestParam(value = "category", defaultValue = "all") String category,
            @PageableDefault(size = 20, sort = "fileName") Pageable pageable) {

        Page<FileDto> page = fileService.getFilesByFolder(authenticatedUser, folder, category, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            String authenticatedUser,
            @PathVariable Long id,
            @RequestHeader("X-File-Password") String password) {

        try {
            FileDto fileDto = fileService.getFileById(id, authenticatedUser);
            InputStream stream = fileService.getFileDownloadStream(id, authenticatedUser, password);
            InputStreamResource resource = new InputStreamResource(stream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileDto.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(fileDto.getFileType()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getUserStats(String authenticatedUser) {
        Map<String, Object> stats = fileService.getUserStats(authenticatedUser);
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(
            String authenticatedUser,
            @PathVariable Long id) {

        try {
            fileService.deleteFile(id, authenticatedUser);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreFile(
            String authenticatedUser,
            @PathVariable Long id) {

        try {
            FileDto restored = fileService.restoreFile(id, authenticatedUser);
            return ResponseEntity.ok(restored);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<FileDto>> searchFiles(
            String authenticatedUser,
            @RequestParam("q") String query,
            @PageableDefault(size = 20, sort = "fileName") Pageable pageable) {

        Page<FileDto> results = fileService.searchFiles(authenticatedUser, query, pageable);
        return ResponseEntity.ok(results);
    }
}