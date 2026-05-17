package com.example.controller.user;

import com.example.dto.user.KeyRequestDto;
import com.example.service.user.UserKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/keys")
@CrossOrigin(origins = "*")
public class UserKeyController {

    private final UserKeyService userKeyService;

    public UserKeyController(UserKeyService userKeyService) {
        this.userKeyService = userKeyService;
    }

    @PostMapping("/register")
    public ResponseEntity<String> registerKeys(@RequestBody KeyRequestDto request, Authentication auth) {
        userKeyService.registerKeys(auth.getName(), request);
        return ResponseEntity.ok("Llaves registradas correctamente");
    }

    @GetMapping("/public/{username}")
    public ResponseEntity<Map<String, Object>> getPublicKey(@PathVariable String username) {
        return ResponseEntity.ok(userKeyService.getPublicInfo(username));
    }

    @GetMapping("/my-private")
    public ResponseEntity<String> getMyPrivateKey(Authentication auth) {
        return ResponseEntity.ok(userKeyService.getEncryptedPrivateKey(auth.getName()));
    }
}