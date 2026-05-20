package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.service.file.FileQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(FileQueryController.class);
    private final FileQueryService queryService;

    public FileQueryController(FileQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<?> listFiles(Authentication auth,
                                       @RequestParam(required = false) Long folderId,
                                       @RequestParam(defaultValue = "all") String category,
                                       @PageableDefault Pageable pageable) {
        log.debug("OPERACIÓN: El usuario [{}] listó el directorio ID: {}, Categoría: {}.", auth.getName(), folderId, category);
        return ResponseEntity.ok(queryService.getFilesByFolder(auth.getName(), folderId, category, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchFiles(Authentication auth,
                                         @RequestParam("q") String query,
                                         @PageableDefault Pageable pageable) {
        log.info("OPERACIÓN: El usuario [{}] ejecutó una búsqueda: '{}'.", auth.getName(), query);
        return ResponseEntity.ok(queryService.searchFiles(auth.getName(), query, pageable));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(Authentication auth, @PathVariable Long id) throws Exception {
        FileDto dto = queryService.getFileById(id, auth.getName());
        log.info("OPERACIÓN: Descargando el recurso ID: {} ('{}') para el usuario [{}].",
                id, dto.getFileName(), auth.getName());

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