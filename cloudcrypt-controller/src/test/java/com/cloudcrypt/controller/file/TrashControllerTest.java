package com.cloudcrypt.controller.file;

import com.cloudcrypt.service.file.TrashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrashControllerTest {

    private MockMvc mockMvc;
    private TrashService trashService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        trashService = Mockito.mock(TrashService.class);

        // Auth de user1
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        mockMvc = MockMvcBuilders.standaloneSetup(new TrashController(trashService)).build();
    }

    // ==========================================
    // 1. TESTS: deleteFile()
    // ==========================================

    @Test
    @DisplayName("TRH-01: Borrado de un recurso enviándolo a la papelera de reciclaje")
    void deleteFile_Success_WhenSoftDeleting() throws Exception {
        Long targetId = 10L;
        doNothing().when(trashService).deleteFile(eq(targetId), eq("user1"), eq(false)); // Mandamos el flag forcePermanent a false porque es borrado lógico (a papelera)

        // Hacemos la petición delete
        mockMvc.perform(delete("/api/files/{id}", targetId)
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Comprobamos que se invocó al servicio con el flag a false
        verify(trashService, times(1)).deleteFile(eq(targetId), eq("user1"), eq(false));
    }

    @Test
    @DisplayName("TRH-02: Borrado físico y permanente de un recurso")
    void deleteFile_Success_WhenPermanentDeleting() throws Exception {
        Long targetId = 10L;
        doNothing().when(trashService).deleteFile(eq(targetId), eq("user1"), eq(true)); // Mandamos el flag forcePermanent a true porque es borrado permanente

        // Invocamos al endpoint poniendo el flag explícitamente a true
        mockMvc.perform(delete("/api/files/{id}", targetId)
                        .param("permanent", "true")
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se invocó al servicio usando dicho flag
        verify(trashService, times(1)).deleteFile(eq(targetId), eq("user1"), eq(true));
    }

    // ==========================================
    // 2. TEST: restoreFile()
    // ==========================================

    @Test
    @DisplayName("TRH-03: Restauración exitosa de un archivo a su ubicación original")
    void restoreFile_Success_WhenFileIdIsValid() throws Exception {
        Long targetId = 10L;
        doNothing().when(trashService).restoreFile(eq(targetId), eq("user1"));

        // Invocamos el endpoint de restablecer sobre el ID de fichero deseado
        mockMvc.perform(post("/api/files/{id}/restore", targetId)
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se invocó al servicio correspondiente
        verify(trashService, times(1)).restoreFile(eq(targetId), eq("user1"));
    }
}