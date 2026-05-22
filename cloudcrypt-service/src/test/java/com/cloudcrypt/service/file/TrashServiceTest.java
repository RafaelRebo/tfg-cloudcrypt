package com.cloudcrypt.service.file;

import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.util.PathUtils;
import com.cloudcrypt.util.StorageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TrashServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileKeyRepository fileKeyRepository;
    @Mock private StorageUtils storageUtils;
    @Mock private PathUtils pathUtils;
    @Mock private FolderService folderService;

    @InjectMocks private TrashService trashService;

    private UserEntity owner;
    private FileEntity plainFile;
    private FileEntity folderEntity;

    @BeforeEach
    void setUp() {
        owner = new UserEntity();
        owner.setId(1L);
        owner.setUsername("user1");

        plainFile = new FileEntity();
        plainFile.setId(101L);
        plainFile.setFileName("documento.pdf");
        plainFile.setFileType("application/pdf");
        plainFile.setStoragePath("uploads/1/file101.enc");
        plainFile.setFolderPath("/");
        plainFile.setOwner(owner);

        folderEntity = new FileEntity();
        folderEntity.setId(50L);
        folderEntity.setFileName("carpeta");
        folderEntity.setFileType("application/x-directory");
        folderEntity.setFolderPath("/");
        folderEntity.setOwner(owner);
    }

    // ==========================================
    // 1. TEST: deleteFile()
    // ==========================================

    @Test
    @DisplayName("TRSH-01: Borrado a papelera")
    void deleteFile_LogicalDelete_PlainFile() {
        // Como no estaba borrado se envía a la papelera
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(plainFile));

        trashService.deleteFile(101L, "user1", false);

        // Verificamos que se marcó como deletedAt en la base de datos, pero que no se accedió al almacenamiento físico porque no se borró del todo
        verify(fileRepository, times(1)).markAsDeleted(101L);
        verifyNoInteractions(storageUtils);
    }

    @Test
    @DisplayName("TRSH-02: Borrado en cascada al enviar un directorio a la papelera")
    void deleteFile_LogicalDelete_DirectoryWithChildren() {
        folderEntity.setFileName("proyectos");
        FileEntity child = new FileEntity();
        child.setId(102L);

        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(folderEntity));
        when(pathUtils.join("/", "proyectos")).thenReturn("/proyectos");
        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/proyectos", 50L))
                .thenReturn(List.of(child));

        trashService.deleteFile(50L, "user1", false);

        // Verificamos que al marcar como borrada la carpeta de ID 50, también se marcó como borrado el fichero con ID 102 interno
        verify(fileRepository).markAsDeleted(50L);
        verify(fileRepository).markAsDeleted(102L);
    }

    @Test
    @DisplayName("TRSH-03: Borrado físico definitivo de un archivo")
    void deleteFile_PermanentDelete_PlainFile() throws IOException {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(plainFile));

        trashService.deleteFile(101L, "user1", true); // Forzamos el borrado definitivo con el flag

        // Valida que se borró de la base de datos y, ahora sí, también del almacenamiento físico
        verify(fileRepository, times(1)).deleteAllInBatch(anyList());
        verify(storageUtils, times(1)).deletePhysicalFile("uploads/1/file101.enc");
    }

    @Test
    @DisplayName("TRSH-04: Borrado físico definitivo  si el archivo ya estaba en la papelera")
    void deleteFile_PermanentDelete_WhenAlreadyInTrash() throws IOException {
        plainFile.setDeletedAt(LocalDateTime.now());
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(plainFile));

        trashService.deleteFile(101L, "user1", false); // Ya estaba en la papelera, se borra aun sin el flag

        // Valida que se borró de la base de datos y también del almacenamiento físico
        verify(fileRepository, times(1)).deleteAllInBatch(anyList());
        verify(storageUtils).deletePhysicalFile("uploads/1/file101.enc");
    }

    @Test
    @DisplayName("TRSH-05: Destrucción física de un árbol de carpetas completo")
    void deleteFile_PermanentDelete_RecursiveDirectoryTree() throws IOException {
        folderEntity.setFileName("fotos");
        FileEntity childFile = new FileEntity();
        childFile.setId(102L);
        childFile.setFileType("image/png");
        childFile.setStoragePath("uploads/1/file102.enc");

        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(folderEntity));
        when(pathUtils.join("/", "fotos")).thenReturn("/fotos");
        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/fotos", 50L))
                .thenReturn(List.of(childFile));

        trashService.deleteFile(50L, "user1", true);

        // Verificamos que se borró tanto la carpeta como el fichero que había dentro
        verify(fileRepository, times(2)).deleteAllInBatch(anyList());
        verify(storageUtils).deletePhysicalFile("uploads/1/file102.enc");
    }

    @Test
    @DisplayName("TRSH-06: Resiliencia ante fallos físicos de disco duro")
    void deleteFile_Resilient_WhenPhysicalDeletionThrowsIOException() throws IOException {
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(plainFile));
        doThrow(new IOException("Hardware failure")).when(storageUtils).deletePhysicalFile("uploads/1/file101.enc");

        // A pesar de existir la excepción el método no se bloquea
        assertDoesNotThrow(() -> trashService.deleteFile(101L, "user1", true));

        // La transacción finaliza igualmente
        verify(fileRepository, times(1)).deleteAllInBatch(anyList());
    }

    // ==========================================
    // 2. TESTS PARA: Renunciar a fichero compartido
    // ==========================================

    @Test
    @DisplayName("TRSH-07: Renuncia de privilegios si el solicitante es un invitado compartido")
    void deleteFile_SharedRevocation_WhenUserHasOnlyAccess() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user2")).thenReturn(Optional.empty());
        when(fileRepository.findByIdAndHasAccess(101L, "user2")).thenReturn(Optional.of(plainFile));

        // user2 ejecuta un borrado sobre un fichero que le compartieron, por ende, el fichero físico no se borra, solo que el se quita a sí mismo el acceso
        trashService.deleteFile(101L, "user2", false);

        // No se borra el fichero físico ni de la BD, solo se borra la clave que le daba acceso compartido a user2 al fichero de ID 101
        verify(fileKeyRepository, times(1)).deleteByFileIdAndUser_Username(101L, "user2");
        verify(fileRepository, never()).delete(any());
        verify(fileRepository, never()).deleteAllInBatch(anyList());
        verify(fileRepository, never()).markAsDeleted(anyLong());
    }

    @Test
    @DisplayName("TRSH-08: Lanzar excepción si el recurso no le pertenece ni se le compartió")
    void deleteFile_ThrowsException_WhenNoOwnershipAndNoAccess() {
        when(fileRepository.findByIdAndOwner_Username(101L, "user3")).thenReturn(Optional.empty());
        when(fileRepository.findByIdAndHasAccess(101L, "user3")).thenReturn(Optional.empty());

        // user3 no es propietario ni le compartieron el fichero de ID 101, debe recibir excepción
        assertThrows(InstanceNotFoundException.class, () -> {
            trashService.deleteFile(101L, "user3", false);
        });
    }

    // ==========================================
    // 3. TESTS PARA: restoreFile()
    // ==========================================

    @Test
    @DisplayName("TRSH-09: Rescate exitoso de archivo")
    void restoreFile_Success_PlainFile() {
        plainFile.setFolderPath("/documentos/pdf");
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.of(plainFile));

        trashService.restoreFile(101L, "user1");

        // Como el documento estaba dentro de /documentos, se tuvo que llamar a restoreParentHierarchy para recuperar la estructura completa
        verify(folderService).restoreParentHierarchy(owner.getUsername(), "/documentos/pdf");
        verify(fileRepository).restoreFile(101L);
    }

    @Test
    @DisplayName("TRSH-10: Rescate completo de un directorio y su contenido")
    void restoreFile_Success_RecursiveDirectory() {
        folderEntity.setFileName("documentos");
        folderEntity.setFolderPath("/");
        FileEntity child = new FileEntity();
        child.setId(102L);

        when(fileRepository.findByIdAndHasAccess(50L, "user1")).thenReturn(Optional.of(folderEntity));
        when(pathUtils.join("/", "documentos")).thenReturn("/documentos");
        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/documentos", 50L))
                .thenReturn(List.of(child));

        // Restablecemos la carpeta de ID 50 documentos
        trashService.restoreFile(50L, "user1");

        // Se llamó a BD para restablecer tanto la carpeta documentos como el fichero que tenía dentro
        verify(fileRepository).restoreFile(50L);
        verify(fileRepository).restoreFile(102L);
    }
}