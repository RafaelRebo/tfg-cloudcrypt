package com.cloudcrypt.service.file;

import com.cloudcrypt.dto.file.FileDto;
import com.cloudcrypt.exceptions.InputValidationException;
import com.cloudcrypt.exceptions.InstanceNotFoundException;
import com.cloudcrypt.exceptions.InternalStorageException;
import com.cloudcrypt.mapper.FileMapper;
import com.cloudcrypt.model.FileEntity;
import com.cloudcrypt.model.FileKeyEntity;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.file.FileRepository;
import com.cloudcrypt.repository.keys.FileKeyRepository;
import com.cloudcrypt.util.StorageUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FileQueryServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private FileKeyRepository fileKeyRepository;
    @Mock private FileMapper fileMapper;
    @Mock private StorageUtils storageUtils;

    @InjectMocks private FileQueryService fileQueryService;

    private FileEntity fileEntity;
    private FileDto fileDto;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        fileEntity = new FileEntity();
        fileEntity.setId(101L);
        fileEntity.setFileName("documento.pdf");
        fileEntity.setFileType("application/pdf");
        fileEntity.setStoragePath("uploads/1/file101.enc");
        fileEntity.setFolderPath("/documentos");

        fileDto = new FileDto();
        fileDto.setId(101L);
        fileDto.setFileName("documento.pdf");

        lenient().when(fileMapper.toDto(any(FileEntity.class), eq("user1"))).thenReturn(fileDto);
    }

    // ==========================================
    // 1. TEST: getFilesByFolder()
    // ==========================================

    @Test
    @DisplayName("QRY-01: Filtrar por ficheros en papelera")
    void getFilesByFolder_TrashRoot() {
        // Hacemos que haya un fichero en la papelera
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findTrashRoot("user1", pageable)).thenReturn(page);

        Page<FileDto> result = fileQueryService.getFilesByFolder("user1", null, "trash", pageable);

        // Tras llamar a la función nos aseguramos que el fichero vino en la respuesta
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(fileRepository).findTrashRoot("user1", pageable);
    }

    @Test
    @DisplayName("QRY-02: Filtrar por elementos compartidos")
    void getFilesByFolder_SharedWithMe() {
        // Hacemos que haya un fichero compartido con el usuario 1
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findSharedWithMe("user1", 1L, pageable)).thenReturn(page);

        Page<FileDto> result = fileQueryService.getFilesByFolder("user1", 1L, "shared", pageable);

        // Tras llamar a la función nos aseguramos que el fichero vino en la respuesta
        assertNotNull(result);
        verify(fileRepository).findSharedWithMe("user1", 1L, pageable);
    }

    @Test
    @DisplayName("QRY-03: Filtrar por elementos destacados")
    void getFilesByFolder_Starred() {
        // Hacemos que haya un fichero destacado por el usuario 1
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findStarred("user1", pageable)).thenReturn(page);

        Page<FileDto> result = fileQueryService.getFilesByFolder("user1", null, "starred", pageable);

        // Tras llamar a la función nos aseguramos que el fichero vino en la respuesta
        assertNotNull(result);
        verify(fileRepository).findStarred("user1", pageable);
    }

    @Test
    @DisplayName("QRY-04: Navegación por ID de carpeta sin categoría")
    void getFilesByFolder_WithParentId() {
        // Hacemos que la carpeta con ID 5 tenga dentro un fichero
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findByOwner_UsernameAndParentId("user1", 5L, pageable)).thenReturn(page);

        Page<FileDto> result = fileQueryService.getFilesByFolder("user1", 5L, "all", pageable);

        // Tras llamar a la función, pidiendo los ficheros de la carpeta de ID 5, nos aseguramos que el fichero vino en la respuesta
        assertNotNull(result);
        verify(fileRepository).findByOwner_UsernameAndParentId("user1", 5L, pageable);
    }

    @Test
    @DisplayName("QRY-05: Mapeo de subcategoría 'image'")
    void getFilesByFolder_CategoryImage() {
        // Hacemos que se devuelva el fichero si se filtra por imágenes
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findByCategory("user1", "image/%", pageable)).thenReturn(page);

        fileQueryService.getFilesByFolder("user1", null, "image", pageable);

        // Tras llamar a la función verificamos que se devolvió el fichero
        verify(fileRepository).findByCategory("user1", "image/%", pageable);
    }

    @Test
    @DisplayName("QRY-06: Mapeo de subcategoría 'video'")
    void getFilesByFolder_CategoryVideo() {
        // Hacemos que se devuelva el fichero si se filtra por vídeo
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findByCategory("user1", "video/%", pageable)).thenReturn(page);

        fileQueryService.getFilesByFolder("user1", null, "video", pageable);

        // Tras llamar a la función verificamos que se devolvió el fichero
        verify(fileRepository).findByCategory("user1", "video/%", pageable);
    }

    @Test
    @DisplayName("QRY-07: Mapeo de subcategoría 'audio'")
    void getFilesByFolder_CategoryAudio() {
        // Hacemos que se devuelva el fichero si se filtra por audio
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findByCategory("user1", "audio/%", pageable)).thenReturn(page);

        fileQueryService.getFilesByFolder("user1", null, "audio", pageable);

        // Tras llamar a la función verificamos que se devolvió el fichero
        verify(fileRepository).findByCategory("user1", "audio/%", pageable);
    }

    @Test
    @DisplayName("QRY-08: Mapeo de subcategoría 'document'")
    void getFilesByFolder_CategoryDocument() {
        // Hacemos que se devuelva el fichero si se filtra por documento pdf
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findByCategory("user1", "%pdf%", pageable)).thenReturn(page);

        fileQueryService.getFilesByFolder("user1", null, "document", pageable);

        // Tras llamar a la función verificamos que se devolvió el fichero
        verify(fileRepository).findByCategory("user1", "%pdf%", pageable);
    }

    @Test
    @DisplayName("QRY-09: Listado por defecto si la categoría no coincide")
    void getFilesByFolder_DefaultRoot() {
        // Verificamos que por defecto se busque sin categoría si usamos una desconocida
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull("user1", pageable)).thenReturn(page);

        fileQueryService.getFilesByFolder("user1", null, "unknown_category", pageable);

        // Tras llamar a la función verificamos que se devolvió el fichero
        verify(fileRepository).findByOwner_UsernameAndParentIsNullAndDeletedAtIsNull("user1", pageable);
    }

    // ==========================================
    // 2. TEST: getFileById()
    // ==========================================

    @Test
    @DisplayName("QRY-10: Obtener metadatos de archivo por ID")
    void getFileById_Success() {
        // Hacemos que se devuelva un fichero cuando user1 intente acceder al fichero con ID 101
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.of(fileEntity));

        FileDto result = fileQueryService.getFileById(101L, "user1");

        // Tras llamar a la función con esos parámetros vemos que obtenemos el documento con todos sus metadatos
        assertNotNull(result);
        assertEquals("documento.pdf", result.getFileName());
    }

    @Test
    @DisplayName("QRY-11: Lanzar excepción si el archivo no existe o no se tiene de acceso")
    void getFileById_ThrowsException_WhenNotFoundOrNoAccess() {
        // No devolvemos nada cuando el usuario user1 intente acceder al fichero de ID 101 forzando así un error del método
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.empty());

        assertThrows(InstanceNotFoundException.class, () -> {
            fileQueryService.getFileById(101L, "user1");
        });
    }

    // ==========================================
    // 3. TEST: getEncryptedFileKey()
    // ==========================================

    @Test
    @DisplayName("QRY-12: Recuperar clave simétrica cifrada para un archivo")
    void getEncryptedFileKey_Success() {
        // Hacemos que el fichero de ID 101 del usuario user1 esté cifrado con una clave simétrica encriptada "encryptedsymkey"
        FileKeyEntity fileKey = new FileKeyEntity();
        fileKey.setEncryptedKey("encryptedsymkey");
        when(fileKeyRepository.findByFileIdAndUser_Username(101L, "user1")).thenReturn(Optional.of(fileKey));

        String key = fileQueryService.getEncryptedFileKey(101L, "user1");

        // Comprobamos que al invocar el método recibimos la clave simétrica cifrada para poder descifrarla en el lado cliente
        assertEquals("encryptedsymkey", key);
    }

    @Test
    @DisplayName("QRY-13: Lanzar excepción si no existe la relación de claves")
    void getEncryptedFileKey_ThrowsException_WhenNotFound() {
        when(fileKeyRepository.findByFileIdAndUser_Username(101L, "user1")).thenReturn(Optional.empty());

        // Si el usuario no tiene relación de claves asociada salta una excepción
        assertThrows(InstanceNotFoundException.class, () -> {
            fileQueryService.getEncryptedFileKey(101L, "user1");
        });
    }

    // ==========================================
    // 4. TEST: getEncryptedFileKeysBatch()
    // ==========================================

    @Test
    @DisplayName("QRY-14: Retornar mapa vacío si no pasamos IDs de fichero")
    void getEncryptedFileKeysBatch_ReturnsEmptyMap_WhenListIsNullOrEmpty() {
        assertTrue(fileQueryService.getEncryptedFileKeysBatch(null, "user1").isEmpty());
        assertTrue(fileQueryService.getEncryptedFileKeysBatch(Collections.emptyList(), "user1").isEmpty());
    }

    @Test
    @DisplayName("QRY-15: Retornar mapa de claves asociadas a ficheros")
    void getEncryptedFileKeysBatch_Success() {
        FileKeyEntity k = new FileKeyEntity();
        k.setFile(fileEntity);
        k.setEncryptedKey("encryptedsymkey101");

        when(fileKeyRepository.findByFileIdInAndUser_Username(List.of(101L), "user1")).thenReturn(List.of(k));

        // Lanzamos una búsqueda para obtener las claves simétricas cifradas correspondientes a la lista que indicamos (solo fichero 101 en este caso)
        Map<Long, String> result = fileQueryService.getEncryptedFileKeysBatch(List.of(101L), "user1");

        // Comprobamos que el resultado es un mapa asociando ID de fichero con su respectiva clave
        assertEquals(1, result.size());
        assertEquals("encryptedsymkey101", result.get(101L));
    }

    // ==========================================
    // 5. TEST: getFileDownloadStream()
    // ==========================================

    @Test
    @DisplayName("QRY-16: Denegar la descarga si el recurso es un directorio")
    void getFileDownloadStream_ThrowsInputValidationException_WhenResourceIsFolder() {
        fileEntity.setFileType("application/x-directory");
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.of(fileEntity));

        // Comprobamos que no se permite la descarga directa de un directorio, se hace a través de compresión en ZIP previa
        assertThrows(InputValidationException.class, () -> {
            fileQueryService.getFileDownloadStream(101L, "user1");
        });
    }

    @Test
    @DisplayName("QRY-17: Lanzar excepción de almacenamiento si no existe el fichero físico")
    void getFileDownloadStream_ThrowsInternalStorageException_WhenPhysicalFileMissing() {
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(storageUtils.exists("uploads/1/file101.enc")).thenReturn(false);

        // Borramos el fichero, pero al no estar en físico en el disco recibimos una excepción
        assertThrows(InternalStorageException.class, () -> {
            fileQueryService.getFileDownloadStream(101L, "user1");
        });
    }

    @Test
    @DisplayName("QRY-18: Descarga exitosa")
    void getFileDownloadStream_Success() throws IOException {
        InputStream mockStream = new ByteArrayInputStream("data".getBytes());
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(storageUtils.exists("uploads/1/file101.enc")).thenReturn(true);
        when(storageUtils.getRawStream("uploads/1/file101.enc")).thenReturn(mockStream);

        // Si existe el fichero en BD y en almacenamiento físico, devolvemos el stream de datos cifrados correctamente
        InputStream result = fileQueryService.getFileDownloadStream(101L, "user1");

        assertNotNull(result);
        verify(storageUtils).getRawStream("uploads/1/file101.enc");
    }

    @Test
    @DisplayName("QRY-19: Lanzamiento de InternalStorageException ante fallos de E/S del disco")
    void getFileDownloadStream_ThrowsInternalStorageException_OnIOException() throws IOException {
        when(fileRepository.findByIdAndHasAccess(101L, "user1")).thenReturn(Optional.of(fileEntity));
        when(storageUtils.exists("uploads/1/file101.enc")).thenReturn(true);
        when(storageUtils.getRawStream("uploads/1/file101.enc")).thenThrow(new IOException("Fallo físico"));

        // Comprobamos que se lanza una excepción si hay fallos de E/S al acceder al archivo físico
        assertThrows(InternalStorageException.class, () -> {
            fileQueryService.getFileDownloadStream(101L, "user1");
        });
    }

    // ==========================================
    // 6. TEST: checkExistsById() y searchFiles()
    // ==========================================

    @Test
    @DisplayName("QRY-20: Comprobar colisiones de nombres de archivos en ramas condicionales de carpetas")
    void checkExistsById_Branches() {
        when(fileRepository.findByOwner_UsernameAndFileNameAndParentIsNullAndDeletedAtIsNull("user1", "documento.pdf"))
                .thenReturn(Optional.of(fileEntity));
        Map<String, Object> resp1 = fileQueryService.checkExistsById("user1", "documento.pdf", null);
        assertTrue((Boolean) resp1.get("exists")); // Existe ya que el fichero está en la carpeta raíz y no dentro de otra

        when(fileRepository.findByOwner_UsernameAndFileNameAndParentIdAndDeletedAtIsNull("user1", "documento.pdf", 200L))
                .thenReturn(Optional.empty());
        Map<String, Object> resp2 = fileQueryService.checkExistsById("user1", "documento.pdf", 200L);
        assertFalse((Boolean) resp2.get("exists")); // Existe pero dentro de una carpeta, por tanto el fichero no está en la raíz
    }

    @Test
    @DisplayName("QRY-21: Búsquedas de ficheros")
    void searchFiles_Success() {
        // Con entrada vacía no hay resultados
        assertTrue(fileQueryService.searchFiles("user1", "   ", pageable).isEmpty());

        // Si buscamos pdf, encontraremos el documento "documento.pdf" de user1
        Page<FileEntity> page = new PageImpl<>(List.of(fileEntity));
        when(fileRepository.searchByName("user1", "pdf", pageable)).thenReturn(page);

        Page<FileDto> result = fileQueryService.searchFiles("user1", "  pdf  ", pageable);
        assertFalse(result.isEmpty());
    }

    // ==========================================
    // 7. TESTS: getRecursiveFilesForSharing()
    // ==========================================

    @Test
    @DisplayName("QRY-22: Validación de rutas en comparticiones recursivas")
    void getRecursiveFilesForSharing_Success_WithRootPathCheck() {
        // Creación del propietario para evitar NullPointerException en el service
        UserEntity owner = new com.cloudcrypt.model.UserEntity();
        owner.setUsername("user1");

        // Hacemos que el usuario user1 tenga una carpeta llamada "compartida"
        FileEntity folderRoot = new FileEntity();
        folderRoot.setId(201L);
        folderRoot.setFileName("compartida");
        folderRoot.setFolderPath("/");
        folderRoot.setOwner(owner); //  Asignamos el dueño al objeto simulado

        when(fileRepository.findByIdAndHasAccess(201L, "user1")).thenReturn(Optional.of(folderRoot));

        // Simulamos que al escanear la ruta "/compartida" hay un fichero dentro
        when(fileRepository.findAllByOwnerAndRecursivePathList("user1", "/compartida", 201L))
                .thenReturn(List.of(fileEntity));

        List<FileDto> result = fileQueryService.getRecursiveFilesForSharing(201L, "user1");

        // Nos aseguramos que el listado devuelto contiene el fichero
        assertFalse(result.isEmpty());
        verify(fileRepository).findAllByOwnerAndRecursivePathList("user1", "/compartida", 201L);
    }
}