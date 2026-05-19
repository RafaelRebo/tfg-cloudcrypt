package com.cloudcrypt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CryptoConfig {

    @Value("${app.crypto.hash-algorithm:SHA-256}")
    private String hashAlgorithm;

    @Value("${app.crypto.symmetric-algorithm:AES/GCM/NoPadding}")
    private String symmetricAlgorithm;

    @Value("${app.crypto.asymmetric-key-size:2048}")
    private int asymmetricKeySize;

    @Value("${app.crypto.salt-suffix:-cloudcrypt}")
    private String saltSuffix;

    public String getHashAlgorithm() { return hashAlgorithm; }
    public String getSymmetricAlgorithm() { return symmetricAlgorithm; }
    public int getAsymmetricKeySize() { return asymmetricKeySize; }

    public String getSaltSuffix() {
        return saltSuffix;
    }
}