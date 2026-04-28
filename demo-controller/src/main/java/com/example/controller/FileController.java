package com.example.controller;

import com.example.config.StorageConfig;
import com.example.dto.FileDto;
import com.example.service.FileService;
import com.example.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private FileService fileService;

    @Autowired
    private JwtUtils jwtUtils;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password,
            @RequestParam(value = "folderPath", defaultValue = "/") String folderPath,
            @RequestParam(value = "fileName", required = false) String fileName) {

        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            FileDto savedFile = fileService.uploadFile(file, username, password, folderPath, fileName);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("folderName") String folderName,
            @RequestParam("password") String password,
            @RequestParam("folderPath") String folderPath) {

        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            FileDto savedFolder = fileService.createFolder(folderName, username, password, folderPath);
            return ResponseEntity.ok(savedFolder);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> listFiles(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "/") String folder,
            @RequestParam(value = "all", defaultValue = "false") boolean all,
            @PageableDefault(size = 20, sort = "fileName") Pageable pageable) {

        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Page<FileDto> page = fileService.getFilesByFolder(username, folder, all, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestHeader("X-File-Password") String password) {


        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

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
    public ResponseEntity<?> getUserStats(@RequestHeader("Authorization") String authHeader) {
        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Map<String, Object> stats = fileService.getUserStats(username);
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {

        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            fileService.deleteFile(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreFile(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {

        String username = getUsernameFromToken(authHeader);
        if (username == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            FileDto restored = fileService.restoreFile(id);
            return ResponseEntity.ok(restored);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private String getUsernameFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        if (jwtUtils.validateToken(token)) {
            return jwtUtils.getUsernameFromToken(token);
        }
        return null;
    }
}