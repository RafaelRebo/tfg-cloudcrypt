package com.example.service.user;

import com.example.dto.user.UserDto;
import com.example.exceptions.InvalidCredentialsException;
import com.example.exceptions.UserAlreadyExistsException;
import com.example.mapper.UserMapper;
import com.example.model.UserEntity;
import com.example.repository.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public UserDto register(String username, String password, String email){
        if (userRepository.findByUsername(username) != null) {
            throw new UserAlreadyExistsException("El usuario '" + username + "' ya está registrado.");
        }

        String encodedPassword = passwordEncoder.encode(password);
        UserEntity user = userRepository.createUser(username, encodedPassword, email);

        return userMapper.toDto(user);
    }

    public UserDto authenticate(String username, String rawPassword){
        UserEntity user = userRepository.findByUsername(username);
        if (user != null && passwordEncoder.matches(rawPassword, user.getPassword())) {
            return userMapper.toDto(user);
        }
        else throw new InvalidCredentialsException("Las credenciales son incorrectas o el usuario no existe");
    }

    public List<String> searchOtherUsers(String query, String currentUsername) {
        return userRepository.findByUsernameContainingIgnoreCase(query)
                .stream()
                .map(UserEntity::getUsername)
                .filter(name -> !name.equals(currentUsername))
                .limit(10) // Limitamos para no saturar el modal
                .collect(Collectors.toList());
    }
}