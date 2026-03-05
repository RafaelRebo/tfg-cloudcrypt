package com.example.controller;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import com.example.repository.UserRepository;
import com.example.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "folderPath", defaultValue = "/") String folderPath,
            @RequestParam(value = "fileName", required = false) String fileName) {
        try {
            UserEntity user = userRepository.findByUsername(username);
            if (user == null || !user.getPassword().equals(password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            FileEntity savedFile = fileService.uploadFile(file, user, password, folderPath, fileName);

            URI location = ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/api/files/download/{id}")
                    .buildAndExpand(savedFile.getId())
                    .toUri();

            return ResponseEntity.created(location).body(savedFile);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping
    public ResponseEntity<List<FileEntity>> listFiles(
            @RequestParam String username,
            @RequestParam(defaultValue = "/") String folder,
            @RequestParam(value = "all", defaultValue = "false") boolean all) {

        UserEntity user = userRepository.findByUsername(username);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        List<FileEntity> userFiles = fileService.getFilesByUser(user);

        if (all) {
            return ResponseEntity.ok(userFiles);
        }

        List<FileEntity> filtered = userFiles.stream()
                .filter(f -> {
                    String path = f.getFolderPath() != null ? f.getFolderPath() : "/";
                    return path.equals(folder);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @RequestParam("password") String password) {
        try {
            FileEntity entity = fileService.getFileById(id);
            byte[] content = fileService.getFileContent(id, password);
            ByteArrayResource resource = new ByteArrayResource(content);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(entity.getFileType()))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(@RequestParam String username) {
        UserEntity user = userRepository.findByUsername(username);
        if (user == null) return ResponseEntity.notFound().build();

        List<FileEntity> files = fileService.getFilesByUser(user);
        long totalSize = files.stream().mapToLong(FileEntity::getFileSize).sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", files.size());
        stats.put("maxQuota", 100 * 1024 * 1024);

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