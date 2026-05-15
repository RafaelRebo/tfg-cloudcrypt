package com.example.controller.user;

import com.example.dto.user.KeyRequestDto;
import com.example.service.user.UserKeyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keys")
@CrossOrigin(origins = "*")
public class UserKeyController {

    private final UserKeyService userKeyService;

    public UserKeyController(UserKeyService userKeyService) {
        this.userKeyService = userKeyService;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerKeys(@RequestBody KeyRequestDto request, Authentication auth) {
        try {
            userKeyService.registerKeys(auth.getName(), request);
            return ResponseEntity.ok("Llaves registradas correctamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/public/{username}")
    public ResponseEntity<?> getPublicKey(@PathVariable String username) {
        try {
            return ResponseEntity.ok(userKeyService.getPublicInfo(username));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @GetMapping("/my-private")
    public ResponseEntity<?> getMyPrivateKey(Authentication auth) {
        try {
            return ResponseEntity.ok(userKeyService.getEncryptedPrivateKey(auth.getName()));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}