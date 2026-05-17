package com.example.controller.file;

import com.example.dto.file.FileDto;
import com.example.service.file.FileQueryService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
public class FileQueryController {
    private final FileQueryService queryService;

    public FileQueryController(FileQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<?> listFiles(Authentication auth,
                                       @RequestParam(required = false) Long folderId,
                                       @RequestParam(defaultValue = "all") String category,
                                       @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(queryService.getFilesByFolder(auth.getName(), folderId, category, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(Authentication auth,
                                         @RequestParam("q") String query,
                                         @PageableDefault Pageable pageable) {
        return ResponseEntity.ok(queryService.searchFiles(auth.getName(), query, pageable));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(Authentication auth, @PathVariable Long id) throws Exception {
        FileDto dto = queryService.getFileById(id, auth.getName());
        InputStream stream = queryService.getFileDownloadStream(id, auth.getName());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dto.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/check-exists")
    public ResponseEntity<?> checkFileExists(Authentication auth,
                                             @RequestParam String fileName,
                                             @RequestParam(required = false) Long parentId) {
        return ResponseEntity.ok(queryService.checkExistsById(auth.getName(), fileName, parentId));
    }

    @GetMapping("/folder-content-recursive/{id}")
    public ResponseEntity<?> getRecursiveContent(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(queryService.getRecursiveFilesForSharing(id, auth.getName()));
    }
}