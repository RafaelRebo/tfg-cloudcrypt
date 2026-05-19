package com.cloudcrypt.controller.user;

import com.cloudcrypt.dto.user.KeyRequestDto;
import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.service.user.UserKeyService;
import com.cloudcrypt.service.user.UserService;
import com.cloudcrypt.config.JwtUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final UserKeyService userKeyService;

    public UserController(UserService userService, JwtUtils jwtUtils, UserKeyService userKeyService) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.userKeyService = userKeyService;
    }

    @PostMapping(value = "/register", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String fullName,
            @RequestParam(required = false) String email,
            @RequestPart(value = "avatar", required = false) MultipartFile avatarFile) {

        String avatarUrl = userService.storeAvatar(avatarFile);
        UserDto userDto = userService.register(username, password, fullName, email, avatarUrl);
        String token = jwtUtils.generateToken(userDto.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", userDto.getUsername());
        response.put("fullName", fullName);
        response.put("avatarUrl", avatarUrl);
        response.put("email", userDto.getEmail());
        response.put("role", userDto.getRole());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String username, @RequestParam String password) {
        UserDto user = userService.authenticate(username, password);
        String token = jwtUtils.generateToken(user.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());
        response.put("fullName", user.getFullName());
        response.put("avatarUrl", user.getAvatarUrl());

        response.put("email", user.getEmail());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam(required = false, defaultValue = "") String q, Authentication auth) {
        return ResponseEntity.ok(userService.searchOtherUsers(q, auth.getName()));
    }

    @PostMapping(value = "/profile", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> updateProfile(
            Authentication auth,
            @RequestParam String fullName,
            @RequestParam String removeAvatar,
            @RequestParam(required = false) String newUsername,
            @RequestParam(required = false) String newPassword,
            @RequestParam(required = false) String newEncryptedPrivateKey,
            @RequestPart(value = "avatar", required = false) MultipartFile avatarFile,
            @RequestParam(required = false) String email) {

        String oldUsername = auth.getName();

        if (newEncryptedPrivateKey != null && !newEncryptedPrivateKey.isEmpty()) {
            KeyRequestDto keyDto = new KeyRequestDto();
            Map<String, Object> publicInfo = userKeyService.getPublicInfo(oldUsername);
            keyDto.setPublicKey((String) publicInfo.get("publicKey"));
            keyDto.setEncryptedPrivateKey(newEncryptedPrivateKey);
            userKeyService.registerKeys(oldUsername, keyDto);
        }

        String avatarUrl = null;
        if (avatarFile != null && !avatarFile.isEmpty()) {
            avatarUrl = userService.storeAvatar(avatarFile);
        }

        UserDto updatedUser = userService.updateProfile(
                oldUsername, fullName, newUsername, newPassword, removeAvatar, avatarUrl, email
        );

        String token = jwtUtils.generateToken(updatedUser.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", updatedUser.getUsername());
        response.put("fullName", updatedUser.getFullName());
        response.put("avatarUrl", updatedUser.getAvatarUrl());

        response.put("email", updatedUser.getEmail());
        response.put("role", updatedUser.getRole());

        return ResponseEntity.ok(response);
    }
}