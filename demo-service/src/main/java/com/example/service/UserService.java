package com.example.service;

import com.example.dto.UserDto;
import com.example.exceptions.InvalidCredentialsException;
import com.example.exceptions.UserAlreadyExistsException;
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

    public UserDto register(String username, String password, String email) throws UserAlreadyExistsException {
        if (userRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException("El usuario '" + username + "' ya está registrado.");
        }

        String encodedPassword = passwordEncoder.encode(password);
        UserEntity user = userRepository.createUser(username, encodedPassword, email);

        return userMapper.toDto(user);
    }

    public UserDto authenticate(String username, String rawPassword) throws InvalidCredentialsException {
        UserEntity user = userRepository.findByUsername(username);
        if (user != null && passwordEncoder.matches(rawPassword, user.getPassword())) {
            return userMapper.toDto(user);
        }
        else throw new InvalidCredentialsException("Las credenciales introducidas son incorrectas");
    }
}