package com.example.repository.storage;

import javax.crypto.Cipher;
import java.io.IOException;
import java.io.InputStream;

public interface IStorageRepository {
    void save(InputStream input, String folder, String filename, Cipher cipher) throws IOException;
    void delete(String storagePath) throws IOException;
    InputStream loadDecryptedStream(String relativePath, Cipher cipher) throws IOException;
    boolean exists(String storagePath);
}