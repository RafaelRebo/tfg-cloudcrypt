package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.FileUploadRequestDto;
import com.cloudcrypt.service.file.FileWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileWriteController {

    private static final Logger log = LoggerFactory.getLogger(FileWriteController.class);
    private final FileWriteService fileWriteService;

    public FileWriteController(FileWriteService fileWriteService) {
        this.fileWriteService = fileWriteService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFile(Authentication auth, @ModelAttribute FileUploadRequestDto requestDto) throws Exception {
        log.info("OPERACIÓN: El usuario [{}] inició la subida del fichero '{}' (Tamaño: {} bytes).",
                auth.getName(), requestDto.getFileName(), requestDto.getFile().getSize());
        return ResponseEntity.ok(fileWriteService.uploadFile(requestDto, auth.getName()));
    }

    @PostMapping("/move")
    public ResponseEntity<?> moveFiles(Authentication auth,
                                       @RequestParam List<Long> fileIds,
                                       @RequestParam(required = false) Long targetParentId) {
        log.info("OPERACIÓN: El usuario [{}] movió {} elemento(s) a la carpeta ID: {}.",
                auth.getName(), fileIds.size(), targetParentId);
        fileWriteService.moveFiles(fileIds, targetParentId, auth.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/star")
    public ResponseEntity<?> toggleStar(@PathVariable Long id, Authentication auth) {
        log.debug("OPERACIÓN: El usuario [{}] alternó el estado destacado del recurso ID: {}.", auth.getName(), id);
        return ResponseEntity.ok(fileWriteService.toggleStar(id, auth.getName()));
    }

    @PostMapping("/{id}/rename")
    public ResponseEntity<?> renameFile(Authentication auth, @PathVariable Long id, @RequestParam("name") String newName) {
        log.info("OPERACIÓN: El usuario [{}] renombró el recurso ID: {} a '{}'.", auth.getName(), id, newName);
        return ResponseEntity.ok(fileWriteService.renameFile(id, newName, auth.getName()));
    }

    @PostMapping("/copy")
    public ResponseEntity<?> copyFiles(Authentication auth,
                                       @RequestParam("fileIds") List<Long> fileIds,
                                       @RequestParam(value = "targetParentId", required = false) Long targetParentId,
                                       @RequestParam(value = "newName", defaultValue = "") String newName) {
        log.info("OPERACIÓN: El usuario [{}] copió {} elemento(s).", auth.getName(), fileIds.size());
        fileWriteService.copyFiles(fileIds, targetParentId, newName, auth.getName());
        return ResponseEntity.ok().build();
    }
}