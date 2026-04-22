package com.example.service;

import com.example.dto.UserDto;
import com.example.mapper.UserMapper;
import com.example.model.UserEntity;
import com.example.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserMapper userMapper;

    public UserDto register(String username, String password, String email) {
        if (userRepository.findByUsername(username) != null) {
            throw new RuntimeException("El usuario ya existe");
        }

        String encodedPassword = passwordEncoder.encode(password);
        UserEntity user = userRepository.createUser(username, encodedPassword, email);

        return userMapper.toDto(user);
    }

    public UserDto authenticate(String username, String rawPassword) {
        UserEntity user = userRepository.findByUsername(username);
        if (user != null && passwordEncoder.matches(rawPassword, user.getPassword())) {
            return userMapper.toDto(user);
        }
        return null;
    }
}