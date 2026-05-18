package com.example.controller.setup;

import com.example.dto.setup.SetupRequestDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    @Autowired
    private com.example.config.CryptoConfig cryptoConfig;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> checkStatus() {
        File prodConfig = new File("./config/application-prod.properties");
        Map<String, Boolean> response = new HashMap<>();
        response.put("installed", prodConfig.exists());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-db")
    public ResponseEntity<String> testDatabaseConnection(@RequestBody SetupRequestDto request) {
        // Formamos la URL de testeo apuntando al motor raíz de MySQL
        String url = "jdbc:mysql://" + request.getDbHost() + ":" + request.getDbPort() + "/?serverTimezone=UTC";

        try (Connection conn = DriverManager.getConnection(url, request.getDbUser(), request.getDbPass())) {
            return ResponseEntity.ok("Conexión con el motor MySQL establecida con éxito.");
        } catch (SQLException e) {
            return ResponseEntity.badRequest().body("Error de infraestructura: " + e.getMessage());
        }
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> finalizeInstallation(
            @ModelAttribute SetupRequestDto request, // ⚡ SOLUCIÓN: Mapea los campos del formulario multipart
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) { // ⚡ Captura el binario de la imagen

        File configDir = new File("./config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File propertiesFile = new File(configDir, "application-prod.properties");

        // 🛡️ PROCESAMIENTO INMEDIATO DEL AVATAR DEL ADMIN
        String savedAvatarPath = "";
        if (avatar != null && !avatar.isEmpty()) {
            try {
                // Construimos la ruta basándonos en el uploadDir elegido por el administrador
                Path avatarDir = Paths.get(request.getUploadDir(), "avatars");
                if (!Files.exists(avatarDir)) {
                    Files.createDirectories(avatarDir);
                }

                String originalName = avatar.getOriginalFilename();
                String extension = originalName != null && originalName.contains(".")
                        ? originalName.substring(originalName.lastIndexOf(".")) : ".png";

                String uniqueFilename = UUID.randomUUID().toString() + extension;
                Path targetPath = avatarDir.resolve(uniqueFilename);

                Files.copy(avatar.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                savedAvatarPath = "/static/avatars/" + uniqueFilename; // URI relativa para la BD

            } catch (IOException e) {
                return ResponseEntity.internalServerError().body("Error al guardar físicamente la foto del admin: " + e.getMessage());
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(propertiesFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("# ARCHIVO GENERADO DINÁMICAMENTE POR CLOUDCRYPT\n");

            // Base de Datos MySQL
            writer.write("spring.datasource.url=jdbc:mysql://" + request.getDbHost() + ":" + request.getDbPort() + "/" + request.getDbName() + "?createDatabaseIfNotExist=true&serverTimezone=UTC\n");
            writer.write("spring.datasource.username=" + request.getDbUser() + "\n");
            writer.write("spring.datasource.password=" + request.getDbPass() + "\n");
            writer.write("spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver\n");
            writer.write("spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect\n");

            // Cuotas y almacenamiento (Limpiando contrabarras de Windows)
            String safePath = request.getUploadDir().replace("\\", "/");
            writer.write("app.storage.max-quota=" + request.getMaxQuotaBytes() + "\n");
            writer.write("spring.servlet.multipart.max-file-size=" + request.getMaxFileSizeGb() + "GB\n");
            writer.write("spring.servlet.multipart.max-request-size=" + request.getMaxFileSizeGb() + "GB\n");
            writer.write("app.storage.upload-dir=" + safePath + "\n");

            // Gobernanza Criptográfica
            writer.write("app.crypto.hash-algorithm=" + request.getHashAlgo() + "\n");
            writer.write("app.crypto.symmetric-algorithm=" + request.getSymAlgo() + "\n");
            writer.write("app.crypto.asymmetric-key-size=" + request.getAsymKeySize() + "\n");
            writer.write("app.crypto.salt-suffix=" + request.getSaltSuffix() + "\n");

            // Servidor JWT Persistente
            byte[] jwtBytes = new byte[64];
            new java.security.SecureRandom().nextBytes(jwtBytes);
            String secureRandomJwtSecret = java.util.Base64.getEncoder().encodeToString(jwtBytes);
            writer.write("app.jwt.secret=" + secureRandomJwtSecret + "\n");
            writer.write("app.jwt.expiration-ms=7200000\n");
            writer.write("spring.jpa.properties.hibernate.default_batch_fetch_size=20\n");

            // Parámetros de inicialización de la cuenta maestra
            writer.write("app.setup.admin-username=" + request.getAdminUsername() + "\n");
            writer.write("app.setup.admin-password=" + request.getAdminPassword() + "\n");
            writer.write("app.setup.admin-fullname=" + request.getAdminFullName() + "\n");
            writer.write("app.setup.admin-email=" + request.getAdminEmail() + "\n");
            writer.write("app.setup.admin-avatar=" + savedAvatarPath + "\n");

            writer.flush();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error al escribir la configuración: " + e.getMessage());
        }

        // Hilo de reinicio controlado...
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();

        return ResponseEntity.ok("Configuración guardada con éxito. Reiniciando el ecosistema criptográfico...");
    }


    @GetMapping("/crypto-specs")
    public ResponseEntity<Map<String, Object>> getLiveCryptoSpecs() {
        Map<String, Object> specs = new HashMap<>();
        specs.put("hashAlgo", cryptoConfig.getHashAlgorithm());
        specs.put("symAlgo", cryptoConfig.getSymmetricAlgorithm());
        specs.put("asymKeySize", cryptoConfig.getAsymmetricKeySize());
        specs.put("saltSuffix", cryptoConfig.getSaltSuffix());
        return ResponseEntity.ok(specs);
    }
}