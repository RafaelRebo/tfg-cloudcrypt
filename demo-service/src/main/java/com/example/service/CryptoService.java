package com.example.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Service
public class CryptoService {
    private static final String ALGORITHM = "AES";

    // Genera una clave AES de 16 bytes a partir de cualquier contraseña
    private SecretKeySpec deriveKey(String password) throws Exception {
        byte[] key = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16); // Usamos los primeros 16 bytes para AES-128
        return new SecretKeySpec(key, ALGORITHM);
    }

    public byte[] encrypt(byte[] data, String password) throws Exception {
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(Cipher.ENCRYPT_MODE, deriveKey(password));
        return c.doFinal(data);
    }

    public byte[] decrypt(byte[] data, String password) throws Exception {
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(Cipher.DECRYPT_MODE, deriveKey(password));
        return c.doFinal(data);
    }
}