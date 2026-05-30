package com.cloudcrypt.controller.setup;

import com.cloudcrypt.config.ConfigPathResolver;
import com.cloudcrypt.dto.setup.SetupRequestDto;
import com.cloudcrypt.service.setup.SetupService;
import com.cloudcrypt.config.CryptoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final SetupService setupService;
    private final CryptoConfig cryptoConfig;

    public SetupController(SetupService setupService, CryptoConfig cryptoConfig) {
        this.setupService = setupService;
        this.cryptoConfig = cryptoConfig;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> checkStatus() {
        File prodConfig = ConfigPathResolver.getConfigFile();
        boolean isInstalled = prodConfig.exists();
        log.debug("INSTALACIÓN: Verificando estado de instalación. ¿Detectado properties?: {}", isInstalled);
        Map<String, Boolean> response = new HashMap<>();
        response.put("installed", isInstalled);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-db")
    public ResponseEntity<String> testDatabaseConnection(@RequestBody SetupRequestDto request) {
        log.info("INSTALACIÓN: Verificando conexión JDBC con [{}:{}]", request.getDbHost(), request.getDbPort());
        try {
            setupService.testDatabaseConnection(request);
            return ResponseEntity.ok("Conexión con el motor MySQL establecida con éxito.");
        } catch (SQLException e) {
            log.error("INSTALACIÓN: Error al conectar con la base de datos: {}", e.getMessage());
            return ResponseEntity.status(400).body("No se pudo conectar a MySQL: Verifica los parámetros.");
        }
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> finalizeInstallation(
            @ModelAttribute SetupRequestDto request,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {

        log.warn("INSTALACIÓN: Validando entorno antes de inicializar cuenta de administrador.");

        try {
            setupService.testDatabaseConnection(request);

            setupService.assertAndCreateStorageDirectory(request.getUploadDir());

            log.info("INSTALACIÓN: Entorno verificado. Inicializando cuenta @{}.", request.getAdminUsername());
            String savedAvatarPath = setupService.storeAdminAvatar(request.getUploadDir(), avatar);
            setupService.writeConfigurationProperties(request, savedAvatarPath);

            executeAsynchronousShutdown();
            return ResponseEntity.ok("Configuración guardada con éxito. Reiniciando...");

        } catch (SQLException e) {
            log.error("INSTALACIÓN ABORTADA: Parámetros de base de datos no válidos: {}", e.getMessage());
            return ResponseEntity.status(400).body("Error: No se puede finalizar la instalación si la conexión con MySQL falla.");
        } catch (IOException e) {
            log.error("INSTALACIÓN ABORTADA: Error de almacenamiento: {}", e.getMessage());
            return ResponseEntity.status(400).body("Error de Almacenamiento: " + e.getMessage());
        }
    }

    @GetMapping("/crypto-specs")
    public ResponseEntity<Map<String, Object>> getLiveCryptoSpecs() {
        log.debug("INSTALACIÓN: Guardando parámetros de configuración criptográfica");
        Map<String, Object> specs = new HashMap<>();
        specs.put("hashAlgo", cryptoConfig.getHashAlgorithm());
        specs.put("symAlgo", cryptoConfig.getSymmetricAlgorithm());
        specs.put("asymKeySize", cryptoConfig.getAsymmetricKeySize());
        return ResponseEntity.ok(specs);
    }

    private void executeAsynchronousShutdown() {
        new Thread(() -> {
            try {
                log.warn("INSTALACIÓN: Reiniciando sistema...");
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
    }
}