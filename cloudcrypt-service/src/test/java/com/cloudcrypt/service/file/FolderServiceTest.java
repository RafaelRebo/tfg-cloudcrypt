package com.cloudcrypt.service.file;

import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.PathUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FolderServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileKeyRepository fileKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private PathUtils pathUtils;

    @InjectMocks private FolderService folderService;

    private UserEntity userEntity;
    private FileEntity existingFolder;

    @BeforeEach
    void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user1");

        existingFolder = new FileEntity();
        existingFolder.setId(50L);
        existingFolder.setFileName("documentos");
        existingFolder.setFileType("application/x-directory");
        existingFolder.setFolderPath("/");
        existingFolder.setOwner(userEntity);
    }

    // ==========================================
    // 1. TEST: ensureExists()
    // ==========================================

    @Test
    @DisplayName("FLD-01: Retornar carpeta existente")
    void ensureExists_ReturnsExistingFolder_WhenAlreadyPresent() {
        when(fileRepository.findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull("user1", "documentos", null))
                .thenReturn(Optional.of(existingFolder));

        FileEntity result = folderService.ensureExists("user1", "documentos", null);

        // Si la carpeta existe en el directorio, se devuelve directamente con sus datos sin consultar la BD
        assertNotNull(result);
        assertEquals(50L, result.getId());
        verify(fileRepository, never()).save(any());
        verify(fileKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("FLD-02: Creación exitosa de un directorio no existente previamente")
    void ensureExists_CreatesNewFolder_InRoot() {
        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(fileRepository.findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull("user1", "Imágenes", null))
                .thenReturn(Optional.empty()); // No existe previamente
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));

        FileEntity result = folderService.ensureExists("user1", "Imágenes", null);

        // Tras crear la carpeta verificamos que existe
        assertNotNull(result);
        assertEquals("Imágenes", result.getFileName());
        assertEquals("/", result.getFolderPath());
        assertEquals("application/x-directory", result.getFileType());
        assertEquals(0L, result.getFileSize());

        // Verificamos que se guardó junto a la clave FOLDER_PERMISSION predeterminada en BD
        verify(fileRepository).save(any(FileEntity.class));
        verify(fileKeyRepository).save(any(FileKeyEntity.class));
    }

    @Test
    @DisplayName("FLD-03: Creación de subdirectorio")
    void ensureExists_CreatesNewFolder_InsideAnotherFolder() {
        existingFolder.setFileName("proyectos");
        existingFolder.setFolderPath("/trabajos");  // Carpeta padre

        when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        when(fileRepository.findByOwner_UsernameAndFileNameAndParentAndDeletedAtIsNull("user1", "test", existingFolder))
                .thenReturn(Optional.empty());
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));

        FileEntity result = folderService.ensureExists("user1", "test", existingFolder);

        // Verificamos que creando una carpeta test, esta se crea dentro de /trabajos/proyectos
        assertNotNull(result);
        assertEquals("/trabajos/proyectos", result.getFolderPath());
        assertEquals(existingFolder, result.getParent());
    }

    // ==========================================
    // 2. TEST: restoreParentHierarchy()
    // ==========================================

    @Test
    @DisplayName("FLD-04: No se hacen operaciones si la carpeta es la raíz o nula")
    void restoreParentHierarchy_ReturnsImmediately_OnRootOrNull() {
        folderService.restoreParentHierarchy("user1", null);
        folderService.restoreParentHierarchy("user1", "/");

        verifyNoInteractions(fileRepository);
    }

    @Test
    @DisplayName("FLD-05: No restablecer nada si la cerpeta no está eliminada")
    void restoreParentHierarchy_SkipsRestore_WhenParentIsNotDeleted() {
        existingFolder.setDeletedAt(null); // Carpeta no borrada

        when(fileRepository.findByOwner_UsernameAndFileNameAndFolderPathAndFileType("user1", "documentos", "/", "application/x-directory"))
                .thenReturn(Optional.of(existingFolder));

        folderService.restoreParentHierarchy("user1", "/documentos/pdf");

        // Al no estar borrado no se invoca el método
        verify(fileRepository, never()).restoreFile(anyLong());
    }

    @Test
    @DisplayName("FLD-06: Restablecer un directorio desde la papelera")
    void restoreParentHierarchy_ExecutesRestore_WhenParentIsDeleted() {
        existingFolder.setDeletedAt(LocalDateTime.now()); // Marcado en la papelera

        when(fileRepository.findByOwner_UsernameAndFileNameAndFolderPathAndFileType("user1", "documentos", "/", "application/x-directory"))
                .thenReturn(Optional.of(existingFolder));
        when(pathUtils.join("/", "documentos")).thenReturn("/documentos");

        folderService.restoreParentHierarchy("user1", "/documentos/informe.docx");

        // Se tuvo que llamar a la función para restablecer la jerarquía puesto que el fichero estaba contenido en un directorio en la papelera
        verify(fileRepository, times(1)).restoreFile(50L);
        verify(pathUtils).join("/", "documentos");
    }
}