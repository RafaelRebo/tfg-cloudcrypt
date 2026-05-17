package com.example.controller.user;

import com.example.dto.user.UserDto;
import com.example.service.user.UserService;
import com.example.config.JwtUtils;
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
    public ResponseEntity<UserDto> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String email) {
        return ResponseEntity.ok(userService.register(username, password, email));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String username, @RequestParam String password) {
        UserDto user = userService.authenticate(username, password);
        String token = jwtUtils.generateToken(user.getUsername());

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.getUsername());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<String>> searchUsers(@RequestParam String q, Authentication auth) {
        return ResponseEntity.ok(userService.searchOtherUsers(q, auth.getName()));
    }
}