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

    /**
     * Guarda el archivo cifrado usando una clave AES única proporcionada.
     * Ya no depende de la contraseña del usuario.
     */
    public Map<String, String> saveEncryptedPackage(InputStream is, String username, String folderPath) throws Exception {
        // 1. Calcular Checksum mientras guardamos (para integridad del paquete cifrado)
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        DigestInputStream dis = new DigestInputStream(is, md);

        // 2. Definir rutas
        String physicalFolder = username + "/" + folderPath.replaceAll("^/|/$", "");
        String storageName = UUID.randomUUID().toString();

        // 3. GUARDADO DIRECTO: El Cipher es null porque el archivo YA viene cifrado del navegador
        storageRepository.save(dis, physicalFolder, storageName, null);

        Map<String, String> results = new HashMap<>();
        results.put("storagePath", physicalFolder + "/" + storageName);
        results.put("checksum", bytesToHex(md.digest()));
        return results;
    }
    /**
     * Carga el stream descifrado usando la clave AES del archivo.
     */
    public InputStream getRawStream(String storagePath) throws IOException {
        return storageRepository.loadStream(storagePath);
    }

    // Métodos auxiliares se mantienen igual
    public void deletePhysicalFile(String storagePath) throws IOException {
        storageRepository.delete(storagePath);
    }

    public boolean exists(String storagePath) {
        return storageRepository.exists(storagePath);
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
}