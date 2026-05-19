package com.cloudcrypt.service.user;

import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.exceptions.InvalidCredentialsException;
import com.cloudcrypt.exceptions.UserAlreadyExistsException;
import com.cloudcrypt.mapper.UserMapper;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public UserDto register(String username, String password, String fullName, String email, String avatar){
        if (userRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException("El usuario '" + username + "' ya está registrado.");
        }

        String encodedPassword = passwordEncoder.encode(preHash(password));
        UserEntity user = userRepository.createUser(username, encodedPassword, fullName, email, avatar);

        return userMapper.toDto(user);
    }

    public UserDto authenticate(String username, String rawPassword){
        UserEntity user = userRepository.findByUsername(username);

        if (user != null && passwordEncoder.matches(preHash(rawPassword), user.getPassword())) {
            UserDto dto = userMapper.toDto(user);
            dto.setFullName(user.getFullName());
            dto.setAvatarUrl(user.getAvatarUrl());

            return dto;
        }
        else throw new InvalidCredentialsException("Las credenciales son incorrectas o el usuario no existe");
    }

    public List<UserDto> searchOtherUsers(String query, String currentUsername) {
        List<UserEntity> users;

        // 🛡️ Si la query está vacía, recuperamos todos los usuarios del sistema
        if (query == null || query.trim().isEmpty()) {
            users = userRepository.findAll();
        } else {
            // Si hay texto, filtramos por username o fullName usando el nuevo método
            users = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(query, query);
        }

        return users.stream()
                .filter(u -> !u.getUsername().equals(currentUsername)) // Te excluye a ti de la lista
                .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getAvatarUrl()))
                .limit(50) // Evita saturar la memoria si la base de datos crece
                .collect(Collectors.toList());
    }

    private final Path rootFolder = Paths.get("uploads/avatars");

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        try {
            // ⚡ Construimos la ruta de avatares basándonos en la configuración global
            Path rootFolder = Paths.get(uploadDir, "avatars");

            if (!Files.exists(rootFolder)) {
                Files.createDirectories(rootFolder);
            }

            String originalName = file.getOriginalFilename();
            String extension = originalName != null && originalName.contains(".")
                    ? originalName.substring(originalName.lastIndexOf(".")) : ".png";

            String uniqueFilename = UUID.randomUUID().toString() + extension;

            Path targetPath = rootFolder.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Devolvemos la URI web relativa
            return "/static/avatars/" + uniqueFilename;

        } catch (IOException e) {
            throw new RuntimeException("Error crítico al guardar el avatar en el disco: " + e.getMessage());
        }
    }

    private String preHash(String input) {
        if (input == null) return null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Fallo en el pre-hash de adaptación", e);
        }
    }
}