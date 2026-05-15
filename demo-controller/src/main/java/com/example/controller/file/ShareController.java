package com.example.controller.file;

import com.example.dto.file.ShareRequestDto;
import com.example.service.file.FileQueryService;
import com.example.service.file.ShareService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class ShareController {
    private final ShareService shareService;
    private final FileQueryService queryService;

    public ShareController(ShareService shareService, FileQueryService queryService) {
        this.shareService = shareService;
        this.queryService = queryService;
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<?> shareFile(@PathVariable Long id, @RequestBody List<ShareRequestDto> requests, Authentication auth) {
        try {
            shareService.shareFile(id, requests, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }

    @GetMapping("/{id}/key")
    public ResponseEntity<?> getFileKey(@PathVariable Long id, Authentication auth) {
        try {
            String key = queryService.getEncryptedFileKey(id, auth.getName());
            return ResponseEntity.ok(Collections.singletonMap("encryptedFileKey", key));
        } catch (Exception e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).build(); }
    }

    @GetMapping("/{id}/shared-users")
    public ResponseEntity<?> getSharedUsers(@PathVariable Long id, Authentication auth) {
        try {
            return ResponseEntity.ok(shareService.getSharedUsernames(id, auth.getName()));
        } catch (Exception e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @DeleteMapping("/{id}/share/revoke")
    public ResponseEntity<?> revokeAccess(@PathVariable Long id, @RequestParam String target, Authentication auth) {
        try {
            shareService.revokeAccess(id, target, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) { return ResponseEntity.badRequest().body(e.getMessage()); }
    }
}
