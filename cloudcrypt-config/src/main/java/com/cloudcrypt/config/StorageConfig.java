package com.cloudcrypt.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "app.storage")
public class StorageConfig {
    private long maxQuota;
    private String uploadDir;
}