package com.cloudcrypt.util;

import com.cloudcrypt.config.CryptoConfig;
import com.cloudcrypt.repository.storage.IStorageRepository;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageUtils {

    private final IStorageRepository storageRepository;
    private final CryptoConfig cryptoConfig;

    public StorageUtils(IStorageRepository storageRepository, CryptoConfig cryptoConfig) {
        this.storageRepository = storageRepository;
        this.cryptoConfig = cryptoConfig;
    }

    public Map<String, String> saveEncryptedPackage(InputStream is, Long userId, String folderPath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(cryptoConfig.getHashAlgorithm());

        String sanitizedFolder = folderPath.replaceAll("^/|/$", "");
        String physicalFolder = sanitizedFolder.isEmpty() ? String.valueOf(userId) : userId + "/" + sanitizedFolder;
        String storageName = UUID.randomUUID().toString();

        try (DigestInputStream dis = new DigestInputStream(is, md)) {
            storageRepository.save(dis, physicalFolder, storageName);
        }

        Map<String, String> results = new HashMap<>();
        results.put("storagePath", physicalFolder + "/" + storageName);
        results.put("checksum", bytesToHex(md.digest()));

        return results;
    }

    public InputStream getRawStream(String storagePath) throws IOException {
        return storageRepository.loadStream(storagePath);
    }

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