package com.cloudcrypt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageConfig {

    private long maxQuota;
    private String uploadDir;

    public long getMaxQuota() {
        return this.maxQuota;
    }

    public void setMaxQuota(long maxQuota) {
        this.maxQuota = maxQuota;
    }

    public String getUploadDir() {
        return this.uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}