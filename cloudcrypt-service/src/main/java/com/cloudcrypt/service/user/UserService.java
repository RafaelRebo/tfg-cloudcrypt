package com.cloudcrypt.service.user;

import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.exceptions.InvalidCredentialsException;
import com.cloudcrypt.exceptions.UserAlreadyExistsException;
import com.cloudcrypt.mapper.UserMapper;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    public UserDto register(String username, String password, String fullName, String email, String avatar, String salt){
        if (userRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException("El usuario '" + username + "' ya está registrado.");
        }
        String encodedPassword = passwordEncoder.encode(preHash(password));
        UserEntity user = userRepository.createUser(username, encodedPassword, fullName, email, avatar);
        user.setSalt(salt);

        return userMapper.toDto(userRepository.save(user));
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
        List<UserEntity> users = (query == null || query.trim().isEmpty())
                ? userRepository.findAll()
                : userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(query, query);

        return users.stream()
                .filter(u -> !u.getUsername().equals(currentUsername))
                .map(u -> new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getAvatarUrl(), u.getRole(), u.getQuotaBytes(), u.getSalt()))
                .limit(50)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public UserDto updateProfile(String oldUsername, com.cloudcrypt.dto.user.UpdateProfileRequest request, String newAvatarUrl) {
        UserEntity user = userRepository.findByUsername(oldUsername);
        if (user == null) {
            throw new com.cloudcrypt.exceptions.InstanceNotFoundException("Usuario inexistente.");
        }

        if (request.getNewUsername() != null && !request.getNewUsername().trim().isEmpty() && !request.getNewUsername().equals(oldUsername)) {
            if (userRepository.findByUsername(request.getNewUsername()) != null) {
                throw new UserAlreadyExistsException("El ID de usuario '" + request.getNewUsername() + "' ya está registrado.");
            }
            user.setUsername(request.getNewUsername().trim());
        }

        if (request.getNewPassword() != null && !request.getNewPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(preHash(request.getNewPassword())));
        }

        if ("true".equalsIgnoreCase(request.getRemoveAvatar())) {
            user.setAvatarUrl(null);
        } else if (newAvatarUrl != null) {
            user.setAvatarUrl(newAvatarUrl);
        }

        user.setFullName(request.getFullName());
        user.setEmail(request.getEmail() != null && !request.getEmail().trim().isEmpty() ? request.getEmail().trim() : null);

        return userMapper.toDto(userRepository.save(user));
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
            throw new RuntimeException("Fallo en el pre-hash", e);
        }
    }
}