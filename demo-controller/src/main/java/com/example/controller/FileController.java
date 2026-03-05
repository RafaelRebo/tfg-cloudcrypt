package com.example.controller;

import com.example.model.FileEntity;
import com.example.model.User;
import com.example.repository.UserRepository;
import com.example.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Capa Servicios: Expone una API REST para la gestión de ficheros[cite: 27].
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserRepository userRepository;

    // 1. ENDPOINT DE REGISTRO
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestParam("username") String username,
                                           @RequestParam("password") String password) {
        if (userRepository.findByUsername(username) != null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El usuario ya existe");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        userRepository.save(user);
        return ResponseEntity.ok("Usuario registrado con éxito");
    }

    // 2. ENDPOINT DE LOGIN (Para verificar antes de entrar)
    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestParam("username") String username,
                                      @RequestParam("password") String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && user.getPassword().equals(password)) {
            return ResponseEntity.ok(user);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    /**
     * Endpoint para subir ficheros al "Servidor de Almacenamiento"[cite: 34].
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "folderPath", defaultValue = "/") String folderPath, // Nuevo
            @RequestParam(value = "fileName", required = false) String fileName) {    // Nuevo
        try {
            User user = userRepository.findByUsername(username);
            if (user == null || !user.getPassword().equals(password)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            FileEntity savedFile = fileService.uploadFile(file, user, password, folderPath, fileName);

            // 1. Construimos la URI del nuevo recurso para la cabecera Location
            URI location = ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/api/files/download/{id}")
                    .buildAndExpand(savedFile.getId())
                    .toUri();

            // 2. Devolvemos 201 Created con el objeto y la localización
            return ResponseEntity.created(location).body(savedFile);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Endpoint para listar todos los ficheros registrados en la base de datos[cite: 34].
     */
    @GetMapping
    public ResponseEntity<List<FileEntity>> listFiles(
            @RequestParam String username,
            @RequestParam(defaultValue = "/") String folder) {

        User user = userRepository.findByUsername(username);
        // Obtenemos todos los archivos del usuario desde la BD
        List<FileEntity> allFiles = fileService.getFilesByUser(user);



        // Filtramos: solo queremos los archivos que estén EXACTAMENTE en 'folder'
        List<FileEntity> filtered = allFiles.stream()
                .filter(f -> {
                    String path = f.getFolderPath();
                    if (path == null) path = "/";
                    return path.equals(folder);
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(filtered);
    }

    /**
     * Endpoint para descargar un fichero físico mediante su ID.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @RequestParam("password") String password) { // Recibimos el password por parámetro
        try {
            FileEntity entity = fileService.getFileById(id);

            // Pasamos el password al servicio
            byte[] content = fileService.getFileContent(id, password);

            ByteArrayResource resource = new ByteArrayResource(content);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(entity.getFileType()))
                    .body(resource);
        } catch (Exception e) {
            // Si la contraseña es incorrecta, el descifrado fallará y entrará aquí
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(@RequestParam String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) return ResponseEntity.notFound().build();

        List<FileEntity> files = fileService.getFilesByUser(user);

        long totalSize = files.stream().mapToLong(FileEntity::getFileSize).sum();
        int fileCount = files.size();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", fileCount);
        stats.put("maxQuota", 100 * 1024 * 1024); // Ejemplo: Límite de 100MB

        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id) {
        try {
            fileService.deleteFile(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


}