package com.example.repository.storage;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;

// En IStorageRepository.java
public interface IStorageRepository {
    void save(InputStream input, String folder, String filename, Cipher cipher) throws IOException;
    void delete(String storagePath) throws IOException;
    // CAMBIO: Ya no pedimos Cipher aquí
    InputStream loadStream(String relativePath) throws IOException;
    boolean exists(String storagePath);
}