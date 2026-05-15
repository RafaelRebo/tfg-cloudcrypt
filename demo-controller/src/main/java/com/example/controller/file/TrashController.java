package com.example.controller.file;

import com.example.service.file.TrashService;
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
    public ResponseEntity<?> deleteFile(Authentication auth, @PathVariable Long id) {
        try {
            trashService.deleteFile(id, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<?> restoreFile(Authentication auth, @PathVariable Long id) {
        try {
            trashService.restoreFile(id, auth.getName());
            return ResponseEntity.ok().build();
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }
}
