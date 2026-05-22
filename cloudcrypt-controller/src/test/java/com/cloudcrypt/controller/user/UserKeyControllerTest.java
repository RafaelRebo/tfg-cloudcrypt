package com.cloudcrypt.controller.user;

import com.cloudcrypt.dto.user.KeyRequestDto;
import com.cloudcrypt.service.user.UserKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserKeyControllerTest {

    private MockMvc mockMvc;
    private UserKeyService userKeyService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        userKeyService = Mockito.mock(UserKeyService.class);

        // Auth simulado
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        // Construcción del entorno
        mockMvc = MockMvcBuilders.standaloneSetup(new UserKeyController(userKeyService)).build();
    }

    // ==========================================
    // 1. TEST: registerKeys()
    // ==========================================

    @Test
    @DisplayName("KEY-01: Registro exitoso del par de claves asimétricas mediante JSON")
    void registerKeys_Success_WhenPayloadIsValid() throws Exception {
        doNothing().when(userKeyService).registerKeys(eq("user1"), any(KeyRequestDto.class));

        // Payload de una petición de registro de keyring por parte de un usuario
        String jsonPayload = "{\"publicKey\":\"pubkey123\",\"encryptedPrivateKey\":\"encprivkey123\"}";

        // Ejecución de la petición
        mockMvc.perform(post("/api/keys/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(content().string("Llaves registradas correctamente")); // Verificamos que se devuelve mensaje de éxito

        // Verificamos que se llamó una vez al service para registrar las claves
        verify(userKeyService, times(1)).registerKeys(eq("user1"), any(KeyRequestDto.class));
    }

    // ==========================================
    // 2. TEST: getPublicKey()
    // ==========================================

    @Test
    @DisplayName("KEY-02: Recuperación correcta de la clave pública de un usuario")
    void getPublicKey_Success_WhenUserExists() throws Exception {
        // Vinculamos la clave pública pubkey123 al usuario user1
        Map<String, Object> publicInfoMock = Map.of("publicKey", "pubkey123");
        when(userKeyService.getPublicInfo("user1")).thenReturn(publicInfoMock);

        // Con un GET al endpoint correspondiente podemos recuperar dicha clave
        mockMvc.perform(get("/api/keys/public/user1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("pubkey123"));
    }

    // ==========================================
    // 3. TEST: getMyPrivateKey()
    // ==========================================

    @Test
    @DisplayName("KEY-03: Descarga exitosa de la clave privada cifrada del usuario autenticado")
    void getMyPrivateKey_Success_WhenUserIsAuthenticated() throws Exception {
        // Configuramos encprivkey123 como la clave privada encriptada del usuario user1 en el servidor
        when(userKeyService.getEncryptedPrivateKey("user1")).thenReturn("encprivkey123");

        // Ejecutamos la petición, esta vez autenticados como user1 mediante mockAuth, y recibimos la clave privada
        mockMvc.perform(get("/api/keys/my-private")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(content().string("encprivkey123"));
    }
}