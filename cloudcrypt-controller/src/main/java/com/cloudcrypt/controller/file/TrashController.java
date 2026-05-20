package com.cloudcrypt.controller.file;

import com.cloudcrypt.service.file.TrashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
public class TrashController {

    private static final Logger log = LoggerFactory.getLogger(TrashController.class);
    private final TrashService trashService;

    public TrashController(TrashService trashService) {
        this.trashService = trashService;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(Authentication auth,
                                           @PathVariable Long id,
                                           @RequestParam(value = "permanent", defaultValue = "false") boolean permanent) {

        if (permanent) {
            log.warn("OPERACIÓN: El usuario [{}] borró definitivamente el recurso ID: {}.", auth.getName(), id);
        } else {
            log.info("OPERACIÓN: El usuario [{}] envió el recurso ID: {} a la papelera de reciclaje.", auth.getName(), id);
        }

        trashService.deleteFile(id, auth.getName(), permanent);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restoreFile(Authentication auth, @PathVariable Long id) {
        log.info("OPERACIÓN: El usuario [{}] restauró el recurso ID: {} desde la papelera.", auth.getName(), id);
        trashService.restoreFile(id, auth.getName());
        return ResponseEntity.ok().build();
    }
}