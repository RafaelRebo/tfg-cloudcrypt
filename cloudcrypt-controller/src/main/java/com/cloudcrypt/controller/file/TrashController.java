package com.cloudcrypt.controller.file;

import com.cloudcrypt.service.file.TrashService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
public class TrashController {
    private final TrashService trashService;

    public TrashController(TrashService trashService) {
        this.trashService = trashService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(Authentication auth,
            @PathVariable Long id,
            @RequestParam(value = "permanent", defaultValue = "false") boolean permanent) {
            trashService.deleteFile(id, auth.getName(), permanent);
            return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreFile(Authentication auth, @PathVariable Long id) {
            trashService.restoreFile(id, auth.getName());
            return ResponseEntity.ok().build();
    }
}
