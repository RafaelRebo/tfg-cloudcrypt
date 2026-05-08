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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.List;
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
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam("password") String password,
            @RequestParam(value = "parentId", required = false) Long parentId,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam("totalBatchSize") Long totalBatchSize) { // <--- NUEVO PARÁMETRO

        try {
            // Pasamos el totalBatchSize al service
            FileDto savedFile = fileService.uploadFile(file, auth.getName(), password, parentId, fileName, totalBatchSize);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/folder")
    public ResponseEntity<?> createFolder(
            Authentication auth,
            @RequestParam("folderName") String folderName,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        try {
            FileDto savedFolder = fileService.createFolder(folderName, auth.getName(), parentId);
            return ResponseEntity.ok(savedFolder);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/folder/sync")
    public ResponseEntity<?> createFolderSync(
            Authentication auth,
            @RequestParam("folderName") String folderName,
            @RequestParam("password") String password,
            @RequestParam(value = "parentId", required = false) Long parentId) {
        try {
            // Pasamos el parentId directamente al service
            FileDto folder = fileService.ensureFolderSync(auth.getName(), folderName, parentId);
            return ResponseEntity.ok(folder);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/check-exists")
    public ResponseEntity<Map<String, Object>> checkFileExists(
            Authentication auth,
            @RequestParam String fileName,
            @RequestParam(required = false) Long parentId) {

        // Usamos auth.getName() en lugar del parámetro 'username'
        Map<String, Object> result = fileService.checkExistsById(auth.getName(), fileName, parentId);
        return ResponseEntity.ok(result);
    }

    @GetMapping
    public ResponseEntity<?> listFiles(
            Authentication auth,
            @RequestParam(value = "folderId", required = false) Long folderId, // Cambiado de 'folder' a 'folderId'
            @RequestParam(value = "category", defaultValue = "all") String category,
            @PageableDefault(size = 20, sort = "fileName") Pageable pageable) {

        Page<FileDto> page = fileService.getFilesByFolder(auth.getName(), folderId, category, pageable);
        return ResponseEntity.ok(page);
    }

    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(
            Authentication auth,
            @RequestParam List<Long> fileIds,
            @RequestParam(required = false) Long targetParentId) {
        try {
            fileService.moveFiles(fileIds, targetParentId, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            Authentication auth,
            @PathVariable Long id,
            @RequestHeader("X-File-Password") String password) {

        try {
            FileDto fileDto = fileService.getFileById(id, auth.getName());
            InputStream stream = fileService.getFileDownloadStream(id, auth.getName(), password);
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
    public ResponseEntity<?> getUserStats(Authentication auth) { // <--- CAMBIO: Usar Authentication
        Map<String, Object> stats = fileService.getUserStats(auth.getName());
        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(
            Authentication auth,
            @PathVariable Long id) {

        try {
            fileService.deleteFile(id, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreFile(
            Authentication auth,
            @PathVariable Long id) {

        try {
            FileDto restored = fileService.restoreFile(id, auth.getName());
            return ResponseEntity.ok(restored);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<FileDto>> searchFiles(
            Authentication auth,
            @RequestParam("q") String query,
            @PageableDefault(size = 20, sort = "fileName") Pageable pageable) {

        Page<FileDto> results = fileService.searchFiles(auth.getName(), query, pageable);
        return ResponseEntity.ok(results);
    }
}