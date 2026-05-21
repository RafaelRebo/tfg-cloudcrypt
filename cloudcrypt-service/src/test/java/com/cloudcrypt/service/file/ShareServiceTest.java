package com.cloudcrypt.service.file;

import com.cloudcrypt.dto.file.ShareRequestDto;
import com.cloudcrypt.exceptions.FileAccessDeniedException;
import com.cloudcrypt.exceptions.InputValidationException;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.PathUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ShareServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileKeyRepository fileKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PathUtils pathUtils;
    @Mock private EntityManager entityManager;

    @InjectMocks private ShareService shareService;

    private UserEntity owner;
    private UserEntity target;
    private FileEntity fileEntity;
    private ShareRequestDto shareRequestDto;

    @BeforeEach
    void setUp() {
        owner = new UserEntity();
        owner.setId(1L);
        owner.setUsername("user1");

        target = new UserEntity();
        target.setId(2L);
        target.setUsername("user2");

        fileEntity = new FileEntity();
        fileEntity.setId(101L);
        fileEntity.setFileName("secreto.enc");
        fileEntity.setFileType("application/octet-stream");
        fileEntity.setFolderPath("/");
        fileEntity.setOwner(owner);
        fileEntity.setFileKeys(new ArrayList<>()); // Keyring vacío

        shareRequestDto = new ShareRequestDto();
        shareRequestDto.setFileId(101L);
        shareRequestDto.setTargetUsername("user2");
        shareRequestDto.setEncryptedKey("encryptedsymkeywithtargetpubkey");
    }

    // ==========================================
    // 1. TEST: shareFile()
    // ==========================================

    @Test
    @DisplayName("SHR-01: Lanzar excepción si el emisor no es dueño del recurso")
    void shareFile_ThrowsFileAccessDeniedException_WhenNotOwner() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.empty());

        // Aseguramos que no se puede compartir un fichero del que no se es propietario
        assertThrows(FileAccessDeniedException.class, () -> {
            shareService.shareFile(101L, List.of(shareRequestDto), "user1");
        });

        verify(fileKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("SHR-02: Compartición única exitosa'")
    void shareFile_Success_AndRepairsOwnerKey() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(userRepository.findByUsername("user2")).thenReturn(target);
        when(fileKeyRepository.findByFileIdAndUser_Username(101L, "user2")).thenReturn(Optional.empty());

        shareService.shareFile(101L, List.of(shareRequestDto), "user1");

        // Tras llamar al método, verificamos que se guardaron dos claves para el fichero compartido, la del usuario emisor y del receptor
        verify(fileKeyRepository, times(2)).save(any(FileKeyEntity.class));
        verify(fileKeyRepository).flush();
        verify(entityManager).refresh(fileEntity);
    }

    @Test
    @DisplayName("SHR-03: Omitir usuarios destino inexistentes")
    void shareFile_SkipsInvalidTargetUser() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(userRepository.findByUsername("user3")).thenReturn(null); // No existe

        shareRequestDto.setTargetUsername("user3");

        // No salta una excepción, el método seguiría compartiendo con los demás usuarios
        assertDoesNotThrow(() -> shareService.shareFile(101L, List.of(shareRequestDto), "user1"));
    }

    // ==========================================
    // 2. TEST: shareBatch()
    // ==========================================

    @Test
    @DisplayName("SHR-04: Impedir compartir ficheros a sí mismo")
    void shareBatch_ThrowsInputValidationException_WhenSharingWithSelf() {
        shareRequestDto.setTargetUsername("user1");

        // Salta una excepción al intentar compartir ficheros con uno mismo
        assertThrows(InputValidationException.class, () -> {
            shareService.shareBatch(List.of(shareRequestDto), "user1");
        });
    }

    @Test
    @DisplayName("SHR-05: Validar que todos los archivos pertenecen al emisor")
    void shareBatch_ThrowsFileAccessDeniedException_WhenFileInBatchIsNotOwned() {
        UserEntity other = new UserEntity();
        other.setUsername("other");
        fileEntity.setOwner(other); // El archivo le pertenece a other

        when(fileRepository.findById(101L)).thenReturn(Optional.of(fileEntity));

        // Comprobamos que ahora a user1 no le deja compartir esos ficheros
        assertThrows(FileAccessDeniedException.class, () -> {
            shareService.shareBatch(List.of(shareRequestDto), "user1");
        });
    }

    @Test
    @DisplayName("SHR-06: Procesamiento múltiple exitoso")
    void shareBatch_Success_SavesAllKeys() {
        when(fileRepository.findById(101L)).thenReturn(Optional.of(fileEntity));
        when(userRepository.findByUsername("user2")).thenReturn(target);
        when(fileKeyRepository.findByFileIdAndUser_Username(101L, "user2")).thenReturn(Optional.empty());

        shareService.shareBatch(List.of(shareRequestDto), "user1");

        // Verificamos que solo se llama una vez por transacción al método de BD
        verify(fileKeyRepository, times(1)).saveAll(anyCollection());
    }

    // ==========================================
    // 3. TEST: revokeAccess()
    // ==========================================

    @Test
    @DisplayName("SHR-07: Revocación de privilegios")
    void revokeAccess_Success_WithPlainFile() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));

        shareService.revokeAccess(101L, "user2", "user1");

        // Comprobamos que se ejecutó la función que elimina la compartición del fichero con ID 101 con el usuario user2
        verify(fileKeyRepository, times(1)).deleteByFileIdAndUser_Username(101L, "user2");
    }

    @Test
    @DisplayName("SHR-08: Revocación en cascada con directorios")
    void revokeAccess_Success_RecursiveFolderRevocation() {
        fileEntity.setFileType("application/x-directory");
        fileEntity.setFileName("sharedfolder");
        fileEntity.setFolderPath("/root");

        FileEntity child = new FileEntity();
        child.setId(999L); // Archivo dentro de la carpeta

        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(pathUtils.join("/root", "sharedfolder")).thenReturn("/root/sharedfolder");

        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/root/sharedfolder", 101L))
                .thenReturn(List.of(child));

        shareService.revokeAccess(101L, "user2", "user1");

        // Verificamos que se quita el acceso de user2 tanto al fichero como a la carpeta en una sola operación
        verify(fileKeyRepository).deleteByFileIdAndUser_Username(101L, "user2");
        verify(fileKeyRepository).deleteByFileIdAndUser_Username(999L, "user2");
    }

    // ==========================================
    // 4. TEST: getSharedUsernames()
    // ==========================================

    @Test
    @DisplayName("SHR-09: Recuperar lista de usuarios con acceso")
    void getSharedUsernames_Success_ExcludesOwner() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(fileKeyRepository.findUsernamesByFileId(101L)).thenReturn(List.of("user1", "user2"));

        List<String> result = shareService.getSharedUsernames(101L, "user1");

        // Comprobamos que aparece user2, que es con quien se compartió y se excluye a user1 que es el dueño
        assertEquals(1, result.size());
        assertEquals("user2", result.get(0));
    }

    @Test
    @DisplayName("SHR-10: Impedir que se busquen compartidos de otro usuario")
    void getSharedUsernames_ThrowsException_WhenNoAccess() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user3")).thenReturn(Optional.empty());

        // Impedimos con una excepción que user3 vea con quien está compartido un fichero que no es suyo
        assertThrows(FileAccessDeniedException.class, () -> {
            shareService.getSharedUsernames(101L, "user3");
        });
    }
}