package com.example.controller;

import com.example.model.FileEntity;
import com.example.model.UserEntity;
import com.example.service.FileService;
import com.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @Autowired
    private UserService userService;

    @Value("${app.max-quota:104857600}")
    private long maxQuota;

    /**
     * Sube un archivo mediante Streaming.
     * El archivo se cifra y se calcula el SHA-256 al vuelo sin cargar los bytes en RAM.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            @RequestParam(value = "folderPath", defaultValue = "/") String folderPath,
            @RequestParam(value = "fileName", required = false) String fileName) {
        try {
            UserEntity user = userService.findEntityByUsername(username);

            if (user == null || !userService.checkPassword(password, user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
            }

            // El service ahora maneja InputStreams internamente
            FileEntity savedFile = fileService.uploadFile(file, user, password, folderPath, fileName);
            return ResponseEntity.ok(savedFile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lista archivos filtrados por carpeta o todos (según el parámetro 'all').
     */
    @GetMapping
    public ResponseEntity<List<FileEntity>> listFiles(
            @RequestParam String username,
            @RequestParam(defaultValue = "/") String folder,
            @RequestParam(value = "all", defaultValue = "false") boolean all) {

        UserEntity user = userService.findEntityByUsername(username);
        if (user == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        List<FileEntity> filteredFiles = fileService.getFilesByFolder(user, folder, all);
        return ResponseEntity.ok(filteredFiles);
    }

    /**
     * Descarga de archivos mediante Streaming.
     * Usa InputStreamResource para que el servidor envíe trozos del archivo mientras lo descifra.
     */
    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long id,
            @RequestHeader("X-File-Password") String password) {
        try {
            FileEntity entity = fileService.getFileById(id);

            // Obtenemos el CipherInputStream del servicio
            InputStream stream = fileService.getFileDownloadStream(id, password);

            // Envolvemos el stream en un InputStreamResource para el body de Spring
            InputStreamResource resource = new InputStreamResource(stream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(entity.getFileType()))
                    .body(resource);
        } catch (Exception e) {
            // Error de integridad o contraseña incorrecta
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Devuelve estadísticas de uso de espacio consumido por el usuario.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getUserStats(@RequestParam String username) {
        UserEntity user = userService.findEntityByUsername(username);
        if (user == null) return ResponseEntity.notFound().build();

        List<FileEntity> files = fileService.getFilesByUser(user);
        long totalSize = files.stream().mapToLong(FileEntity::getFileSize).sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSize", totalSize);
        stats.put("fileCount", files.size());
        stats.put("maxQuota", maxQuota);

        return ResponseEntity.ok(stats);
    }

    /**
     * Elimina los metadatos y el archivo físico cifrado.
     */
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