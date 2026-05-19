package com.cloudcrypt.controller.setup;

import com.cloudcrypt.dto.setup.SetupRequestDto;
import com.cloudcrypt.service.setup.SetupService;
import com.cloudcrypt.config.CryptoConfig;
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

    private final SetupService setupService;
    private final CryptoConfig cryptoConfig;

    public SetupController(SetupService setupService, CryptoConfig cryptoConfig) {
        this.setupService = setupService;
        this.cryptoConfig = cryptoConfig;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> checkStatus() {
        File prodConfig = new File("./config/application-prod.properties");
        Map<String, Boolean> response = new HashMap<>();
        response.put("installed", prodConfig.exists());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/test-db")
    public ResponseEntity<String> testDatabaseConnection(@RequestBody SetupRequestDto request) {
        try {
            setupService.testDatabaseConnection(request);
            return ResponseEntity.ok("Conexión con el motor MySQL establecida con éxito.");
        } catch (SQLException e) {
            return ResponseEntity.badRequest().body("Error de infraestructura: " + e.getMessage());
        }
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> finalizeInstallation(
            @ModelAttribute SetupRequestDto request,
            @RequestParam(value = "avatar", required = false) MultipartFile avatar) {

        try {
            String savedAvatarPath = setupService.storeAdminAvatar(request.getUploadDir(), avatar);
            setupService.writeConfigurationProperties(request, savedAvatarPath);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Fallo de I/O en el aprovisionamiento de configuración: " + e.getMessage());
        }

        executeAsynchronousShutdown();

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

    private void executeAsynchronousShutdown() {
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            System.exit(0);
        }).start();
    }
}