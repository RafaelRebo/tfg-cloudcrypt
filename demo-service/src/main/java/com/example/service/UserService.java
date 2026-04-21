package com.example.service;

import com.example.dto.UserDto;
import com.example.model.UserEntity;
import com.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // Ahora devuelve UserDTO en lugar de UserEntity
    public UserDto register(String username, String password, String email) {
        if (userRepository.findByUsername(username) != null) {
            throw new RuntimeException("El usuario ya existe");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);

        UserEntity savedUser = userRepository.save(user);

        // Convertimos a DTO antes de devolver
        return new UserDto(savedUser.getId(), savedUser.getUsername(), savedUser.getEmail());
    }

    // Ahora devuelve UserDTO en lugar de UserEntity
    public UserDto authenticate(String username, String rawPassword) {
        UserEntity user = userRepository.findByUsername(username);

        if (user != null && passwordEncoder.matches(rawPassword, user.getPassword())) {
            // Convertimos a DTO: la contraseña (hash) se queda en la entidad, no pasa al DTO
            return new UserDto(user.getId(), user.getUsername(), user.getEmail());
        }
        return null;
    }

    public UserEntity findEntityByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean checkPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
