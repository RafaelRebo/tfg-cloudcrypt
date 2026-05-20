package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.ShareRequestDto;
import com.cloudcrypt.service.file.FileQueryService;
import com.cloudcrypt.service.file.ShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);
    private final ShareService shareService;
    private final FileQueryService queryService;

    public ShareController(ShareService shareService, FileQueryService queryService) {
        this.shareService = shareService;
        this.queryService = queryService;
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<?> shareFile(@PathVariable Long id, @RequestBody List<ShareRequestDto> requests, Authentication auth) {
        log.info("COMPARTIDOS: El usuario [{}] modificó el acceso compartido para el recurso ID: {}.", auth.getName(), id);
        shareService.shareFile(id, requests, auth.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/share/batch")
    public ResponseEntity<?> shareFilesBatch(Authentication auth, @RequestBody List<ShareRequestDto> requests) {
        log.info("COMPARTIDOS: El usuario [{}] cambió el acceso compartido en bloque para {} elemento(s).", auth.getName(), requests.size());
        shareService.shareBatch(requests, auth.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/key")
    public ResponseEntity<?> getFileKey(@PathVariable Long id, Authentication auth) {
        log.debug("COMPARTIDOS: El usuario [{}] solicitó la clave del recurso ID: {}.", auth.getName(), id);
        String key = queryService.getEncryptedFileKey(id, auth.getName());
        return ResponseEntity.ok(Collections.singletonMap("encryptedFileKey", key));
    }

    @GetMapping("/{id}/shared-users")
    public ResponseEntity<?> getSharedUsers(@PathVariable Long id, Authentication auth) {
        log.debug("COMPARTIDOS: El usuario [{}] solicitó los usuarios con acceso al archivo ID: {}.", auth.getName(), id);
        return ResponseEntity.ok(shareService.getSharedUsernames(id, auth.getName()));
    }

    @DeleteMapping("/{id}/share/revoke")
    public ResponseEntity<?> revokeAccess(@PathVariable Long id, @RequestParam String target, Authentication auth) {
        log.warn("COMPARTIDOS: El usuario [{}] revocó el acceso al usuario @{} sobre el recurso ID: {}.", auth.getName(), target, id);
        shareService.revokeAccess(id, target, auth.getName());
        return ResponseEntity.ok().build();
    }
}