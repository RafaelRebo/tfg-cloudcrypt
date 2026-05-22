package com.cloudcrypt;

import com.cloudcrypt.config.ConfigPathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(CloudCryptApplication.class);

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CloudCryptApplication.class);

        File prodConfig = ConfigPathResolver.getConfigFile();
        if (prodConfig.exists()) {
            System.setProperty("spring.config.additional-location", "file:" + prodConfig.getAbsolutePath());
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
                admin.setRole("ADMIN");
                String dynamicAdminSalt = java.util.UUID.randomUUID().toString().replace("-", "");
                admin.setSalt(dynamicAdminSalt);

                try {
                    String clientSideDerivedKey = javaDeriveMasterKey(adminUser, adminPass, hashAlgo);
                    String secureBcryptInput = internalSha256(clientSideDerivedKey);
                    admin.setPassword(passwordEncoder.encode(secureBcryptInput));

                    userRepository.save(admin);
                } catch (Exception e) {
                    log.error("SETUP: No se pudo procesar la creación de la cuenta de administrador.", e);
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