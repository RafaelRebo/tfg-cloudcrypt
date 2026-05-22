package com.cloudcrypt.service.setup;

import com.cloudcrypt.config.ConfigPathResolver;
import com.cloudcrypt.dto.setup.SetupRequestDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.UUID;

@Service
public class SetupService {

    private static final Logger log = LoggerFactory.getLogger(SetupService.class);

    public void testDatabaseConnection(SetupRequestDto request) throws SQLException {
        String url = "jdbc:mysql://" + request.getDbHost() + ":" + request.getDbPort() + "/?serverTimezone=UTC";
        try (Connection conn = DriverManager.getConnection(url, request.getDbUser(), request.getDbPass())) {
            log.info("INSTALACIÓN: Conexión JDBC exitosa con el motor de base de datos.");
        }
    }

    public String storeAdminAvatar(String uploadDir, MultipartFile avatar) throws IOException {
        if (avatar == null || avatar.isEmpty()) {
            return "";
        }

        Path avatarDir = Paths.get(uploadDir, "avatars");
        if (!Files.exists(avatarDir)) {
            log.debug("INSTALACIÓN: Carpeta de avatares inexistente. Creando ruta: {}", avatarDir);
            Files.createDirectories(avatarDir);
        }

        String originalName = avatar.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".")) : ".png";

        String uniqueFilename = UUID.randomUUID().toString() + extension;
        Path targetPath = avatarDir.resolve(uniqueFilename);

        Files.copy(avatar.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        return "/static/avatars/" + uniqueFilename;
    }

    public void writeConfigurationProperties(SetupRequestDto request, String savedAvatarPath) throws IOException {
        File configDir = ConfigPathResolver.getConfigDir();
        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        File propertiesFile = ConfigPathResolver.getConfigFile();
        log.warn("INSTALACIÓN: Escribiendo archivo de configuración en: {}", propertiesFile.getAbsolutePath());

        try (BufferedWriter writer = Files.newBufferedWriter(propertiesFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write("# ARCHIVO GENERADO AUTOMÁTICAMENTE POR CLOUDCRYPT\n");

            writer.write("spring.datasource.url=jdbc:mysql://" + request.getDbHost() + ":" + request.getDbPort() + "/" + request.getDbName() + "?createDatabaseIfNotExist=true&serverTimezone=UTC\n");
            writer.write("spring.datasource.username=" + request.getDbUser() + "\n");
            writer.write("spring.datasource.password=" + request.getDbPass() + "\n");
            writer.write("spring.datasource.driverClassName=com.mysql.cj.jdbc.Driver\n");
            writer.write("spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect\n");

            String safePath = request.getUploadDir().replace("\\", "/");
            writer.write("app.storage.max-quota=" + request.getMaxQuotaBytes() + "\n");
            writer.write("spring.servlet.multipart.max-file-size=" + request.getMaxFileSizeGb() + "GB\n");
            writer.write("spring.servlet.multipart.max-request-size=" + request.getMaxFileSizeGb() + "GB\n");
            writer.write("app.storage.upload-dir=" + safePath + "\n");

            writer.write("app.crypto.hash-algorithm=" + request.getHashAlgo() + "\n");
            writer.write("app.crypto.symmetric-algorithm=" + request.getSymAlgo() + "\n");
            writer.write("app.crypto.asymmetric-key-size=" + request.getAsymKeySize() + "\n");

            byte[] jwtBytes = new byte[64];
            new java.security.SecureRandom().nextBytes(jwtBytes);
            String secureRandomJwtSecret = Base64.getEncoder().encodeToString(jwtBytes);
            writer.write("app.jwt.secret=" + secureRandomJwtSecret + "\n");
            writer.write("app.jwt.expiration-ms=7200000\n");
            writer.write("spring.jpa.properties.hibernate.default_batch_fetch_size=20\n");

            writer.write("app.setup.admin-username=" + request.getAdminUsername() + "\n");
            writer.write("app.setup.admin-password=" + request.getAdminPassword() + "\n");
            writer.write("app.setup.admin-fullname=" + request.getAdminFullName() + "\n");
            writer.write("app.setup.admin-email=" + request.getAdminEmail() + "\n");
            writer.write("app.setup.admin-avatar=" + savedAvatarPath + "\n");

            writer.write("logging.file.name=./config/logs/cloudcrypt.log\n");
            writer.write("logging.logback.rollingpolicy.max-file-size=10MB\n");
            writer.write("logging.logback.rollingpolicy.max-history=7\n");

            writer.write("springdoc.api-docs.path=/api-docs\n");
            writer.write("springdoc.swagger-ui.path=/swagger-ui.html\n");

            writer.flush();
            log.info("INSTALACIÓN: Archivo properties generado.");
        }
    }
}