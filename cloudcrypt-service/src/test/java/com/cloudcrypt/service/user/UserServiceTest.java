package com.cloudcrypt.service.user;

import com.cloudcrypt.dto.user.UpdateProfileRequestDto;
import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.exceptions.InvalidCredentialsException;
import com.cloudcrypt.exceptions.UserAlreadyExistsException;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.mapper.UserMapper;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserService userService;

    private UserEntity userEntity;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user1");
        userEntity.setPassword("pass1");
        userEntity.setFullName("Usuario 1");
        userEntity.setEmail("user1@cloudcrypt.com");
        userEntity.setAvatarUrl("/avatars/user1.png");
        userEntity.setRole("USER");
        userEntity.setSalt("salt1");

        userDto = new UserDto(1L, "user1", "user1@cloudcrypt.com", "Usuario 1", "/avatars/user1.png", "USER", 1073741824L, "salt1");
    }

    // ==========================================
    // 1. TEST: register()
    // ==========================================

    @Test
    @DisplayName("REG-01: Registro exitoso cuando el nombre de usuario está disponible")
    void register_Success_WhenUsernameIsFree() {
        when(userRepository.findByUsername("user1")).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("pass1");
        when(userRepository.createUser(eq("user1"), anyString(), eq("Usuario 1"), eq("user1@cloudcrypt.com"), eq("/avatars/user1.png"))).thenReturn(userEntity);
        when(userRepository.save(any(UserEntity.class))).thenReturn(userEntity);
        when(userMapper.toDto(any(UserEntity.class))).thenReturn(userDto);

        UserDto result = userService.register("user1", "pass1", "Usuario 1", "user1@cloudcrypt.com", "/avatars/user1.png", "salt1");

        assertNotNull(result);
        assertEquals("user1", result.getUsername());
        verify(userRepository, times(1)).createUser(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("REG-02: Fallo de registro al usar un nombre de usuario que ya existe")
    void register_ThrowsException_WhenUsernameAlreadyExists() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);

        assertThrows(UserAlreadyExistsException.class, () -> {
            userService.register("user1", "pass1", "Usuario 1", "user1@cloudcrypt.com", "/avatars/user1.png", "salt1");
        });

        verify(userRepository, never()).createUser(any(), any(), any(), any(), any());
        verify(userRepository, never()).save(any());
    }

    // ==========================================
    // 2. TEST: authenticate()
    // ==========================================

    @Test
    @DisplayName("AUTH-01: Autenticación exitosa con credenciales válidas")
    void authenticate_Success_WhenCredentialsAreValid() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(passwordEncoder.matches(anyString(), eq("pass1"))).thenReturn(true);
        when(userMapper.toDto(userEntity)).thenReturn(userDto);

        UserDto result = userService.authenticate("user1", "pass1");

        assertNotNull(result);
        assertEquals("Usuario 1", result.getFullName());
        assertEquals("/avatars/user1.png", result.getAvatarUrl());
    }

    @Test
    @DisplayName("AUTH-02: Fallo de autenticación porque el usuario no existe")
    void authenticate_ThrowsException_WhenUserDoesNotExist() {
        when(userRepository.findByUsername("user2")).thenReturn(null);

        assertThrows(InvalidCredentialsException.class, () -> {
            userService.authenticate("user2", "pass2");
        });
    }

    @Test
    @DisplayName("AUTH-03: Fallo de autenticación porque la contraseña es incorrecta")
    void authenticate_ThrowsException_WhenPasswordIsIncorrect() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(passwordEncoder.matches(anyString(), eq("pass1"))).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> {
            userService.authenticate("user1", "pass2");
        });
    }

    // ==========================================
    // 3. TESTS: searchOtherUsers()
    // ==========================================

    @Test
    @DisplayName("SRCH-01: Búsqueda sin filtro devuelve todos menos el actual")
    void searchOtherUsers_ReturnsAllExceptCurrent_WhenQueryIsEmpty() {
        UserEntity user2 = new UserEntity();
        user2.setId(2L);
        user2.setUsername("user2");
        user2.setEmail("user2@cloudcrypt.com");

        when(userRepository.findAll()).thenReturn(List.of(userEntity, user2));

        List<UserDto> results = userService.searchOtherUsers("", "user1");

        assertEquals(1, results.size());
        assertEquals("user2", results.get(0).getUsername());
        verify(userRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("SRCH-02: Búsqueda con filtro devuelve coincidencias")
    void searchOtherUsers_ReturnsFilteredUsers_WhenQueryIsPresent() {
        UserEntity user2 = new UserEntity();
        user2.setId(2L);
        user2.setUsername("user2");

        when(userRepository.findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase("user2", "user2"))
                .thenReturn(List.of(user2));

        List<UserDto> results = userService.searchOtherUsers("user2", "user1");

        assertEquals(1, results.size());
        assertEquals("user2", results.get(0).getUsername());
    }

    @Test
    @DisplayName("SRCH-03: La búsqueda limita la salida a un máximo de 50 resultados")
    void searchOtherUsers_LimitsResultsToFifty() {
        List<UserEntity> users = new ArrayList<>();
        for (long i = 10; i < 70; i++) {
            UserEntity u = new UserEntity();
            u.setId(i);
            u.setUsername("user" + i);
            users.add(u);
        }
        when(userRepository.findAll()).thenReturn(users);

        List<UserDto> results = userService.searchOtherUsers(null, "user1");

        assertEquals(50, results.size());
    }

    // ==========================================
    // 4. TESTS: updateProfile()
    // ==========================================

    @Test
    @DisplayName("UPD-01: Actualización de un usuario que no existe")
    void updateProfile_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByUsername("user1")).thenReturn(null);
        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();

        assertThrows(InstanceNotFoundException.class, () -> {
            userService.updateProfile("user1", dto, null);
        });
    }

    @Test
    @DisplayName("UPD-02: Actualización simple de FullName y email")
    void updateProfile_Success_SimpleMetadataChange() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userRepository.save(userEntity)).thenReturn(userEntity);
        when(userMapper.toDto(userEntity)).thenReturn(userDto);

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user1");
        dto.setFullName("Usuario 1 actualizado");
        dto.setEmail("user1updated@cloudcrypt.com");
        dto.setRemoveAvatar("false");

        userService.updateProfile("user1", dto, null);

        assertEquals("Usuario 1 actualizado", userEntity.getFullName());
        assertEquals("user1updated@cloudcrypt.com", userEntity.getEmail());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("UPD-03: Modificación exitosa del nombre de usuario")
    void updateProfile_Success_UsernameChange() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userRepository.findByUsername("user_updated")).thenReturn(null);
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user_updated");
        dto.setFullName("Usuario 1");
        dto.setEmail("user1@cloudcrypt.com");
        dto.setRemoveAvatar("false");

        userService.updateProfile("user1", dto, null);

        assertEquals("user_updated", userEntity.getUsername());
    }

    @Test
    @DisplayName("UPD-04: Conflicto al intentar usar un nombre de usuario existente")
    void updateProfile_ThrowsException_WhenNewUsernameAlreadyTaken() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userRepository.findByUsername("user_duplicado")).thenReturn(new UserEntity());

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user_duplicado");
        dto.setFullName("Usuario 1");
        dto.setEmail("user1@cloudcrypt.com");
        dto.setRemoveAvatar("false");

        assertThrows(UserAlreadyExistsException.class, () -> {
            userService.updateProfile("user1", dto, null);
        });
    }

    @Test
    @DisplayName("UPD-05: Modificación del password")
    void updateProfile_Success_WithPasswordChange() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(passwordEncoder.encode(anyString())).thenReturn("hash_updated");
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user1");
        dto.setNewPassword("pass_updated");
        dto.setFullName("Usuario 1");
        dto.setEmail("user1@cloudcrypt.com");
        dto.setRemoveAvatar("false");

        userService.updateProfile("user1", dto, null);

        assertEquals("hash_updated", userEntity.getPassword());
        verify(passwordEncoder, times(1)).encode(anyString());
    }

    @Test
    @DisplayName("UPD-06: Borrar avatar")
    void updateProfile_Success_RemoveAvatar() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user1");
        dto.setFullName("Usuario 1");
        dto.setEmail("user1@cloudcrypt.com");
        dto.setRemoveAvatar("true");

        userService.updateProfile("user1", dto, null);

        assertNull(userEntity.getAvatarUrl());
    }

    @Test
    @DisplayName("UPD-07: Correo se convierte a nulo si está vacío")
    void updateProfile_Success_EmptyEmailBecomesNull() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user1");
        dto.setFullName("Usuario 1");
        dto.setEmail("   ");
        dto.setRemoveAvatar("false");

        userService.updateProfile("user1", dto, null);

        assertNull(userEntity.getEmail());
    }

    @Test
    @DisplayName("UPD-08: Actualización exitosa del avatar")
    void updateProfile_Success_WithNewAvatarUrl() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userRepository.save(userEntity)).thenReturn(userEntity);

        UpdateProfileRequestDto dto = new UpdateProfileRequestDto();
        dto.setNewUsername("user1");
        dto.setFullName("Usuario 1");
        dto.setEmail("user1@cloudcrypt.com");
        dto.setRemoveAvatar("false");

        userService.updateProfile("user1", dto, "/avatars/user1new.png");

        assertEquals("/avatars/user1new.png", userEntity.getAvatarUrl());
        verify(userRepository, times(1)).save(userEntity);
    }
}