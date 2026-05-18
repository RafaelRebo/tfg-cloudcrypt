package com.example.controller.user;

import com.example.dto.user.UserDto;
import com.example.model.UserEntity;
import com.example.service.user.UserService;
import com.example.config.JwtUtils;
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

    public UserController(UserService userService, JwtUtils jwtUtils) {
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping(value = "/register", consumes = {"multipart/form-data"})
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String fullName,
            @RequestParam(required = false) String email,
            @RequestPart(value = "avatar", required = false) MultipartFile avatarFile) {

        String avatarUrl = userService.storeAvatar(avatarFile);
        // Cambiamos el retorno para enviar un mapa idéntico al del login con la sesión inicializada
        UserDto userDto = userService.register(username, password, fullName, email, avatarUrl);
        String token = jwtUtils.generateToken(userDto.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", userDto.getUsername());
        response.put("fullName", fullName);   // ⚡ Directo del parámetro para evitar fallos del mapper
        response.put("avatarUrl", avatarUrl); // ⚡ Directo de la subida
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

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(@RequestParam(required = false, defaultValue = "") String q, Authentication auth) {
        return ResponseEntity.ok(userService.searchOtherUsers(q, auth.getName()));
    }
}