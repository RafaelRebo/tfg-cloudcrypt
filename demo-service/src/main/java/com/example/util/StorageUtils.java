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
        // 1. Preparar el cálculo de integridad (SHA-256)
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        // 2. Definir estructura de almacenamiento físico
        // Limpiamos la ruta para evitar problemas de dobles slashes o caracteres prohibidos
        String sanitizedFolder = folderPath.replaceAll("^/|/$", "");
        String physicalFolder = username + "/" + sanitizedFolder;
        String storageName = UUID.randomUUID().toString();

        // 3. FLUJO DE TRANSFERENCIA:
        // DigestInputStream actúa como un "peaje": el archivo pasa a través de él,
        // se calcula el hash al vuelo y se entrega al storageRepository.
        try (DigestInputStream dis = new DigestInputStream(is, md)) {
            storageRepository.save(dis, physicalFolder, storageName);
        }

        // 4. Generar respuesta con metadatos del archivo físico
        Map<String, String> results = new HashMap<>();
        results.put("storagePath", physicalFolder + "/" + storageName);
        results.put("checksum", bytesToHex(md.digest())); // md.digest() ya tiene el hash calculado

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