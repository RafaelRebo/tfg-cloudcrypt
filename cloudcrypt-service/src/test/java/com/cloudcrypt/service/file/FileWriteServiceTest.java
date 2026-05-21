package com.cloudcrypt.service.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.dto.file.FileUploadRequestDto;
import com.cloudcrypt.exceptions.*;
import com.cloudcrypt.mapper.FileMapper;
import com.cloudcrypt.model.*;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.repository.user.UserRepository;
import com.cloudcrypt.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileWriteServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileKeyRepository fileKeyRepository;
    @Mock private UserRepository userRepository;
    @Mock private StorageUtils storageUtils;
    @Mock private QuotaUtils quotaUtils;
    @Mock private FileMapper fileMapper;
    @Mock private FolderService folderService;
    @Mock private EntityManager entityManager;

    @InjectMocks private FileWriteService fileWriteService;

    private UserEntity userEntity;
    private FileEntity rootFolder;
    private FileEntity fileEntity;
    private FileDto fileDto;
    private FileUploadRequestDto uploadDto;
    private MockMultipartFile multipartFile;

    @BeforeEach
    void setUp() {
        userEntity = new UserEntity();
        userEntity.setId(1L);
        userEntity.setUsername("user1");

        rootFolder = new FileEntity();
        rootFolder.setId(50L);
        rootFolder.setFileName("documentos");
        rootFolder.setFileType("application/x-directory");
        rootFolder.setFolderPath("/");
        rootFolder.setOwner(userEntity);

        fileEntity = new FileEntity();
        fileEntity.setId(101L);
        fileEntity.setFileName("foto.png");
        fileEntity.setFileType("image/png");
        fileEntity.setStoragePath("uploads/1/file101.enc");
        fileEntity.setFolderPath("/");
        fileEntity.setOwner(userEntity);

        fileDto = new FileDto();
        fileDto.setId(101L);
        fileDto.setFileName("foto.png");

        multipartFile = new MockMultipartFile("file", "foto.png", "image/png", "data".getBytes());

        uploadDto = new FileUploadRequestDto();
        uploadDto.setFileName("foto.png");
        uploadDto.setFile(multipartFile);
        uploadDto.setEncryptedFileKey("encryptedsymkey");
        uploadDto.setParentId(null);

        lenient().when(userRepository.findByUsername("user1")).thenReturn(userEntity);
        lenient().when(fileMapper.toDto(any(FileEntity.class), eq("user1"))).thenReturn(fileDto);
    }

    // ==========================================
    // 1. TEST: uploadFile()
    // ==========================================

    @Test
    @DisplayName("WRT-01: Subida exitosa en la raíz")
    void uploadFile_Success_InRoot() throws Exception {
        // Hacemos que se guarde el fichero y nos devuelva sus metadatos físicos
        Map<String, String> storageResult = Map.of("storagePath", "uploads/1/file101.enc", "checksum", "sha256_checksum_123");
        when(storageUtils.saveEncryptedPackage(any(InputStream.class), eq(1L), eq("/"))).thenReturn(storageResult);
        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);

        FileDto result = fileWriteService.uploadFile(uploadDto, "user1");

        // Verificamos que se calculó la cuota, se guardó el archivo y su clave simétrica
        assertNotNull(result);
        verify(quotaUtils).checkQuota("user1", multipartFile.getSize());
        verify(fileKeyRepository).save(any(FileKeyEntity.class));
    }

    @Test
    @DisplayName("WRT-02: Subida exitosa en una carpeta")
    void uploadFile_Success_InsideFolder() throws Exception {
        uploadDto.setParentId(50L);
        when(fileRepository.findById(50L)).thenReturn(Optional.of(rootFolder));

        // Subimos el fichero dentro de una carpeta documentos
        Map<String, String> storageResult = Map.of("storagePath", "uploads/1/file101.enc", "checksum", "checksum_val");
        when(storageUtils.saveEncryptedPackage(any(InputStream.class), eq(1L), eq("/documentos"))).thenReturn(storageResult);
        when(fileRepository.save(any(FileEntity.class))).thenReturn(fileEntity);

        fileWriteService.uploadFile(uploadDto, "user1");

        // Verificamos que el fichero se ha guardado en dicha carpeta
        verify(storageUtils).saveEncryptedPackage(any(InputStream.class), eq(1L), eq("/documentos"));
    }

    @Test
    @DisplayName("WRT-03: Rollback en disco si la transacción falla")
    void uploadFile_RollbackPhysicalFile_WhenDatabaseFails() throws Exception {
        Map<String, String> storageResult = Map.of("storagePath", "uploads/1/file.enc", "checksum", "checksum");
        when(storageUtils.saveEncryptedPackage(any(InputStream.class), eq(1L), eq("/"))).thenReturn(storageResult);

        // Provocamos un fallo en la base de datos al guardar el ficheros
        when(fileRepository.save(any(FileEntity.class))).thenThrow(new RuntimeException("Error SQL"));

        // Verificamos que se lanza el error
        assertThrows(InternalStorageException.class, () -> {
            fileWriteService.uploadFile(uploadDto, "user1");
        });

        // Verificamos que el fichero que se estaba guardando se borró
        verify(storageUtils).deletePhysicalFile("uploads/1/file.enc");
    }

    // ==========================================
    // 2. TEST: createFolder() y ensureFolderSync()
    // ==========================================

    @Test
    @DisplayName("WRT-04: Bloquear creación de carpetas con carácteres inválidos")
    void createFolder_ThrowsInputValidationException_OnInvalidNames() {
        assertThrows(InputValidationException.class, () -> fileWriteService.createFolder(null, "user1", null));
        assertThrows(InputValidationException.class, () -> fileWriteService.createFolder("   ", "user1", null));
        assertThrows(InputValidationException.class, () -> fileWriteService.createFolder("fotos/carpeta", "user1", null));
        assertThrows(InputValidationException.class, () -> fileWriteService.createFolder("../carpeta", "user1", null));
    }

    @Test
    @DisplayName("WRT-05: Creación de un directorio")
    void createFolder_Success() {
        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(rootFolder));
        when(folderService.ensureExists(eq("user1"), eq("Fotos"), eq(rootFolder))).thenReturn(new FileEntity());

        fileWriteService.createFolder("Fotos", "user1", 50L);

        // Verificamos que existe un directorio con el nombre deseado en el rootFolder tras llamar a la función
        verify(folderService).ensureExists("user1", "Fotos", rootFolder);
    }

    // ==========================================
    // 3. TEST: toggleStar() (Metadatos Destacados)
    // ==========================================

    @Test
    @DisplayName("WRT-06: Invertir elementos destacados")
    void toggleStar_Success() {
        FileKeyEntity key = new FileKeyEntity();
        key.setStarred(false); // Inicialmente no está destacado
        key.setFile(fileEntity);

        when(fileKeyRepository.findByFileIdAndUser_Username(101L, "user1")).thenReturn(Optional.of(key));
        when(fileKeyRepository.save(key)).thenReturn(key);

        fileWriteService.toggleStar(101L, "user1");

        // Tras marcar el fichero como destacado comprobamos que la función ahora devuelve true
        assertTrue(key.isStarred());
    }

    // ==========================================
    // 4. TEST: moveFiles()
    // ==========================================

    @Test
    @DisplayName("WRT-07: Bloquear operación de mover si el destino no es una carpeta")
    void moveFiles_ThrowsInputValidationException_WhenTargetIsNotFolder() {
        FileEntity targetIsAFile = new FileEntity();
        targetIsAFile.setId(200L);
        targetIsAFile.setFileType("image/png"); // No es una carpeta

        when(fileRepository.findByIdAndOwner_Username(200L, "user1")).thenReturn(Optional.of(targetIsAFile));

        // Verificamos que la función devuelve una excepción
        assertThrows(InputValidationException.class, () -> {
            fileWriteService.moveFiles(List.of(101L), 200L, "user1");
        });
    }

    @Test
    @DisplayName("WRT-08: Bloquear mover un directorio a sí mismo")
    void moveFiles_ThrowsInputValidationException_WhenAnidatingFolderIntoItself() {
        // Hacemos que el destino sea el propio elemento origen
        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(rootFolder));

        // Validamos que se lanza una excepción impidiendo la operación
        assertThrows(InputValidationException.class, () -> {
            fileWriteService.moveFiles(List.of(50L), 50L, "user1");
        });
    }

    @Test
    @DisplayName("WRT-09: Desplazamiento correcto de archivos")
    void moveFiles_Success_WithFile() {
        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(rootFolder));
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));

        fileWriteService.moveFiles(List.of(101L), 50L, "user1");

        // Comprobamos que el fichero ahora se encuentra de la carpeta /documentos
        assertEquals(rootFolder, fileEntity.getParent());
        assertEquals("/documentos", fileEntity.getFolderPath());
        verify(fileRepository).save(fileEntity);
    }

    // ==========================================
    // 5. TEST: renameFile()
    // ==========================================

    @Test
    @DisplayName("WRT-10: Denegar el renombramiento de elementos en la papelera")
    void renameFile_ThrowsException_WhenElementIsInTrash() {
        fileEntity.setDeletedAt(LocalDateTime.now()); // Marcamos como eliminado
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));

        // Validamos que no podemos renombrar un fichero que esté en la papelera
        assertThrows(InputValidationException.class, () -> {
            fileWriteService.renameFile(101L, "file_new.png", "user1");
        });
    }

    @Test
    @DisplayName("WRT-11: Abortar si el nuevo nombre genera una colisión")
    void renameFile_ThrowsException_OnNameCollision() {
        FileEntity conflictiveFile = new FileEntity();
        conflictiveFile.setId(999L); // Otro ID con el mismo nombre deseado

        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull("user1", "colision.png"))
                .thenReturn(Optional.of(conflictiveFile));

        // Hacemos que se intente renombrar a colision.png, que ya existe, lo que genera una excepción
        assertThrows(InputValidationException.class, () -> {
            fileWriteService.renameFile(101L, "colision.png", "user1");
        });
    }

    @Test
    @DisplayName("WRT-12: Renombrado de un directorio recalculando la ruta de todos sus descendientes")
    void renameFile_Success_WithFolderUpdatingDescendants() {
        // Modificamos rootFolder
        rootFolder.setFileName("trabajos");
        rootFolder.setFolderPath("/");

        FileEntity childFile = new FileEntity();
        childFile.setId(102L);
        childFile.setFileName("memoria.docx"); // Este fichero está dentro de la carpeta /trabajos
        childFile.setFolderPath("/trabajos");

        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(rootFolder));
        when(fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull(eq("user1"), anyString()))
                .thenReturn(Optional.empty());

        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/trabajos", 50L))
                .thenReturn(List.of(rootFolder, childFile));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Renombramos la carpeta trabajos a proyectos
        fileWriteService.renameFile(50L, "proyectos", "user1");

        // El fichero hijo cambió su ruta para adecuarse al cambio
        assertEquals("proyectos", rootFolder.getFileName());
        assertEquals("/proyectos", childFile.getFolderPath());
    }

    // ==========================================
    // 6. TEST: copyFiles()
    // ==========================================

    @Test
    @DisplayName("WRT-13: Lanzar excepción si el ID origen de copia no existe")
    void copyFiles_ThrowsException_WhenSourceNotFound() {
        when(fileRepository.findByIdAndOwner_Username(888L, "user1")).thenReturn(Optional.empty());

        // Verificamos que salta una excepción si el origen de copia es incorrecto
        assertThrows(InputValidationException.class, () -> {
            fileWriteService.copyFiles(List.of(888L), null, "copia.png", "user1");
        });
    }

    @Test
    @DisplayName("WRT-14: Copia múltiple exitosa")
    void copyFiles_Success_WithSingleFile() {
        fileEntity.setFileSize(500L);
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));

        FileKeyEntity oldKey = new FileKeyEntity();
        oldKey.setEncryptedKey("symkey");
        when(fileKeyRepository.findByFileIdAndUser_Username(101L, "user1")).thenReturn(Optional.of(oldKey));

        fileWriteService.copyFiles(List.of(101L), null, "foto_copiada.png", "user1");

        // Verificamos la cuota y la ejecución de la copia del fichero
        verify(quotaUtils).checkQuota("user1", 500L);
        verify(fileRepository).saveAll(anyCollection());
        verify(fileKeyRepository).saveAll(anyCollection());
    }

    @Test
    @DisplayName("WRT-15: Fallo al copiar por exceso de espacio ocupado")
    void copyFiles_ThrowsQuotaException_WhenBatchExceedsStorage() {
        fileEntity.setFileSize(9000000L);
        when(fileRepository.findByIdAndOwner_Username(101L, "user1")).thenReturn(Optional.of(fileEntity));

        doThrow(new QuotaExceededException("Cuota insuficiente")).when(quotaUtils).checkQuota(eq("user1"), anyLong());

        // Una excepción se lanza si al copiar sobrepasamos límite de espacio
        assertThrows(QuotaExceededException.class, () -> {
            fileWriteService.copyFiles(List.of(101L), null, "foto_clonada.png", "user1");
        });

        // Verificamos que no se guardó nada en BD
        verify(fileRepository, never()).saveAll(any());
        verify(fileKeyRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("WRT-16: Copia recursiva")
    void copyFiles_Success_RecursiveFolderTree() {
        // Creamos carpeta origen con datos.txt dentro
        rootFolder.setFileName("origen");
        rootFolder.setFolderPath("/");
        rootFolder.setFileSize(0L);

        FileEntity subFile = new FileEntity();
        subFile.setId(102L);
        subFile.setFileName("datos.txt");
        subFile.setFileType("text/plain");
        subFile.setFileSize(100L);
        subFile.setFolderPath("/origen");
        subFile.setParent(rootFolder);

        when(fileRepository.findByIdAndOwner_Username(50L, "user1")).thenReturn(Optional.of(rootFolder));
        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/origen", 50L))
                .thenReturn(List.of(rootFolder, subFile));

        FileKeyEntity dummyKey = new FileKeyEntity();
        dummyKey.setEncryptedKey("symkey");
        lenient().when(fileKeyRepository.findByFileIdAndUser_Username(anyLong(), eq("user1")))
                .thenReturn(Optional.of(dummyKey));

        // Lanzamos la copia a destino
        fileWriteService.copyFiles(List.of(50L), null, "destino", "user1");

        // Verificamos que los ficheros se copiaron correctamente
        verify(quotaUtils).checkQuota("user1", 100L);
        verify(fileRepository, times(1)).saveAll(anyCollection());
    }
}