package com.cloudcrypt.controller.admin;

import com.cloudcrypt.dto.admin.AdminStatsDto;
import com.cloudcrypt.service.admin.AdminService;
import com.cloudcrypt.service.user.UserDeleteService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest {

    private MockMvc mockMvc;
    private AdminService adminService;
    private UserDeleteService userDeleteService;
    private Authentication mockAuth;
    private AdminStatsDto adminStatsDto;

    @BeforeEach
    void setUp() {
        adminService = Mockito.mock(AdminService.class);
        userDeleteService = Mockito.mock(UserDeleteService.class);

        // Auth del administrador
        mockAuth = Mockito.mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("user1");

        mockMvc = MockMvcBuilders.standaloneSetup(new AdminController(adminService, userDeleteService)).build();

        // Inicialización del DTO de admin
        adminStatsDto = new AdminStatsDto();
        adminStatsDto.setGlobalUsedBytes(5368709120L); // 5 GB de consumo global
    }

    // ==========================================
    // 1. TEST: getSystemVolumeStats()
    // ==========================================

    @Test
    @DisplayName("ADM-01: Recuperación correcta de las métricas de almacenamiento global")
    void getSystemVolumeStats_Success_WhenUserIsAdmin() throws Exception {
        when(adminService.getSystemVolumeStats()).thenReturn(adminStatsDto);

        // Llamamos al endpoint y verificamos que se devuelven los 5GB de uso que establecimos en el setup
        mockMvc.perform(get("/api/admin/stats")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalUsedBytes").value(5368709120L));

        verify(adminService, times(1)).getSystemVolumeStats(); // Se llama al servicio correspondiente para hacer la consulta
    }

    // ==========================================
    // 2. TEST: updateUserParameters()
    // ==========================================

    @Test
    @DisplayName("ADM-02: Modificación de los límites de cuota y asignación de rol a un usuario")
    void updateUserParameters_Success_WhenParametersAreValid() throws Exception {
        Long targetUserId = 2L; // Usuario de ID 2
        Long newQuotaBytes = 10737418240L; // Le asignamos 10 GB
        String newRole = "ADMIN"; //Le asignamos admin

        doNothing().when(adminService).updateUserParameters(eq(targetUserId), eq(newQuotaBytes), eq(newRole), eq("user1"));

        // Hacemos la petición para cambiar los parámetros del usuario a los de arriba y verificamos que se devuelve un OK
        mockMvc.perform(post("/api/admin/users/{id}/manage", targetUserId)
                        .param("quotaBytes", newQuotaBytes.toString())
                        .param("role", newRole)
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se ha llamado al servicio correspondiente
        verify(adminService, times(1)).updateUserParameters(eq(targetUserId), eq(newQuotaBytes), eq(newRole), eq("user1"));
    }

    // ==========================================
    // 3. TEST: deleteUserAndData()
    // ==========================================

    @Test
    @DisplayName("ADM-03: Eliminación de un usuario y sus ficheros")
    void deleteUserAndData_Success_WhenUserExists() throws Exception {
        Long targetUserId = 2L;
        doNothing().when(userDeleteService).purgeUserFully(targetUserId);

        // Verificamos que la petición no devuelve errores
        mockMvc.perform(delete("/api/admin/users/{id}", targetUserId)
                        .principal(mockAuth))
                .andExpect(status().isOk());

        // Verificamos que se ha llamado al servicio correspondiente
        verify(userDeleteService, times(1)).purgeUserFully(targetUserId);
    }
}