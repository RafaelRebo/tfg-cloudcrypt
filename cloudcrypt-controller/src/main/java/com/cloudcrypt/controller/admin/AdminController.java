package com.cloudcrypt.controller.admin;

import com.cloudcrypt.dto.admin.AdminStatsDto;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.service.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final UserService userService;

    public AdminController(UserRepository userRepository, FileRepository fileRepository, UserService userService) {
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.userService = userService;
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getSystemVolumeStats(Authentication auth) {
        UserEntity requester = userRepository.findByUsername(auth.getName());
        if (requester == null || !"ADMIN".equalsIgnoreCase(requester.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso restringido: Se requieren privilegios de Administrador Maestro.");
        }

        List<UserEntity> allUsers = userRepository.findAll();
        List<AdminStatsDto.UserDiskMetric> metrics = new ArrayList<>();
        long totalGlobalBytes = 0;

        for (UserEntity user : allUsers) {
            long bytesUsed = fileRepository.getTotalUsageByUser(user.getUsername());
            long filesOwned = fileRepository.countFilesByUser(user.getUsername());
            totalGlobalBytes += bytesUsed;

            metrics.add(new AdminStatsDto.UserDiskMetric(
                    user.getId(), // ⚡ Inyectamos el ID relacional
                    user.getUsername(),
                    user.getFullName(),
                    filesOwned,
                    bytesUsed,
                    user.getQuotaBytes(), // ⚡ Mapeado de cuota específica
                    user.getRole()        // ⚡ Mapeado de rol actual
            ));
        }

        AdminStatsDto stats = new AdminStatsDto(totalGlobalBytes, metrics);
        return ResponseEntity.ok(stats);
    }

    @PostMapping("/users/{id}/manage")
    public ResponseEntity<?> updateUserParameters(
            Authentication auth,
            @PathVariable Long id,
            @RequestParam Long quotaBytes,
            @RequestParam String role) {

        UserEntity requester = userRepository.findByUsername(auth.getName());
        if (requester == null || !"ADMIN".equalsIgnoreCase(requester.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso denegado.");
        }

        if (requester.getId().equals(id)) {
            return ResponseEntity.badRequest().body("No puedes alterar tus propios privilegios");
        }


        UserEntity target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado en el sistema."));

        target.setQuotaBytes(quotaBytes);
        target.setRole(role.toUpperCase());
        userRepository.save(target);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUserAndData(Authentication auth, @PathVariable Long id) {
        UserEntity requester = userRepository.findByUsername(auth.getName());

        // Verificación de rango de autoridad operativa
        if (requester == null || !"ADMIN".equalsIgnoreCase(requester.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Acceso restringido: Se requieren credenciales de Administrador Maestro.");
        }

        // 🛡️ FILTRO DE CONTENCIÓN: Bloquea intentos de auto-eliminación desde el panel
        if (requester.getId().equals(id)) {
            return ResponseEntity.badRequest()
                    .body("Fallo de seguridad: Un Administrador no puede invocar una purga sobre su propia sesión de control.");
        }

        try {
            // Delegamos la secuencia atómica de borrado físico e institucional al Service Layer
            userService.purgeUserFully(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error crítico durante la secuencia de destrucción de datos: " + e.getMessage());
        }
    }
}