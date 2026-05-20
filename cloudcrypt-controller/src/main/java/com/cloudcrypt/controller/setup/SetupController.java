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
    public ResponseEntity<String> testDatabaseConnection(@RequestBody SetupRequestDto request) throws java.sql.SQLException {
        log.info("INSTALACIÓN: Verificando conexión JDBC con [{}:{}]", request.getDbHost(), request.getDbPort());
        setupService.testDatabaseConnection(request);
        return ResponseEntity.ok("Conexión con el motor MySQL establecida con éxito.");
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> finalizeInstallation(
            @ModelAttribute SetupRequestDto request,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) throws java.io.IOException {

        log.warn("INSTALACIÓN: Inicializando cuenta de administrador @{}.", request.getAdminUsername());
        String savedAvatarPath = setupService.storeAdminAvatar(request.getUploadDir(), avatar);
        setupService.writeConfigurationProperties(request, savedAvatarPath);

        executeAsynchronousShutdown();
        return ResponseEntity.ok("Configuración guardada con éxito. Reiniciando el ecosistema criptográfico...");
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