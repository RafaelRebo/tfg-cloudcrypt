package com.cloudcrypt.service.user;

import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.exceptions.InvalidCredentialsException;
import com.cloudcrypt.exceptions.UserAlreadyExistsException;
import com.cloudcrypt.mapper.UserMapper;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.UserKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.StorageUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final FileRepository fileRepository;
    private final StorageUtils storageUtils;
    private final UserKeyRepository userKeyRepository;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper,
                       FileRepository fileRepository, com.cloudcrypt.util.StorageUtils storageUtils,
                       com.cloudcrypt.repository.keys.UserKeyRepository userKeyRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.fileRepository = fileRepository;
        this.storageUtils = storageUtils;
        this.userKeyRepository = userKeyRepository;
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

        if (query == null || query.trim().isEmpty()) {
            users = userRepository.findAll();
        } else {
            users = userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(query, query);
        }

        return users.stream()
                .filter(u -> !u.getUsername().equals(currentUsername))
                .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getAvatarUrl(), u.getRole(), u.getQuotaBytes()))
                .limit(50)
                .collect(Collectors.toList());
    }

    public String storeAvatar(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        try {
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

            return "/static/avatars/" + uniqueFilename;

        } catch (IOException e) {
            throw new RuntimeException("Error crítico al guardar el avatar en el disco: " + e.getMessage());
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public UserDto updateProfile(String oldUsername, String fullName, String newUsername,
                                 String newPassword, String removeAvatar, String newAvatarUrl, String email) {
        UserEntity user = userRepository.findByUsername(oldUsername);
        if (user == null) {
            throw new com.cloudcrypt.exceptions.InstanceNotFoundException("Usuario inexistente.");
        }

        if (newUsername != null && !newUsername.trim().isEmpty() && !newUsername.equals(oldUsername)) {
            if (userRepository.findByUsername(newUsername) != null) {
                throw new UserAlreadyExistsException("El ID de usuario '" + newUsername + "' ya está registrado por otra cuenta.");
            }
            user.setUsername(newUsername.trim());
        }

        if (newPassword != null && !newPassword.isEmpty()) {
            user.setPassword(passwordEncoder.encode(preHash(newPassword)));
        }

        if ("true".equalsIgnoreCase(removeAvatar)) {
            user.setAvatarUrl(null);
        } else if (newAvatarUrl != null) {
            user.setAvatarUrl(newAvatarUrl);
        }

        user.setFullName(fullName);

        user.setEmail(email != null && !email.trim().isEmpty() ? email.trim() : null);

        return userMapper.toDto(userRepository.save(user));
    }

    @Transactional(rollbackFor = Exception.class)
    public void purgeUserFully(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new com.cloudcrypt.exceptions.InstanceNotFoundException("Usuario inexistente en la plataforma."));

        List<FileEntity> userFiles = fileRepository.findByOwnerUsername(user.getUsername());
        for (FileEntity file : userFiles) {
            try {
                if (file.getStoragePath() != null) {
                    storageUtils.deletePhysicalFile(file.getStoragePath());
                }
            } catch (Exception e) {
                System.err.println("Advertencia de I/O: No se pudo eliminar el paquete cifrado en: " + file.getStoragePath());
            }
        }

        if (user.getAvatarUrl() != null && user.getAvatarUrl().startsWith("/static/avatars/")) {
            try {
                String filename = user.getAvatarUrl().substring(user.getAvatarUrl().lastIndexOf("/") + 1);
                Path avatarPath = java.nio.file.Paths.get(uploadDir, "avatars", filename);
                Files.deleteIfExists(avatarPath);
            } catch (java.io.IOException e) {
                System.err.println("Advertencia de I/O: No se pudo purgar la imagen de avatar en disco: " + e.getMessage());
            }
        }

        fileRepository.deleteByOwnerId(userId);
        userKeyRepository.deleteById(userId);
        userRepository.delete(user);
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