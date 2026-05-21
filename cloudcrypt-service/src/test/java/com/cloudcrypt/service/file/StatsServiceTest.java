package com.cloudcrypt.service.file;

import com.cloudcrypt.config.StorageConfig;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StatsServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private UserRepository userRepository;

    private StorageConfig storageConfig;

    private StatsService statsService;
    private UserEntity userEntity;

    @BeforeEach
    void setUp() {
        storageConfig = new StorageConfig();
        storageConfig.setMaxQuota(2000L); // Cuota global
        storageConfig.setUploadDir("uploads");

        statsService = new StatsService(fileRepository, storageConfig, userRepository);

        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user1");
        userEntity.setQuotaBytes(1000L); // Cuota 1000 bytes
    }

    // ==========================================
    // TESTS PARA: getUserStats()
    // ==========================================

    @Test
    @DisplayName("STS-01: Cálculo de estadísticas usando la cuota asignada al usuario")
    void getUserStats_Success_WithUserCustomQuota() {
        when(fileRepository.getTotalUsageByUser("user1")).thenReturn(250L);
        when(fileRepository.countFilesByUser("user1")).thenReturn(5L);
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);

        Map<String, Object> stats = statsService.getUserStats("user1");

        // Comprobamos que las estadísticas devueltas coinciden con las esperadas
        assertNotNull(stats);
        assertEquals(250L, stats.get("totalSize"));
        assertEquals(5L, stats.get("fileCount"));
        assertEquals(1000L, stats.get("maxQuota"));
        assertEquals(25.0, stats.get("usagePercentage"));
    }

    @Test
    @DisplayName("STS-02: Se usa cuota global si el usuario no existe")
    void getUserStats_FallbackToGlobalQuota_WhenUserNotFound() {
        when(fileRepository.getTotalUsageByUser("user3")).thenReturn(100L);
        when(fileRepository.countFilesByUser("user3")).thenReturn(2L);
        when(userRepository.findByUsername("user3")).thenReturn(null);

        storageConfig.setMaxQuota(2000L);

        Map<String, Object> stats = statsService.getUserStats("user3");

        assertNotNull(stats);
        assertEquals(2000L, stats.get("maxQuota")); // Se usan los 2000 globales ya que user3 no existe
        assertEquals(5.0, stats.get("usagePercentage"));
    }

    @Test
    @DisplayName("STS-03: Se usa cuota global si el usuario existe y no tiene cuota propia asignada")
    void getUserStats_FallbackToGlobalQuota_WhenUserQuotaIsNull() {
        userEntity.setQuotaBytes(null); // El usuario no tiene cuota propia

        when(fileRepository.getTotalUsageByUser("user1")).thenReturn(300L);
        when(fileRepository.countFilesByUser("user1")).thenReturn(3L);
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);

        storageConfig.setMaxQuota(3000L);

        Map<String, Object> stats = statsService.getUserStats("user1");

        assertNotNull(stats);
        assertEquals(3000L, stats.get("maxQuota")); // Se usan los 3000 globales ya que user3 no tiene cuota propia
        assertEquals(10.0, stats.get("usagePercentage"));
    }

    @Test
    @DisplayName("STS-04: Control de división por cero")
    void getUserStats_ReturnsZeroPercentage_WhenQuotaIsZero() {
        userEntity.setQuotaBytes(0L); // Forzamos cuota a cero

        when(fileRepository.getTotalUsageByUser("user1")).thenReturn(50L);
        when(fileRepository.countFilesByUser("user1")).thenReturn(1L);
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);

        Map<String, Object> stats = statsService.getUserStats("user1");

        assertNotNull(stats);
        assertEquals(0L, stats.get("maxQuota")); // Se coloca a 0 directamente para no dar error
        assertEquals(0.0, stats.get("usagePercentage"));
    }
}