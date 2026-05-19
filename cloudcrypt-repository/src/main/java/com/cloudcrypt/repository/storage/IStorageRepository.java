package com.cloudcrypt.repository.storage;

import java.io.IOException;
import java.io.InputStream;


public interface IStorageRepository {
    void save(InputStream input, String folder, String filename) throws IOException;
    void delete(String storagePath) throws IOException;
    InputStream loadStream(String relativePath) throws IOException;
    boolean exists(String storagePath);
}