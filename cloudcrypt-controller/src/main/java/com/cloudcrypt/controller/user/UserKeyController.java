package com.cloudcrypt.controller.user;

import com.cloudcrypt.dto.user.KeyRequestDto;
import com.cloudcrypt.service.user.UserKeyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/keys")
@CrossOrigin(origins = "*")
public class UserKeyController {

    private static final Logger log = LoggerFactory.getLogger(UserKeyController.class);
    private final UserKeyService userKeyService;

    public UserKeyController(UserKeyService userKeyService) {
        this.userKeyService = userKeyService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerKeys(@RequestBody KeyRequestDto request, Authentication auth) {
        log.warn("OPERACIÓN: Claves asimétricas registradas para el usuario [{}].", auth.getName());
        userKeyService.registerKeys(auth.getName(), request);
        return ResponseEntity.ok("Llaves registradas correctamente");
    }

    @GetMapping("/public/{username}")
    public ResponseEntity<Map<String, Object>> getPublicKey(@PathVariable String username) {
        log.debug("OPERACIÓN: Clave pública RSA solicitada para el usuario: [{}]", username);
        return ResponseEntity.ok(userKeyService.getPublicInfo(username));
    }

    @GetMapping("/my-private")
    public ResponseEntity<String> getMyPrivateKey(Authentication auth) {
        log.info("OPERACIÓN: El usuario [{}] solicitó la descarga de su clave privada cifrada.", auth.getName());
        return ResponseEntity.ok(userKeyService.getEncryptedPrivateKey(auth.getName()));
    }
}