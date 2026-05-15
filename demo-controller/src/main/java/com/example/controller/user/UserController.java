package com.example.controller.user;

import com.example.dto.user.UserDto;
import com.example.exceptions.InvalidCredentialsException;
import com.example.service.user.UserService;
import com.example.config.JwtUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String email) {
        try {
            return ResponseEntity.ok(userService.register(username, password, email));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        try {
            UserDto user = userService.authenticate(username, password);

            String token = jwtUtils.generateToken(user.getUsername());

            Map<String, Object> response = new HashMap<>();
            response.put("token", token);
            response.put("username", user.getUsername());

            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error en el servidor");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<String>> searchUsers(@RequestParam String q, Authentication auth) {
        // Retornamos solo los nombres de usuario que coincidan con la búsqueda
        // y que no sean el usuario actual
        List<String> usernames = userService.searchOtherUsers(q, auth.getName());
        return ResponseEntity.ok(usernames);
    }
}