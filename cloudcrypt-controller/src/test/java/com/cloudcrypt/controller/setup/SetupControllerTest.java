package com.cloudcrypt.controller.setup;

import com.cloudcrypt.config.ConfigPathResolver;
import com.cloudcrypt.config.CryptoConfig;
import com.cloudcrypt.dto.setup.SetupRequestDto;
import com.cloudcrypt.service.setup.SetupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.File;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SetupControllerTest {

    private MockMvc mockMvc;
    private SetupService setupService;
    private CryptoConfig cryptoConfig;
    private ConfigurableApplicationContext context;

    @BeforeEach
    void setUp() {
        setupService = Mockito.mock(SetupService.class);
        cryptoConfig = Mockito.mock(CryptoConfig.class);

        // Construcción del entorno
        mockMvc = MockMvcBuilders.standaloneSetup(new SetupController(setupService, cryptoConfig,context)).build();
    }

    // ==========================================
    // 1. TEST: checkStatus()
    // ==========================================

    @Test
    @DisplayName("STP-01: Verificación del estado de instalación cuando el archivo de configuración existe")
    void checkStatus_ReturnsInstalledTrue_WhenConfigFileExists() throws Exception {
        // Simulación del archivo de propiedades
        File mockFile = Mockito.mock(File.class);
        when(mockFile.exists()).thenReturn(true);

        // Hacemos que la ruta hacia el fichero de configuración se resuelva sin errores
        try (MockedStatic<ConfigPathResolver> mockedResolver = Mockito.mockStatic(ConfigPathResolver.class)) {
            mockedResolver.when(ConfigPathResolver::getConfigFile).thenReturn(mockFile);

            mockMvc.perform(get("/api/setup/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.installed").value(true)); // Verificamos que la aplicación detecta que está instalada
        }
    }

    // ==========================================
    // 2. TEST: testDatabaseConnection()
    // ==========================================

    @Test
    @DisplayName("STP-02: Verificación de la conexión JDBC contra el motor de base de datos MySQL")
    void testDatabaseConnection_Success_WhenCredentialsAreValid() throws Exception {
        doNothing().when(setupService).testDatabaseConnection(any(SetupRequestDto.class));

        // Recreamos el payload con los parámetros de conexión a la BD
        String jsonPayload = "{\"dbHost\":\"localhost\",\"dbPort\":3306,\"dbUser\":\"root\",\"dbPassword\":\"pass1\",\"dbName\":\"cloudcrypt_db\"}";

        mockMvc.perform(post("/api/setup/test-db")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(content().string("Conexión con el motor MySQL establecida con éxito.")); // Verificamos que se devuelve el mensaje de éxito
    }

    // ==========================================
    // 3. TEST: finalizeInstallation()
    // ==========================================

    @Test
    @DisplayName("STP-03: Almacenamiento correcto de las propiedades del sistema")
    void finalizeInstallation_Success_WithMultipartData() throws Exception {
        // Avatar del admin
        MockMultipartFile mockAvatar = new MockMultipartFile(
                "avatar", "admin.png", MediaType.IMAGE_PNG_VALUE, "--- bytes ---".getBytes()
        );

        when(setupService.storeAdminAvatar(eq("/uploads"), any())).thenReturn("/uploads/avatars/admin.png");
        doNothing().when(setupService).writeConfigurationProperties(any(SetupRequestDto.class), eq("/uploads/avatars/admin.png"));

        // Simulamos llamada al endpoint para guardar la configuración inicial y verificamos que se procesa con éxito
        mockMvc.perform(multipart("/api/setup/submit")
                        .file(mockAvatar)
                        .param("dbHost", "localhost")
                        .param("dbPort", "3306")
                        .param("adminUsername", "user1")
                        .param("adminPassword", "pass1")
                        .param("uploadDir", "/uploads"))
                .andExpect(status().isOk())
                .andExpect(content().string("Configuración guardada con éxito. Reiniciando..."));
    }

    // ==========================================
    // 4. TEST: getLiveCryptoSpecs()
    // ==========================================

    @Test
    @DisplayName("STP-04: Lectura correcta de las especificaciones criptográficas")
    void getLiveCryptoSpecs_Success_ReturnsActiveAlgorithms() throws Exception {
        when(cryptoConfig.getHashAlgorithm()).thenReturn("SHA-256");
        when(cryptoConfig.getSymmetricAlgorithm()).thenReturn("AES/GCM/NoPadding");
        when(cryptoConfig.getAsymmetricKeySize()).thenReturn(2048);

        // Verificamos que el endpoint devuelve los datos tal cual los configuramos
        mockMvc.perform(get("/api/setup/crypto-specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hashAlgo").value("SHA-256"))
                .andExpect(jsonPath("$.symAlgo").value("AES/GCM/NoPadding"))
                .andExpect(jsonPath("$.asymKeySize").value(2048));
    }
}