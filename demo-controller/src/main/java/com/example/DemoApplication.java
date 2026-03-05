package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example") // Escanea Controllers y Services
@EnableJpaRepositories(basePackages = "com.example.repository") // Escanea Repositorios
@EntityScan(basePackages = "com.example.model") // Escanea Entidades (User, FileEntity)
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}