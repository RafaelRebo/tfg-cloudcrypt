package com.example.controller;

import com.example.config.StorageConfig;
import com.example.dto.FileDto;
import com.example.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private StorageConfig storageConfig;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "folderPath", defaultValue = "/") String folderPath,
            @RequestParam(value = "fileName", required = false) String fileName) {
        try {
            FileDto savedFile = fileService.uploadFile(file, username, password, folderPath, fileName);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<FileDto>> listFiles(
            @RequestParam String username,
            @RequestParam(defaultValue = "/") String folder,
            @RequestParam(value = "all", defaultValue = "false") boolean all) {

        List<FileDto> filteredFiles = fileService.getFilesByFolder(username, folder, all);
        return ResponseEntity.ok(filteredFiles);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @RequestHeader("X-File-Password") String password) {
        try {
            FileDto fileDto = fileService.getFileById(id);

            InputStream stream = fileService.getFileDownloadStream(id, password);
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
    public ResponseEntity<Map<String, Object>> getUserStats(@RequestParam String username) {
        List<FileDto> files = fileService.getFilesByUser(username);

        long totalSize = files.stream().mapToLong(FileDto::getFileSize).sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", files.size());
        stats.put("maxQuota", storageConfig.getMaxQuota());

        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        try {
            fileService.deleteFile(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}