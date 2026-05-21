package com.cloudcrypt.service.user;

import com.cloudcrypt.dto.user.KeyRequestDto;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.model.UserKeyEntity;
import com.cloudcrypt.repository.keys.UserKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserKeyServiceTest {

    @Mock
    private UserKeyRepository userKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserKeyService userKeyService;

    private UserEntity userEntity;
    private UserKeyEntity userKeyEntity;
    private KeyRequestDto keyRequestDto;

    @BeforeEach
    void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user1");
        userEntity.setFullName("Usuario 1");

        userKeyEntity = new UserKeyEntity();
        userKeyEntity.setUser(userEntity);
        userKeyEntity.setPublicKey("pubkey1");
        userKeyEntity.setEncryptedPrivateKey("encryptedprivkey1");

        keyRequestDto = new KeyRequestDto();
        keyRequestDto.setPublicKey("pubkey1");
        keyRequestDto.setEncryptedPrivateKey("encryptedprivkey1");
    }

    // ==========================================
    // 1. TESTS: registerKeys()
    // ==========================================

    @Test
    @DisplayName("KEY-01: Lanzar excepción al registrar claves para usuario inexistente")
    void registerKeys_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByUsername("user2")).thenReturn(null);

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.registerKeys("user2", keyRequestDto);
        });

        verify(userKeyRepository, never()).findById(any());
        verify(userKeyRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("KEY-02: Registro de un nuevo keyring si el usuario no existe")
    void registerKeys_Success_WhenUserKeyDoesNotExistYet() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.empty());
        when(userKeyRepository.saveAndFlush(any(UserKeyEntity.class))).thenReturn(userKeyEntity);

        userKeyService.registerKeys("user1", keyRequestDto);

        verify(userKeyRepository, times(1)).findById(1L);
        verify(userKeyRepository, times(1)).saveAndFlush(any(UserKeyEntity.class));
    }

    @Test
    @DisplayName("KEY-03: Actualización de keyring ya existente")
    void registerKeys_Success_WhenUserKeyAlreadyExists() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.of(userKeyEntity));
        when(userKeyRepository.saveAndFlush(userKeyEntity)).thenReturn(userKeyEntity);

        userKeyService.registerKeys("user1", keyRequestDto);

        verify(userKeyRepository, times(1)).findById(1L);
        verify(userKeyRepository, times(1)).saveAndFlush(userKeyEntity);
    }

    // ==========================================
    // 2. TESTS: getPublicInfo()
    // ==========================================

    @Test
    @DisplayName("KEY-04: Pedir clave pública de usuario inexistente")
    void getPublicInfo_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByUsername("user2")).thenReturn(null);

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.getPublicInfo("user2");
        });
    }

    @Test
    @DisplayName("KEY-05: Pedir clave pública de usuario sin keyring")
    void getPublicInfo_ThrowsException_WhenKeysDoNotExist() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.getPublicInfo("user1");
        });
    }

    @Test
    @DisplayName("KEY-06: Pedir clave pública existente")
    void getPublicInfo_Success_WhenKeysExist() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.of(userKeyEntity));

        Map<String, Object> response = userKeyService.getPublicInfo("user1");

        assertNotNull(response);
        assertTrue(response.containsKey("publicKey"));
        assertEquals("pubkey1", response.get("publicKey"));
    }

    // ==========================================
    // 3. TESTS: getEncryptedPrivateKey()
    // ==========================================

    @Test
    @DisplayName("KEY-07: Pedir clave privada de usuario inexistente")
    void getEncryptedPrivateKey_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByUsername("user2")).thenReturn(null);

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.getEncryptedPrivateKey("user2");
        });
    }

    @Test
    @DisplayName("KEY-08: Pedir clave privada de usuario sin keyring")
    void getEncryptedPrivateKey_ThrowsException_WhenKeysDoNotExist() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.getEncryptedPrivateKey("user1");
        });
    }

    @Test
    @DisplayName("KEY-09: Pedir clave privada cifrada existente")
    void getEncryptedPrivateKey_Success_WhenKeysExist() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.of(userKeyEntity));

        String privateKey = userKeyService.getEncryptedPrivateKey("user1");

        assertNotNull(privateKey);
        assertEquals("encryptedprivkey1", privateKey);
    }

    // ==========================================
    // 4. TESTS: updatePrivateKey()
    // ==========================================

    @Test
    @DisplayName("KEY-10: Actualizar clave privada de usuario inexistente")
    void updatePrivateKey_ThrowsException_WhenUserNotFound() {
        when(userRepository.findByUsername("user2")).thenReturn(null);

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.updatePrivateKey("user2", "encryptedprivkey2");
        });

        verify(userKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("KEY-11: Actualizar clave privada sin keyring")
    void updatePrivateKey_ThrowsException_WhenUserHasNoKeyPair() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(InstanceNotFoundException.class, () -> {
            userKeyService.updatePrivateKey("user1", "encryptedprivkey2");
        });

        verify(userKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("KEY-12: Actualización de clave privada exitosa")
    void updatePrivateKey_Success_WhenKeyPairExists() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(userKeyRepository.findById(1L)).thenReturn(Optional.of(userKeyEntity));
        when(userKeyRepository.save(userKeyEntity)).thenReturn(userKeyEntity);

        userKeyService.updatePrivateKey("user1", "encryptedprivkey2");

        assertEquals("encryptedprivkey2", userKeyEntity.getEncryptedPrivateKey());
        verify(userKeyRepository, times(1)).save(userKeyEntity);
    }
}