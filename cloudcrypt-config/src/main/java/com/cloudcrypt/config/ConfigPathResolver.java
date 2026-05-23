package com.cloudcrypt.config;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

// Clase que permite resolver dinámicamente la ruta del fichero de configuración independientemente de la ruta absoluta de instalación
public class ConfigPathResolver {

    private static final String DEFAULT_CONFIG_DIR = "./config";
    private static final String CONFIG_FILE_NAME = "application-prod.properties";

    // Permite obtener la ruta absoluta del fichero .properties de la app
    public static File getConfigFile() {
        String configDir = System.getProperty("app.config.dir", DEFAULT_CONFIG_DIR);
        Path absolutePath = Paths.get(configDir).resolve(CONFIG_FILE_NAME).toAbsolutePath().normalize();
        return absolutePath.toFile();
    }

    // Permite obtener la ruta absoluta del directorio "config" donde se ubican los ficheros de configuración
    public static File getConfigDir() {
        String configDir = System.getProperty("app.config.dir", DEFAULT_CONFIG_DIR);
        return Paths.get(configDir).toAbsolutePath().normalize().toFile();
    }
}
