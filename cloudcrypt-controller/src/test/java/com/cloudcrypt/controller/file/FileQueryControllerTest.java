package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.service.file.FileQueryService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileQueryControllerTest {

    private MockMvc mockMvc;
    private FileQueryService fileQueryService;
    private Authentication mockAuth;
    private FileDto sampleFileDto;

    @JsonIgnoreProperties({"pageable", "sort"})
    private interface PageMixin {}

    @BeforeEach
    void setUp() {
        fileQueryService = Mockito.mock(FileQueryService.class);

        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        // Personalización de Jackson para evitar excepciones con los objetos Page
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.addMixIn(Page.class, PageMixin.class);
        objectMapper.addMixIn(PageImpl.class, PageMixin.class);

        mockMvc = MockMvcBuilders.standaloneSetup(new FileQueryController(fileQueryService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new org.springframework.http.converter.ResourceHttpMessageConverter()
                )
                .build();

        // Inicialización del DTO estándar del fichero
        sampleFileDto = new FileDto();
        sampleFileDto.setId(10L);
        sampleFileDto.setFileName("documento.enc");
        sampleFileDto.setOwnerUsername("user1");
        sampleFileDto.setFileSize(2048L);
        sampleFileDto.setFileType("application/octet-stream");
    }

    // ==========================================
    // 1. TEST: listFiles()
    // ==========================================

    @Test
    @DisplayName("FLQ-01: Listado correcto de los ficheros de un directorio")
    void listFiles_Success_WhenParametersAreValid() throws Exception {
        // Convertimos la lista de ficheros en una page
        Page<FileDto> filePage = new PageImpl<>(Collections.singletonList(sampleFileDto));

        when(fileQueryService.getFilesByFolder(eq("user1"), eq(5L), eq("document"), any(Pageable.class)))
                .thenReturn(filePage);

        // Al pedir los ficheros de la carpeta con ID 5 y categoría document, deberíamos recibir el fichero document creado al principio
        // Se halla dentro de content porque se devuelve una Page
        mockMvc.perform(get("/api/files")
                        .param("folderId", "5")
                        .param("category", "document")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10L))
                .andExpect(jsonPath("$.content[0].fileName").value("documento.enc"));

        // Verificamos que se llamó al servicio correspondiente
        verify(fileQueryService, times(1)).getFilesByFolder(eq("user1"), eq(5L), eq("document"), any(Pageable.class));
    }

    // ==========================================
    // 2. TEST: searchFiles()
    // ==========================================

    @Test
    @DisplayName("FLQ-02: Búsqueda de ficheros")
    void searchFiles_Success_WhenQueryIsProvided() throws Exception {
        Page<FileDto> filePage = new PageImpl<>(Collections.singletonList(sampleFileDto));

        when(fileQueryService.searchFiles(eq("user1"), eq("documento"), any(Pageable.class)))
                .thenReturn(filePage);

        // Buscamos la query "documento" y verificamos que se nos devuelve una page con el fichero dentro
        mockMvc.perform(get("/api/files/search")
                        .param("q", "documento")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fileName").value("documento.enc"));
    }

    // ==========================================
    // 3. TEST: downloadFile()
    // ==========================================

    @Test
    @DisplayName("FLQ-03: Descarga de un fichero")
    void downloadFile_Success_ReturnsBinaryStreamAndHeaders() throws Exception {
        byte[] rawContent = "data".getBytes();
        InputStream mockStream = new ByteArrayInputStream(rawContent);

        when(fileQueryService.getFileById(10L, "user1")).thenReturn(sampleFileDto);
        when(fileQueryService.getFileDownloadStream(10L, "user1")).thenReturn(mockStream);

        // Verificamos que al descargar el fichero con ID 10 recibimos "documento.enc" y que su contenido es el que introducimos al principio
        mockMvc.perform(get("/api/files/download/{id}", 10L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"documento.enc\""))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(rawContent));
    }

    // ==========================================
    // 4. TEST: checkFileExists()
    // ==========================================

    @Test
    @DisplayName("FLQ-04: Verificación de duplicidad de nombre de archivo en un directorio específico")
    void checkFileExists_Success_ReturnsVerificationMap() throws Exception {
        Map<String, Object> responseMock = Map.of("exists", true);
        when(fileQueryService.checkExistsById("user1", "documento.enc", 5L)).thenReturn(responseMock);

        // Verificamos si en la carpeta de ID 5 existe documento.enc
        mockMvc.perform(get("/api/files/check-exists")
                        .param("fileName", "documento.enc")
                        .param("parentId", "5")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true)); // Debería devolver que existe
    }

    // ==========================================
    // 5. TEST: getRecursiveContent()
    // ==========================================

    @Test
    @DisplayName("FLQ-05: Recuperación jerárquica y recursiva del contenido de una carpeta")
    void getRecursiveContent_Success_WhenFolderIdIsValid() throws Exception {
        when(fileQueryService.getRecursiveFilesForSharing(5L, "user1"))
                .thenReturn(Collections.singletonList(sampleFileDto));

        // Deberíamos recuperar el fichero de ID 10 que está dentro de la carpeta de ID 5
        mockMvc.perform(get("/api/files/folder-content-recursive/{id}", 5L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10L)); // Verificamos que sea así
    }
}