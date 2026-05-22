package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.dto.file.FileUploadRequestDto;
import com.cloudcrypt.service.file.FileWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileWriteControllerTest {

    private MockMvc mockMvc;
    private FileWriteService fileWriteService;
    private Authentication mockAuth;
    private FileDto sampleFileDto;

    @BeforeEach
    void setUp() {
        fileWriteService = Mockito.mock(FileWriteService.class);

        // Auth de user1
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        mockMvc = MockMvcBuilders.standaloneSetup(new FileWriteController(fileWriteService)).build();

        // Inicialización del objeto DTO del fichero
        sampleFileDto = new FileDto();
        sampleFileDto.setId(10L);
        sampleFileDto.setFileName("documento.enc");
        sampleFileDto.setOwnerUsername("user1");
        sampleFileDto.setFileSize(2048L);
        sampleFileDto.setFileType("application/octet-stream");
    }

    // ==========================================
    // 1. TEST: uploadFile()
    // ==========================================

    @Test
    @DisplayName("FLW-01: Subida exitosa de un archivo")
    void uploadFile_Success_WhenMultipartDataIsValid() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "documento.enc", MediaType.APPLICATION_OCTET_STREAM_VALUE, "data".getBytes()
        );

        when(fileWriteService.uploadFile(any(FileUploadRequestDto.class), eq("user1")))
                .thenReturn(sampleFileDto);

        // Ejecutamos la petición de subida del fichero, la respuesta esperada debe ser un JSON con la ID y el nombre
        mockMvc.perform(multipart("/api/files/upload")
                        .file(mockFile)
                        .param("fileName", "documento.enc")
                        .param("parentId", "5")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.fileName").value("documento.enc"));

        // Verificamos que se haya llamado al método correspondiente del servicio
        verify(fileWriteService, times(1)).uploadFile(any(FileUploadRequestDto.class), eq("user1"));
    }

    // ==========================================
    // 2. TEST: moveFiles()
    // ==========================================

    @Test
    @DisplayName("FLW-02: Traslado de ficheros hacia un directorio destino")
    void moveFiles_Success_WhenParametersAreValid() throws Exception {
        List<Long> fileIds = List.of(10L, 11L);
        doNothing().when(fileWriteService).moveFiles(eq(fileIds), eq(5L), eq("user1"));

        // Indicamos que queremos mover los ficheros con IDs 10 y 11 a la carpeta de ID 5
        mockMvc.perform(post("/api/files/move")
                        .param("fileIds", "10", "11")
                        .param("targetParentId", "5")
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se ejecutó correctamente la petición y que se llamó al servicio correspondiente
        verify(fileWriteService, times(1)).moveFiles(eq(fileIds), eq(5L), eq("user1"));
    }

    // ==========================================
    // 3. TEST: toggleStar()
    // ==========================================

    @Test
    @DisplayName("FLW-03: Alternar el estado destacado de un recurso")
    void toggleStar_Success_WhenFileIdExists() throws Exception {
        when(fileWriteService.toggleStar(10L, "user1")).thenReturn(sampleFileDto);

        // Comprobamos que al llamar al método se devuelve OK y el documento
        mockMvc.perform(post("/api/files/{id}/star", 10L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L))
                .andExpect(jsonPath("$.fileName").value("documento.enc"));
    }

    // ==========================================
    // 4. TEST: renameFile()
    // ==========================================

    @Test
    @DisplayName("FLW-04: Modificación del nombre asignado a un archivo o carpeta")
    void renameFile_Success_WhenNewNameIsProvided() throws Exception {
        // Como el servicio no es void, programamos el retorno del DTO de control
        when(fileWriteService.renameFile(10L, "nuevo.enc", "user1")).thenReturn(sampleFileDto);

        // Tratamos de renombrar el fichero pasándole el nuevo nombre por parámetro
        mockMvc.perform(post("/api/files/{id}/rename", 10L)
                        .param("name", "nuevo.enc")
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se ha invocado correctamente el servicio de escritura
        verify(fileWriteService, times(1)).renameFile(10L, "nuevo.enc", "user1");
    }

    // ==========================================
    // 5. TEST: copyFiles()
    // ==========================================

    @Test
    @DisplayName("FLW-05: Copia de ficheros")
    void copyFiles_Success_WhenLiftingMetadata() throws Exception {
        List<Long> fileIds = List.of(10L);
        doNothing().when(fileWriteService).copyFiles(eq(fileIds), eq(5L), eq("copia.enc"), eq("user1"));

        // Verificamos que podemos hacer una copia del fichero de ID 10 en la misma carpeta, llamándole copia.enc en este caso
        mockMvc.perform(post("/api/files/copy")
                        .param("fileIds", "10")
                        .param("targetParentId", "5")
                        .param("newName", "copia.enc")
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se llamó al servicio correspondiente
        verify(fileWriteService, times(1)).copyFiles(eq(fileIds), eq(5L), eq("copia.enc"), eq("user1"));
    }
}