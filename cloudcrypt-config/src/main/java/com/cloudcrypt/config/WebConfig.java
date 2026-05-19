package com.cloudcrypt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Resolvemos la ruta absoluta real elegida por el administrador en el disco
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString().replace("\\", "/");

        // ⚡ MAPEO MAESTRO: Enlazamos la URL web con la carpeta física de avatares del disco real
        registry.addResourceHandler("/static/avatars/**")
                .addResourceLocations("file:" + absolutePath + "/avatars/");
    }
}