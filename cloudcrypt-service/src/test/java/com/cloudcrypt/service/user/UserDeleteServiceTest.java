package com.cloudcrypt.service.user;

import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.UserKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.StorageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserDeleteServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private FileRepository fileRepository;
    @Mock private UserKeyRepository userKeyRepository;
    @Mock private StorageUtils storageUtils;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private UserDeleteService userDeleteService;

    @TempDir Path tempDir;

    private UserEntity userEntity;
    private FileEntity file1;
    private FileEntity file2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userDeleteService, "uploadDir", tempDir.toString());

        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user1");
        userEntity.setAvatarUrl("/static/avatars/avatar1.png");

        file1 = new FileEntity();
        file1.setId(101L);
        file1.setStoragePath("uploads/user1/file1.enc");

        file2 = new FileEntity();
        file2.setId(102L);
        file2.setStoragePath("uploads/user1/file2.enc");

        lenient().doAnswer(invocation -> {
            Consumer<Object> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    // ==========================================
    // TESTS BORRAR USUARIO
    // ==========================================

    @Test
    @DisplayName("DEL-01: Abortar si el usuario a borrar no existe")
    void purgeUserFully_ThrowsException_WhenUserDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(InstanceNotFoundException.class, () -> {
            userDeleteService.purgeUserFully(99L);
        });

        verify(fileRepository, never()).findByOwnerUsername(any());
        verify(transactionTemplate, never()).executeWithoutResult(any());
    }

    @Test
    @DisplayName("DEL-02: Borrar usuario sin archivos ni avatar")
    void purgeUserFully_Success_WhenUserHasNoFilesAndNoAvatar() {
        userEntity.setAvatarUrl(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(fileRepository.findByOwnerUsername("user1")).thenReturn(Collections.emptyList());

        userDeleteService.purgeUserFully(1L);

        // Verificar que se limpia la base de datos
        verify(fileRepository, times(1)).deleteByOwnerId(1L);
        verify(userKeyRepository, times(1)).deleteById(1L);
        verify(userRepository, times(1)).delete(userEntity);
        verifyNoInteractions(storageUtils); // No hay archivos que borrar
    }

    // ==========================================
    // TESTS DISCO
    // ==========================================

    @Test
    @DisplayName("DEL-03: Eliminación correcta de todos los ficheros")
    void purgeUserFully_Success_DeletesAllPhysicalFiles() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(fileRepository.findByOwnerUsername("user1")).thenReturn(List.of(file1, file2));

        userDeleteService.purgeUserFully(1L);

        // Se borraron los archivos existentes
        verify(storageUtils, times(1)).deletePhysicalFile("uploads/user1/file1.enc");
        verify(storageUtils, times(1)).deletePhysicalFile("uploads/user1/file2.enc");
    }

    @Test
    @DisplayName("DEL-04: Resiliencia del borrado ante fallos específicos de ficheros")
    void purgeUserFully_Resilient_WhenAFileDeletionThrowsException() throws Exception {
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(fileRepository.findByOwnerUsername("user1")).thenReturn(List.of(file1, file2));

        // Provocamos un fallo en un fichero
        doThrow(new RuntimeException("Disco bloqueado")).when(storageUtils).deletePhysicalFile("uploads/user1/file1.enc");

        assertDoesNotThrow(() -> userDeleteService.purgeUserFully(1L));

        // El segundo se borró igualmente
        verify(storageUtils, times(1)).deletePhysicalFile("uploads/user1/file2.enc");
    }

    @Test
    @DisplayName("DEL-05: Se descartan ficheros con rutas nulas")
    void purgeUserFully_FiltersNullPaths_Correctly() throws Exception {
        FileEntity fileCorrupto = new FileEntity();
        fileCorrupto.setId(103L);
        fileCorrupto.setStoragePath(null);

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(fileRepository.findByOwnerUsername("user1")).thenReturn(List.of(file1, fileCorrupto));

        userDeleteService.purgeUserFully(1L);

        verify(storageUtils, times(1)).deletePhysicalFile("uploads/user1/file1.enc");
        verify(storageUtils, never()).deletePhysicalFile(null);
    }

    // ==========================================
    // TESTS AVATARES
    // ==========================================

    @Test
    @DisplayName("DEL-06: Eliminación correcta del avatar")
    void purgeUserFully_Success_DeletesAvatarFromDisk() throws IOException {
        Path avatarsFolder = tempDir.resolve("avatars");
        Files.createDirectories(avatarsFolder);
        Path mockAvatarFile = avatarsFolder.resolve("avatar1.png");
        Files.writeString(mockAvatarFile, "avatar1");

        assertTrue(Files.exists(mockAvatarFile));

        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(fileRepository.findByOwnerUsername("user1")).thenReturn(Collections.emptyList());

        userDeleteService.purgeUserFully(1L);

        // El archivo se borró correctamente
        assertFalse(Files.exists(mockAvatarFile));
    }

    @Test
    @DisplayName("DEL-07: Omitir borrado ante URLs externas")
    void purgeUserFully_SkipsAvatarDeletion_WhenUrlIsExternal() throws IOException {
        userEntity.setAvatarUrl("https://lh3.googleusercontent.com/a/avatar_externo");
        when(userRepository.findById(1L)).thenReturn(Optional.of(userEntity));
        when(fileRepository.findByOwnerUsername("user1")).thenReturn(Collections.emptyList());

        userDeleteService.purgeUserFully(1L);

        Path avatarsFolder = tempDir.resolve("avatars");
        assertFalse(Files.exists(avatarsFolder));
    }
}