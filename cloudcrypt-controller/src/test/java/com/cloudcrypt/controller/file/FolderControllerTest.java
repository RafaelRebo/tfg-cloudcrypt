package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.service.file.FileWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FolderControllerTest {

    private MockMvc mockMvc;
    private FileWriteService fileWriteService;
    private Authentication mockAuth;
    private FileDto sampleFolderDto;

    @BeforeEach
    void setUp() {
        fileWriteService = Mockito.mock(FileWriteService.class);

        // Auth de user1
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        mockMvc = MockMvcBuilders.standaloneSetup(new FolderController(fileWriteService)).build();

        // Configuración de los metadatos de una carpeta
        sampleFolderDto = new FileDto();
        sampleFolderDto.setId(20L);
        sampleFolderDto.setFileName("Nueva Carpeta");
        sampleFolderDto.setOwnerUsername("user1");
        sampleFolderDto.setFileType("application/x-directory");
        sampleFolderDto.setFileSize(0L);
    }

    // ==========================================
    // 1. TEST: createFolder()
    // ==========================================

    @Test
    @DisplayName("FLD-01: Creación de un nuevo directorio")
    void createFolder_Success_WhenParametersAreValid() throws Exception {
        when(fileWriteService.createFolder(eq("Nueva Carpeta"), eq("user1"), eq(5L)))
                .thenReturn(sampleFolderDto);

        // Intentamos crear una nueva carpeta con ID 20 dentro de una existente con ID 5
        // Verificamos que se crea con su ID, nombre y MIME type correctos
        mockMvc.perform(post("/api/files/folder")
                        .param("folderName", "Nueva Carpeta")
                        .param("parentId", "5")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20L))
                .andExpect(jsonPath("$.fileName").value("Nueva Carpeta"))
                .andExpect(jsonPath("$.fileType").value("application/x-directory"));

        // Verificamos que se ha llamado al servicio correspondiente
        verify(fileWriteService, times(1)).createFolder(eq("Nueva Carpeta"), eq("user1"), eq(5L));
    }

    // ==========================================
    // 2. TEST: createFolderSync()
    // ==========================================

    @Test
    @DisplayName("FLD-02: Sincronización de estructura de carpetas")
    void createFolderSync_Success_WhenSynchronizingStructure() throws Exception {
        when(fileWriteService.ensureFolderSync(eq("user1"), eq("Carpeta Sincronizada"), eq(5L)))
                .thenReturn(sampleFolderDto);

        // Aseguramos que la carpeta de ID 20 dentro de la de ID 5 viene en la respuesta correcta del endpoint
        mockMvc.perform(post("/api/files/folder/sync")
                        .param("folderName", "Carpeta Sincronizada")
                        .param("parentId", "5")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(20L));

        // Verificamos que se ha llamado correctamente al servicio
        verify(fileWriteService, times(1)).ensureFolderSync(eq("user1"), eq("Carpeta Sincronizada"), eq(5L));
    }
}