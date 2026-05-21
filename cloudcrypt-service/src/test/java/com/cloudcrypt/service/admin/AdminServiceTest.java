package com.cloudcrypt.service.admin;

import com.cloudcrypt.dto.admin.AdminStatsDto;
import com.cloudcrypt.dto.admin.UserDiskMetricDto;
import com.cloudcrypt.exceptions.InputValidationException;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private AdminService adminService;

    private UserEntity user1;
    private UserEntity user2;

    @BeforeEach
    void setUp() {
        user1 = new UserEntity();
        user1.setId(1L);
        user1.setUsername("user1");
        user1.setFullName("Usuario 1");
        user1.setQuotaBytes(1000L);
        user1.setRole("USER");
        user1.setAvatarUrl("/avatars/user1.png");

        user2 = new UserEntity();
        user2.setId(2L);
        user2.setUsername("admin1");
        user2.setFullName("Administrador 1");
        user2.setQuotaBytes(5000L);
        user2.setRole("ADMIN");
        user2.setAvatarUrl("/avatars/admin1.png");
    }

    // ==========================================
    // 1. TESTS: getSystemVolumeStats()
    // ==========================================

    @Test
    @DisplayName("ADM-01: Métricas vacías si no existen usuarios")
    void getSystemVolumeStats_ReturnsZero_WhenNoUsersExist() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        AdminStatsDto stats = adminService.getSystemVolumeStats();

        // Comprobamos que las estadísticas existen, pero no contienen usuarios ni información de uso
        assertNotNull(stats);
        assertEquals(0, stats.getGlobalUsedBytes());
        assertTrue(stats.getUsers().isEmpty());
        verify(fileRepository, never()).getTotalUsageByUser(anyString());
    }

    @Test
    @DisplayName("ADM-02: Cálculo correcto del uso global de disco")
    void getSystemVolumeStats_Success_CalculatesGlobalMetrics() {
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        // Simulamos que el usuario user1 tiene 3 ficheros que ocupan 300 bytes y el usuario admin1 7 ficheros que ocupan 700 bytes
        when(fileRepository.getTotalUsageByUser("user1")).thenReturn(300L);
        when(fileRepository.countFilesByUser("user1")).thenReturn(3L);

        when(fileRepository.getTotalUsageByUser("admin1")).thenReturn(700L);
        when(fileRepository.countFilesByUser("admin1")).thenReturn(7L);

        AdminStatsDto stats = adminService.getSystemVolumeStats();

        // Comprobamos que en total hay 2 usuarios y 1000 bytes ocupados
        assertNotNull(stats);
        assertEquals(1000L, stats.getGlobalUsedBytes());
        assertEquals(2, stats.getUsers().size());

        // Verificamos que el primer usuario tiene las propiedades esperadas
        UserDiskMetricDto metric1 = stats.getUsers().get(0);
        assertEquals("user1", metric1.getUsername());
        assertEquals(3L, metric1.getFileCount());
        assertEquals(300L, metric1.getUsedBytes());
        assertEquals(1000L, metric1.getQuotaBytes());
    }

    // ==========================================
    // 2. TESTS: updateUserParameters()
    // ==========================================

    @Test
    @DisplayName("ADM-03: Lanzar excepción si se intenta modificar un usuario inexistente")
    void updateUserParameters_ThrowsException_WhenUserNotFound() {
        // Explicación: Control perimetral. Si el ID no existe en la BD, corta el flujo con InstanceNotFoundException.
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(InstanceNotFoundException.class, () -> {
            adminService.updateUserParameters(99L, 2000L, "ADMIN", "admin1");
        });

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADM-04: Verificación de que el admin no puede cambiar sus propios privilegios")
    void updateUserParameters_ThrowsException_WhenAdminTriesToModifyThemselves() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));

        // Intentamos cambiar los parámetros del usuario administrador
        assertThrows(InputValidationException.class, () -> {
            adminService.updateUserParameters(2L, 9999L, "USER", "ADMIN1");
        });

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("ADM-05: Modificación de cuota y rol de un usuario")
    void updateUserParameters_Success_UpdatesDataAndConvertsRoleToUppercase() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.save(user1)).thenReturn(user1);

        adminService.updateUserParameters(1L, 4000L, "admin", "admin1");

        // Aseguramos que los parámetros del usuario 1 cambiaron
        assertEquals(4000L, user1.getQuotaBytes());
        assertEquals("ADMIN", user1.getRole());
        verify(userRepository, times(1)).save(user1);
    }
}