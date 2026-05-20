package com.cloudcrypt.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ConfigPathResolver {

    private static final String DEFAULT_CONFIG_DIR = "./config";
    private static final String CONFIG_FILE_NAME = "application-prod.properties";

    public static File getConfigFile() {
        String configDir = System.getProperty("app.config.dir", DEFAULT_CONFIG_DIR);
        Path absolutePath = Paths.get(configDir).resolve(CONFIG_FILE_NAME).toAbsolutePath().normalize();
        return absolutePath.toFile();
    }

    public static File getConfigDir() {
        String configDir = System.getProperty("app.config.dir", DEFAULT_CONFIG_DIR);
        return Paths.get(configDir).toAbsolutePath().normalize().toFile();
    }
}
