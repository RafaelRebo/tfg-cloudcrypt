package com.cloudcrypt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Configura la seguridad web global de la app y controla los accesos a sus diferentes endpoints
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Define el codificador BCrypt usando para almacenar las contraseñas en la base de datos
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Define las rutas autorizadas públicamente y las que necesitan autorización expresa
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtRequestFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/api/setup/**", "/setup.html", "/index.html", "/css/**", "/img/**", "/js/**", "/static/avatars/**", "/favicon.ico").permitAll()
                        .requestMatchers("/api/users/login", "/api/users/register").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs", "/api-docs/**", "/v3/api-docs/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class); // Aquí se añade el filtro JWT para autenticación

        return http.build();
    }
}