package com.cloudcrypt.controller.user;

import com.cloudcrypt.config.JwtUtils;
import com.cloudcrypt.dto.user.UpdateProfileRequestDto;
import com.cloudcrypt.dto.user.UserDto;
import com.cloudcrypt.service.user.AvatarService;
import com.cloudcrypt.service.user.UserKeyService;
import com.cloudcrypt.service.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private MockMvc mockMvc;

    private UserService userService;
    private JwtUtils jwtUtils;
    private UserKeyService userKeyService;
    private AvatarService avatarService;

    private UserDto userDto;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        jwtUtils = Mockito.mock(JwtUtils.class);
        userKeyService = Mockito.mock(UserKeyService.class);
        avatarService = Mockito.mock(AvatarService.class);

        // Auth simulado
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        // Construcción del entorno
        mockMvc = MockMvcBuilders.standaloneSetup(
                new UserController(userService, jwtUtils, userKeyService, avatarService)
        ).build();

        // Inicialización del DTO de un usuario
        userDto = new UserDto(1L, "user1", "user1@cloudcrypt.com", "Usuario 1", "/avatars/user1.png", "USER", 1073741824L, "salt1");
    }

    // ==========================================
    // 1. TEST: login()
    // ==========================================

    @Test
    @DisplayName("LGN-01: Inicio de sesión con credenciales válidas")
    void login_Success_WhenCredentialsAreValid() throws Exception {
        // Configuramos los servicios para devolver los datos esperados
        when(userService.authenticate("user1", "pass1")).thenReturn(userDto);
        when(jwtUtils.generateToken("user1", "USER")).thenReturn("mock-token-123");

        // Simulamos la petición al endpoint de login y verificamos que el JSON devuelto es correcto
        mockMvc.perform(post("/api/users/login")
                        .param("username", "user1")
                        .param("password", "pass1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock-token-123"))
                .andExpect(jsonPath("$.username").value("user1"))
                .andExpect(jsonPath("$.salt").value("salt1"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    // ==========================================
    // 2. TEST: register()
    // ==========================================

    @Test
    @DisplayName("REG-01: Registro de usuario procesando un archivo de avatar")
    void register_Success_WithOptionalAvatar() throws Exception {
        // Creación del avatar simulado
        MockMultipartFile mockAvatar = new MockMultipartFile(
                "avatar", "user1.png", MediaType.IMAGE_PNG_VALUE, "data".getBytes()
        );

        when(avatarService.storeAvatar(any())).thenReturn("/avatars/user1.png");
        when(userService.register(eq("user1"), eq("pass1"), eq("Usuario 1"), eq("user1@cloudcrypt.com"), eq("/avatars/user1.png"), eq("salt1")))
                .thenReturn(userDto);
        when(jwtUtils.generateToken("user1", "USER")).thenReturn("mock-token-123");

        // Ejecutamos la petición de registro y verificamos que el JSON devuelto contiene, entre otros campos, el username correcto, y como pusimos foto, la URL de la foto
        mockMvc.perform(multipart("/api/users/register")
                        .file(mockAvatar)
                        .param("username", "user1")
                        .param("password", "pass1")
                        .param("fullName", "Usuario 1")
                        .param("email", "user1@cloudcrypt.com")
                        .param("salt", "salt1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user1"))
                .andExpect(jsonPath("$.avatarUrl").value("/avatars/user1.png"));
    }

    // ==========================================
    // 3. TEST: searchOtherUsers()
    // ==========================================

    @Test
    @DisplayName("SRCH-01: Búsqueda filtrada de usuarios")
    void searchOtherUsers_ReturnsFilteredUsers_WhenQueryIsPresent() throws Exception {
        // Creamos los datos del usuario que vamos a encontrar en la búsqueda
        UserDto searchedUser = new UserDto(2L, "user2", "user2@cloudcrypt.com", "Usuario 2", "/avatars/user2.png", "USER", 1073741824L, "salt2");

        // Cuando 'user1' busque 'user2', el servicio devolverá a 'user2'
        when(userService.searchOtherUsers("user2", "user1")).thenReturn(Collections.singletonList(searchedUser));

        // Ejecutamos la petición
        mockMvc.perform(get("/api/users/search")
                        .param("q", "user2")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("user2")); // Encontramos a user2
    }

    // ==========================================
    // 4. TEST: updateProfile()
    // ==========================================

    @Test
    @DisplayName("UPD-01: Modificación del perfil")
    void updateProfile_Success_WithKeyRotationAndMetadata() throws Exception {
        MockMultipartFile mockAvatar = new MockMultipartFile(
                "avatar", "user1new.png", MediaType.IMAGE_PNG_VALUE, "data".getBytes()
        );

        when(avatarService.storeAvatar(any())).thenReturn("/avatars/user1new.png");
        when(userService.updateProfile(eq("user1"), any(UpdateProfileRequestDto.class), eq("/avatars/user1new.png")))
                .thenReturn(userDto);
        when(jwtUtils.generateToken("user1", "USER")).thenReturn("new-mock-token");

        // Hacemos la petición para actualizar el usuario con un nuevo avatar y clave privada, y verificamos que se nos devuelve correctamente un token auth nuevo
        mockMvc.perform(multipart("/api/users/profile")
                        .file(mockAvatar)
                        .param("newEncryptedPrivateKey", "encprivkey")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-mock-token"));
    }
}