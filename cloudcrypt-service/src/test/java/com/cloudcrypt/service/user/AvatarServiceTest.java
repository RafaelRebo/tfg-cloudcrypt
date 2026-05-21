package com.cloudcrypt.service.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AvatarServiceTest {

    @InjectMocks
    private AvatarService avatarService;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(avatarService, "uploadDir", tempDir.toString());
    }

    // ==========================================
    // TESTS DE VALIDACIÓN INICIAL
    // ==========================================

    @Test
    @DisplayName("AVA-01: Se retorna nulo si se pasa un archivo nulo")
    void storeAvatar_ReturnsNull_WhenFileIsNull() {
        String result = avatarService.storeAvatar(null);
        assertNull(result);
    }

    @Test
    @DisplayName("AVA-02: Se retorna nulo si se pasa un archivo vacío")
    void storeAvatar_ReturnsNull_WhenFileIsEmpty() {
        // Explicación: Simulamos un archivo con bytes vacíos. Debe retornar nulo.
        MockMultipartFile emptyFile = new MockMultipartFile("avatar", "vacio.png", "image/png", new byte[0]);

        String result = avatarService.storeAvatar(emptyFile);
        assertNull(result);
    }

    // ==========================================
    // TESTS DE ALMACENAMIENTO
    // ==========================================

    @Test
    @DisplayName("AVA-03: Guardado de avatar y creación del directorio si no existía")
    void storeAvatar_Success_CreatesDirectoryAndSavesFile() throws IOException {
        MockMultipartFile validFile = new MockMultipartFile("avatar", "foto_perfil.jpg", "image/jpeg", "bytes_de_imagen_simulados".getBytes());
        Path avatarsFolder = tempDir.resolve("avatars");

        // La carpeta no existe antes de ejecutar el método
        assertFalse(Files.exists(avatarsFolder));

        String webPath = avatarService.storeAvatar(validFile);

        assertNotNull(webPath);
        assertTrue(webPath.startsWith("/static/avatars/"));
        assertTrue(webPath.endsWith(".jpg")); // La imagen se guardó
        assertTrue(Files.exists(avatarsFolder)); // La carpeta se creó dinámicamente
    }

    @Test
    @DisplayName("AVA-04: Asignación por defecto .png si el archivo original no tiene extensión")
    void storeAvatar_Success_DefaultsToPng_WhenNoExtensionFound() {
        MockMultipartFile fileWithoutExt = new MockMultipartFile("avatar", "image", "image/png", "datos".getBytes());

        String webPath = avatarService.storeAvatar(fileWithoutExt);

        assertNotNull(webPath);
        assertTrue(webPath.endsWith(".png"));
    }

    @Test
    @DisplayName("AVA-05: Guardado correcto si directorio ya existe")
    void storeAvatar_Success_WhenDirectoryAlreadyExists() throws IOException {
        Path avatarsFolder = tempDir.resolve("avatars");
        Files.createDirectories(avatarsFolder); // Creamos la carpeta previamente

        MockMultipartFile validFile = new MockMultipartFile("avatar", "avatar.png", "image/png", "datos".getBytes());

        String webPath = avatarService.storeAvatar(validFile);

        assertNotNull(webPath); // El fichero se guarda
        assertTrue(Files.exists(avatarsFolder));
    }
}