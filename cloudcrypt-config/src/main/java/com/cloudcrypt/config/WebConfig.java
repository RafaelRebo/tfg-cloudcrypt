package com.cloudcrypt.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Paths;

// Clase que permite mapear los avatares al directorio físico del almacenamiento donde se encuentran
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get(uploadDir).toAbsolutePath().normalize().toString().replace("\\", "/");

        registry.addResourceHandler("/static/avatars/**")
                .addResourceLocations("file:" + absolutePath + "/avatars/");
    }
}