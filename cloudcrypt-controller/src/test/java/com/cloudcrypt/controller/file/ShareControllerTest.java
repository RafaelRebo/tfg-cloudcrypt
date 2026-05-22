package com.cloudcrypt.controller.file;

import com.cloudcrypt.dto.file.ShareRequestDto;
import com.cloudcrypt.service.file.FileQueryService;
import com.cloudcrypt.service.file.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareControllerTest {

    private MockMvc mockMvc;
    private ShareService shareService;
    private FileQueryService fileQueryService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        shareService = Mockito.mock(ShareService.class);
        fileQueryService = Mockito.mock(FileQueryService.class);

        // Auth de user1
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(shareService, fileQueryService)).build();
    }

    // ==========================================
    // 1. TEST: shareFile()
    // ==========================================

    @Test
    @DisplayName("SHR-01: Modificación de los accesos compartidos de un archivo")
    void shareFile_Success_WhenPayloadIsValid() throws Exception {
        doNothing().when(shareService).shareFile(eq(10L), anyList(), eq("user1"));

        // Payload de la petición de acceso compartido (target y clave simétrica cifrada con la pública de user2)
        String jsonPayload = "[{\"targetUsername\":\"user2\",\"encryptedKey\":\"enckey123\"}]";

        // Verificamos que se nos devuelva un OK al compartir el fichero de ID 10
        mockMvc.perform(post("/api/files/{id}/share", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se llamase al método correspondiente
        verify(shareService, times(1)).shareFile(eq(10L), anyList(), eq("user1"));
    }

    // ==========================================
    // 2. TEST: shareFilesBatch()
    // ==========================================

    @Test
    @DisplayName("SHR-02: Gestión compartida de archivos para múltiples usuarios")
    void shareFilesBatch_Success_WhenBatchPayloadIsValid() throws Exception {
        doNothing().when(shareService).shareBatch(anyList(), eq("user1"));

        // Payload de la petición de acceso compartido (target y clave simétrica cifrada con la pública de user2)
        String jsonPayload = "[{\"targetUsername\":\"user2\",\"encryptedKey\":\"enckey123\"}]";

        // Llamamos al endpoint para compartir en bloque
        mockMvc.perform(post("/api/files/share/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se llama al servicio correspondiente
        verify(shareService, times(1)).shareBatch(anyList(), eq("user1"));
    }

    // ==========================================
    // 3. TEST: getFileKey()
    // ==========================================

    @Test
    @DisplayName("SHR-03: Recuperación de la clave simétrica cifrada asociada a un recurso")
    void getFileKey_Success_WhenUserHasAccess() throws Exception {
        when(fileQueryService.getEncryptedFileKey(10L, "user1")).thenReturn("encfilekey123");

        // Comprobamos que podemos recuperar la clave simétrica cifrada de un fichero con ID 10
        // Si estamos autorizados para ver el fichero, la podremos descifrar con nuestra clave pública y descifrar el fichero
        mockMvc.perform(get("/api/files/{id}/key", 10L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encryptedFileKey").value("encfilekey123"));
    }

    // ==========================================
    // 4. TEST: getFileKeysBatch()
    // ==========================================

    @Test
    @DisplayName("SHR-04: Descarga de claves simétricas cifradas")
    void getFileKeysBatch_Success_WhenRequestingMultipleIds() throws Exception {
        Map<Long, String> keysMock = Map.of(10L, "key10", 11L, "key11");
        when(fileQueryService.getEncryptedFileKeysBatch(anyList(), eq("user1"))).thenReturn(keysMock);

        // Envío del array de IDs de archivos de los que queremos las claves
        String jsonPayload = "[10, 11]";

        // Verificamos que para cada archivo pedido se nos envía su clave
        mockMvc.perform(post("/api/files/keys/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['10']").value("key10"))
                .andExpect(jsonPath("$.['11']").value("key11"));
    }

    // ==========================================
    // 5. TEST: getSharedUsers()
    // ==========================================

    @Test
    @DisplayName("SHR-05: Listado de nombres de usuario con autorización sobre el recurso")
    void getSharedUsers_Success_WhenFileIsShared() throws Exception {
        List<String> sharedUsernames = List.of("user2", "user3");
        when(shareService.getSharedUsernames(10L, "user1")).thenReturn(sharedUsernames);

        // Verificamos que se devuelve correctamente la lista de usuarios que tienen acceso como compartidos al fichero
        mockMvc.perform(get("/api/files/{id}/shared-users", 10L)
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("user2"))
                .andExpect(jsonPath("$[1]").value("user3"));
    }

    // ==========================================
    // 6. TEST: revokeAccess()
    // ==========================================

    @Test
    @DisplayName("SHR-06: Revocación de permisos de acceso compartido para un usuario")
    void revokeAccess_Success_WhenRevokingTarget() throws Exception {
        doNothing().when(shareService).revokeAccess(10L, "user2", "user1");

        // Verificamos que, autenticados como user1, poseedor del fichero de ID 10, podemos quitar permisos correctamente a user2
        mockMvc.perform(delete("/api/files/{id}/share/revoke", 10L)
                        .param("target", "user2")
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se ha llamado al servicio correspondiente
        verify(shareService, times(1)).revokeAccess(10L, "user2", "user1");
    }
}