package com.cloudcrypt.controller.file;

import com.cloudcrypt.service.file.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/files")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);
    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getUserStats(Authentication auth) {
        log.debug("OPERACIÓN: Recopilando métricas de uso para [{}].", auth.getName());
        return ResponseEntity.ok(statsService.getUserStats(auth.getName()));
    }
}