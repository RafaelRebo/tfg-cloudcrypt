package com.cloudcrypt.controller.admin;

import com.cloudcrypt.dto.admin.AdminStatsDto;
import com.cloudcrypt.service.admin.AdminService;
import com.cloudcrypt.service.user.UserDeleteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService adminService;
    private final UserDeleteService userDeleteService;

    public AdminController(AdminService adminService, UserDeleteService userDeleteService) {
        this.adminService = adminService;
        this.userDeleteService = userDeleteService;
    }

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDto> getSystemVolumeStats(Authentication auth) {
        log.info("ADMIN: El administrador [{}] solicitó el informe de almacenamiento global.", auth.getName());
        return ResponseEntity.ok(adminService.getSystemVolumeStats());
    }

    @PostMapping("/users/{id}/manage")
    public ResponseEntity<Void> updateUserParameters(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam Long quotaBytes,
            @RequestParam String role) {

        log.info("ADMIN: El administrador [{}] modificó los parámetros del usuario ID: {} (Nueva Cuota: {} bytes, Rol: {}).",
                auth.getName(), id, quotaBytes, role);
        adminService.updateUserParameters(id, quotaBytes, role, auth.getName());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUserAndData(Authentication auth, @PathVariable Long id) {
        log.warn("ADMIN: El administrador [{}] ha borrado el usuario ID: {}.", auth.getName(), id);
        userDeleteService.purgeUserFully(id);
        return ResponseEntity.ok().build();
    }
}