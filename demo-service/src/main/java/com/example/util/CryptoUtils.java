package com.example.util;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Service
public class CryptoUtils {
    private static final String ALGORITHM = "AES";

    private SecretKeySpec deriveKey(String password) throws Exception {
        byte[] key = password.getBytes(StandardCharsets.UTF_8);
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        key = sha.digest(key);
        key = Arrays.copyOf(key, 16);
        return new SecretKeySpec(key, ALGORITHM);
    }

    public Cipher getCipher(int mode, String password) throws Exception {
        Cipher c = Cipher.getInstance(ALGORITHM);
        c.init(mode, deriveKey(password));
        return c;
    }
}