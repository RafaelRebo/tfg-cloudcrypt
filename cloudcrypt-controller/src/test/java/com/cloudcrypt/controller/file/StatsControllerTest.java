package com.cloudcrypt.controller.file;

import com.cloudcrypt.service.file.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsControllerTest {

    private MockMvc mockMvc;
    private StatsService statsService;
    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        statsService = Mockito.mock(StatsService.class);

        // Auth de user1
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        mockMvc = MockMvcBuilders.standaloneSetup(new StatsController(statsService)).build();
    }

    // ==========================================
    // 1. TEST: getUserStats()
    // ==========================================

    @Test
    @DisplayName("STS-01: Recuperación correcta de las estadísticas de almacenamiento del usuario")
    void getUserStats_Success_WhenUserIsAuthenticated() throws Exception {
        // Objeto simulado devuelto por el servicio
        Map<String, Object> statsMock = Map.of(
                "totalSize", 10485760L,       // 10 MB utilizados
                "fileCount", 15L,              // 15 archivos
                "maxQuota", 10737418240L,      // 10 GB de cuota máxima
                "usagePercentage", 0.1         // 0.1% de progreso de uso
        );
        when(statsService.getUserStats("user1")).thenReturn(statsMock);

        // Ejecutamos la consulta y comprobamos que vienen los datos esperados
        mockMvc.perform(get("/api/files/stats")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSize").value(10485760L))
                .andExpect(jsonPath("$.fileCount").value(15))
                .andExpect(jsonPath("$.maxQuota").value(10737418240L))
                .andExpect(jsonPath("$.usagePercentage").value(0.1));

        // Comprobamos que se llamó al servicio correspondiente
        verify(statsService, times(1)).getUserStats("user1");
    }
}