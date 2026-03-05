package com.example.service;

import com.example.model.FileEntity;
import com.example.model.User;
import com.example.repository.FileRepository;
import com.example.repository.FileStorageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private FileStorageRepository fileStorageRepository;

    @Autowired
    private CryptoService cryptoService;

    /**
     * Sube y cifra un fichero usando la contraseña del dueño.
     */
    public FileEntity uploadFile(MultipartFile file, User owner, String rawPassword, String folderPath, String fileName) throws Exception {
        // 1. Verificación de Cuota (Lógica de Negocio) [cite: 380, 746]
        List<FileEntity> existingFiles = fileRepository.findByOwner(owner);
        long currentUsage = existingFiles.stream().mapToLong(FileEntity::getFileSize).sum();
        if (currentUsage + file.getSize() > 100 * 1024 * 1024) {
            throw new RuntimeException("Cuota excedida");
        }

        // 2. Cifrado (Seguridad en Capa de Gestión de API) [cite: 192, 753]
        byte[] encryptedContent = cryptoService.encrypt(file.getBytes(), rawPassword);

        // 3. Organización Jerárquica Física
        // Concatenamos el usuario con la ruta de carpeta deseada
        String physicalPath = owner.getUsername() + "/" + folderPath.replaceAll("^/|/$", "");
        String storageName = UUID.randomUUID().toString();

        // El repositorio de almacenamiento debe encargarse de crear los directorios si no existen
        fileStorageRepository.save(physicalPath, storageName, encryptedContent);

        // 4. Persistencia de Metadatos Correcta
        FileEntity entity = new FileEntity();
        // PRIORIDAD: Usamos el nombre final decidido en el controlador
        entity.setFileName(fileName);
        entity.setFolderPath(folderPath);
        entity.setFileType(file.getContentType());
        entity.setFileSize(file.getSize());
        // Guardamos la ruta física completa para poder localizarlo luego
        entity.setStoragePath(physicalPath + "/" + storageName);
        entity.setOwner(owner);

        return fileRepository.save(entity);
    }

    /**
     * Obtiene el contenido físico DESCRIFRADO.
     * ¡OJO! Ahora recibe el password para poder descifrar.
     */
    public byte[] getFileContent(Long fileId, String rawPassword) throws Exception {
        FileEntity entity = getFileById(fileId);

        // 1. Cargar los bytes cifrados del disco
        byte[] encryptedData = fileStorageRepository.load(entity.getStoragePath());

        // 2. Descifrar usando la contraseña proporcionada
        return cryptoService.decrypt(encryptedData, rawPassword);
    }

    public void deleteFile(Long id) throws Exception {
        FileEntity entity = fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fichero no encontrado"));

        // 1. Borrar físicamente el archivo cifrado
        fileStorageRepository.delete(entity.getStoragePath());

        // 2. Borrar metadatos de la BD
        fileRepository.delete(entity);
    }

    public List<FileEntity> getFilesByUser(User owner) {
        return fileRepository.findByOwner(owner);
    }

    public FileEntity getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Fichero no encontrado con ID: " + id));
    }

    public List<FileEntity> getAllFiles() {
        return fileRepository.findAll();
    }
}