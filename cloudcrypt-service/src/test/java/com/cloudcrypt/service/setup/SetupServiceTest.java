package com.cloudcrypt.service.setup;

import com.cloudcrypt.config.ConfigPathResolver;
import com.cloudcrypt.dto.setup.SetupRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SetupServiceTest {

    @InjectMocks
    private SetupService setupService;

    @TempDir
    Path tempDir;

    private SetupRequestDto setupRequestDto;

    @BeforeEach
    void setUp() {
        setupRequestDto = new SetupRequestDto();
        setupRequestDto.setDbHost("localhost");
        setupRequestDto.setDbPort("3306");
        setupRequestDto.setDbName("cloudcrypt_db");
        setupRequestDto.setDbUser("root");
        setupRequestDto.setDbPass("pass");
        setupRequestDto.setUploadDir(tempDir.toString());
        setupRequestDto.setMaxQuotaBytes(String.valueOf(1073741824L));
        setupRequestDto.setMaxFileSizeGb(String.valueOf(2));
        setupRequestDto.setHashAlgo("SHA-256");
        setupRequestDto.setSymAlgo("AES/GCM/NoPadding");
        setupRequestDto.setAsymKeySize(String.valueOf(2048));
        setupRequestDto.setAdminUsername("admin1");
        setupRequestDto.setAdminPassword("adminPass123");
        setupRequestDto.setAdminFullName("Administrador Búnker");
        setupRequestDto.setAdminEmail("admin@cloudcrypt.com");
    }

    // ==========================================
    // 1. TESTS: testDatabaseConnection()
    // ==========================================

    @Test
    @DisplayName("SET-01: Simulación de conexión JDBC exitosa")
    void testDatabaseConnection_Success() throws SQLException {
        try (MockedStatic<DriverManager> driverManagerMock = mockStatic(DriverManager.class)) {
            //Creamos una conexión Mock
            Connection mockConnection = mock(Connection.class);

            driverManagerMock.when(() -> DriverManager.getConnection(
                            "jdbc:mysql://localhost:3306/?serverTimezone=UTC", "root", "pass"))
                    .thenReturn(mockConnection);

            // Comprobamos que no salta al llamar al método
            assertDoesNotThrow(() -> setupService.testDatabaseConnection(setupRequestDto));
        }
    }

    @Test
    @DisplayName("SET-02: Lanzamiento de excepción ante fallo de credenciales")
    void testDatabaseConnection_ThrowsSQLException_WhenCredentialsAreWrong() throws SQLException {
        try (MockedStatic<DriverManager> driverManagerMock = mockStatic(DriverManager.class)) {
            // Forzamos la conexión a devolver una excepción al conectarnos
            driverManagerMock.when(() -> DriverManager.getConnection(
                            "jdbc:mysql://localhost:3306/?serverTimezone=UTC", "root", "pass"))
                    .thenAnswer(invocation -> {
                        throw new SQLException("Access denied for user 'root'@'localhost'");
                    });

            // Comprobamos que salta al llamar al método
            assertThrows(SQLException.class, () -> setupService.testDatabaseConnection(setupRequestDto));
        }
    }

    // ==========================================
    // 2. TESTS: storeAdminAvatar()
    // ==========================================

    @Test
    @DisplayName("SET-03: Comprobar que se devuelve una cadena vacía si el admin no pone avatar")
    void storeAdminAvatar_ReturnsEmptyString_WhenAvatarIsNull() throws IOException {
        String path = setupService.storeAdminAvatar(tempDir.toString(), null);
        assertEquals("", path);
    }

    @Test
    @DisplayName("SET-04: Comprobar que se devuelve una cadena vacía si el admin pone un avatar sin información")
    void storeAdminAvatar_ReturnsEmptyString_WhenAvatarIsEmpty() throws IOException {
        MockMultipartFile emptyFile = new MockMultipartFile("avatar", "vacio.png", "image/png", new byte[0]);
        String path = setupService.storeAdminAvatar(tempDir.toString(), emptyFile);
        assertEquals("", path);
    }

    @Test
    @DisplayName("SET-05: Guardado del avatar del admin con inicialización de carpeta")
    void storeAdminAvatar_Success_SavesFileAndRespectsExtension() throws IOException {
        MockMultipartFile file = new MockMultipartFile("avatar", "admin.jpg", "image/jpeg", "admin-photo".getBytes());
        Path avatarsFolder = tempDir.resolve("avatars");

        assertFalse(Files.exists(avatarsFolder)); // No existe la carpeta

        String webPath = setupService.storeAdminAvatar(tempDir.toString(), file);

        assertNotNull(webPath);
        assertTrue(webPath.startsWith("/static/avatars/"));
        assertTrue(webPath.endsWith(".jpg")); // Se guardó el fichero
        assertTrue(Files.exists(avatarsFolder)); // La carpeta se creño
    }

    @Test
    @DisplayName("SET-06: Asignación por defecto .png si el archivo original no tiene extensión")
    void storeAdminAvatar_Success_DefaultsToPng_WhenNoExtension() throws IOException {
        MockMultipartFile fileWithoutExt = new MockMultipartFile("avatar", "admin", "image/png", "admin".getBytes());
        String webPath = setupService.storeAdminAvatar(tempDir.toString(), fileWithoutExt);

        assertTrue(webPath.endsWith(".png")); // Comprobamos que se asigna .png
    }

    // ==========================================
    // 3. TESTS: writeConfigurationProperties()
    // ==========================================

    @Test
    @DisplayName("SET-07: Escritura completa del archivo properties")
    void writeConfigurationProperties_Success_GeneratesValidPropertiesFile() throws IOException {
        Path mockConfigDir = tempDir.resolve("config");
        Path mockConfigFile = mockConfigDir.resolve("application.properties");

        try (MockedStatic<ConfigPathResolver> resolverMock = mockStatic(ConfigPathResolver.class)) {
            resolverMock.when(ConfigPathResolver::getConfigDir).thenReturn(mockConfigDir.toFile());
            resolverMock.when(ConfigPathResolver::getConfigFile).thenReturn(mockConfigFile.toFile());

            setupService.writeConfigurationProperties(setupRequestDto, "/static/avatars/admin.png");

            // Validamos que existe el fichero tras usar el método
            assertTrue(Files.exists(mockConfigFile));
            String fileContent = Files.readString(mockConfigFile);

            // Comprobamos algunas de las propiedades que deben existir en el fichero final
            assertTrue(fileContent.contains("spring.datasource.username=root"));
            assertTrue(fileContent.contains("app.storage.max-quota=1073741824"));
            assertTrue(fileContent.contains("app.crypto.hash-algorithm=SHA-256"));
            assertTrue(fileContent.contains("app.jwt.secret="));
            assertTrue(fileContent.contains("app.setup.admin-username=admin1"));
        }
    }

    @Test
    @DisplayName("SET-08: Captura de IOException si el sistema de archivos está bloqueado")
    void writeConfigurationProperties_ThrowsIOException_WhenFileSystemIsReadOnly() {
        Path mockConfigDir = tempDir.resolve("error");

        try (MockedStatic<ConfigPathResolver> resolverMock = mockStatic(ConfigPathResolver.class)) {
            // Hacemos que getConfigFile devuelva el directorio en vez de un archivo para provocar un fallo de E/S
            resolverMock.when(ConfigPathResolver::getConfigDir).thenReturn(mockConfigDir.toFile());
            resolverMock.when(ConfigPathResolver::getConfigFile).thenReturn(mockConfigDir.toFile());

            assertThrows(IOException.class, () -> {
                setupService.writeConfigurationProperties(setupRequestDto, "");
            });
        }
    }
}