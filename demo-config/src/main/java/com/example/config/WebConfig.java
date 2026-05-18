package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolutePath = Paths.get("uploads/avatars").toAbsolutePath().toUri().toString();

        // Mapeamos la ruta virtual a la ruta física real del disco
        registry.addResourceHandler("/static/avatars/**")
                .addResourceLocations(absolutePath);
    }
}