package com.cloudcrypt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.cloudcrypt.model.UserEntity;
import com.cloudcrypt.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@SpringBootApplication(scanBasePackages = "com.cloudcrypt")
@EnableJpaRepositories(basePackages = "com.cloudcrypt.repository")
@EntityScan(basePackages = "com.cloudcrypt.model")
public class CloudCryptApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CloudCryptApplication.class);
        File prodConfig = new File("./config/application-prod.properties");
        if (prodConfig.exists()) {
            System.setProperty("spring.config.additional-location", "file:./config/application-prod.properties");
        }
        app.run(args);
    }

    @Bean
    CommandLineRunner initAdminAccount(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.setup.admin-username:}") String adminUser,
            @Value("${app.setup.admin-password:}") String adminPass,
            @Value("${app.setup.admin-fullname:}") String adminName,
            @Value("${app.setup.admin-email:}") String adminEmail,
            @Value("${app.setup.admin-avatar:}") String adminAvatar,
            @Value("${app.crypto.hash-algorithm:SHA-256}") String hashAlgo) {

        return args -> {
            if (!adminUser.isEmpty() && userRepository.count() == 0) {
                UserEntity admin = new UserEntity();
                admin.setUsername(adminUser);
                admin.setFullName(adminName);
                admin.setEmail(adminEmail);
                admin.setAvatarUrl(adminAvatar.isEmpty() ? null : adminAvatar);

                try {
                    String clientSideDerivedKey = javaDeriveMasterKey(adminUser, adminPass, hashAlgo);

                    // ⚡ SOLUCIÓN: Pre-hasheamos el resultado largo de SHA-512 antes de mandarlo a BCrypt
                    String secureBcryptInput = internalSha256(clientSideDerivedKey);
                    admin.setPassword(passwordEncoder.encode(secureBcryptInput));

                    userRepository.save(admin);
                    System.out.println("=================================================");
                    System.out.println("🛡️ CONFIGURACIÓN DE SEGURIDAD: Cuenta de Administrador Zero-Knowledge creada.");
                    System.out.println("=================================================");
                } catch (Exception e) {
                    System.err.println("ERROR CRÍTICO al derivar la clave del admin: " + e.getMessage());
                }
            }
        };
    }

    private String javaDeriveMasterKey(String username, String password, String algorithm) throws Exception {
        String input = username.toLowerCase() + password;
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Helper local para el pre-hash del admin
     */
    private String internalSha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}