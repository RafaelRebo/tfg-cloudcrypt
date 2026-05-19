package com.cloudcrypt.util;

import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;
import java.security.SecureRandom;

@Component
public class CryptoUtils {
    private static final String ALGORITHM = "AES";
    // Usamos GCM por ser un modo de cifrado autenticado (incluye integridad)
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int GCM_IV_LENGTH = 12; // Longitud recomendada para IV en GCM
    private static final int GCM_TAG_LENGTH = 128;

    public byte[] generateRandomSalt() {
        byte[] salt = new byte[GCM_IV_LENGTH]; // En GCM, el salt actúa como IV
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    public Cipher getReadyCipherWithRawKey(int mode, byte[] rawKey, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecretKeySpec keySpec = new SecretKeySpec(rawKey, ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(mode, keySpec, gcmSpec);
        return cipher;
    }

    // Mantenemos este método para la encriptación de la clave privada del usuario (Fase 1)
    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance(DERIVATION_ALGORITHM);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), ALGORITHM);
    }

    public Cipher getReadyCipher(int mode, String password, byte[] salt) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, salt);
        cipher.init(mode, deriveKey(password, salt), gcmSpec);
        return cipher;
    }
}