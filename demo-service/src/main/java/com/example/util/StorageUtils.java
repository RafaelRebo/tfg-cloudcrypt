package com.example.util;

import com.example.repository.storage.IStorageRepository;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageUtils {

    private final IStorageRepository storageRepository;
    private final CryptoUtils cryptoUtils;

    public StorageUtils(IStorageRepository storageRepository, CryptoUtils cryptoUtils) {
        this.storageRepository = storageRepository;
        this.cryptoUtils = cryptoUtils;
    }

    public Map<String, String> encryptAndSave(InputStream is, String username, String folderPath, String rawPassword) throws Exception {
        // Preparar el algoritmo de Hash
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        // Envolver el stream original: cada byte leído pasará por el MessageDigest
        DigestInputStream dis = new DigestInputStream(is, md);

        byte[] salt = cryptoUtils.generateRandomSalt();
        String saltBase64 = Base64.getEncoder().encodeToString(salt);

        String physicalFolder = username + "/" + folderPath.replaceAll("^/|/$", "");
        String storageName = UUID.randomUUID().toString();
        String finalStoragePath = physicalFolder + "/" + storageName;

        Cipher cipher = cryptoUtils.getReadyCipher(Cipher.ENCRYPT_MODE, rawPassword, salt);

        // Guardamos usando el DigestInputStream
        storageRepository.save(dis, physicalFolder, storageName, cipher);

        // Una vez que el stream se ha agotado (guardado), extraemos el hash calculado
        String checksum = bytesToHex(md.digest());

        Map<String, String> results = new HashMap<>();
        results.put("storagePath", finalStoragePath);
        results.put("salt", saltBase64);
        results.put("checksum", checksum); // Retornamos el checksum calculado en una sola pasada
        return results;
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }


    public InputStream getDecryptedStream(String storagePath, String rawPassword, String saltBase64) throws Exception {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        Cipher decryptCipher = cryptoUtils.getReadyCipher(Cipher.DECRYPT_MODE, rawPassword, salt);
        return storageRepository.loadDecryptedStream(storagePath, decryptCipher);
    }

    public void deletePhysicalFile(String storagePath) throws IOException {
        storageRepository.delete(storagePath);
    }

    public boolean exists(String storagePath) {
        return storageRepository.exists(storagePath);
    }
}