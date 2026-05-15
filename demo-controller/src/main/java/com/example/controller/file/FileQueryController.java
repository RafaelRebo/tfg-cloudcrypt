package com.example.controller.file;

import com.example.dto.file.FileDto;
import com.example.service.file.FileQueryService;
import com.example.service.file.StatsService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
public class FileQueryController {
    private final FileQueryService queryService;
    private final StatsService statsService;

    public FileQueryController(FileQueryService queryService, StatsService statsService) {
        this.queryService = queryService;
        this.statsService = statsService;
    }

    @GetMapping
    public ResponseEntity<?> listFiles(Authentication auth, @RequestParam(required = false) Long folderId,
                                       @RequestParam(defaultValue = "all") String category, @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(queryService.getFilesByFolder(auth.getName(), folderId, category, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(Authentication auth, @RequestParam("q") String query, @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(queryService.searchFiles(auth.getName(), query, pageable));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(Authentication auth, @PathVariable Long id) {
        try {
            FileDto dto = queryService.getFileById(id, auth.getName());
            InputStream stream = queryService.getFileDownloadStream(id, auth.getName());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new InputStreamResource(stream));
        } catch (Exception e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); }
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getUserStats(Authentication auth) {
        return ResponseEntity.ok(statsService.getUserStats(auth.getName()));
    }

    @GetMapping("/check-exists")
    public ResponseEntity<?> checkFileExists(Authentication auth, @RequestParam String fileName, @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(queryService.checkExistsById(auth.getName(), fileName, parentId));
    }

    @GetMapping("/folder-content-recursive/{id}")
    public ResponseEntity<?> getRecursiveContent(@PathVariable Long id, Authentication auth) {
        try {
            return ResponseEntity.ok(queryService.getRecursiveFilesForSharing(id, auth.getName()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}
