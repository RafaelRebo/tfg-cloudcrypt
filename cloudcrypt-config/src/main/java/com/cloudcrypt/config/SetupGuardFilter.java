package com.cloudcrypt.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

// Clase de utilidad que intercepta, con máxima prioridad, las peticiones HTTP para forzar la instalación de la app si se detecta que no lo está
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SetupGuardFilter implements Filter {

    // Función que redirige al usuario a la página de instalación (setup.html) si la aplicación no está instalada
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();

        boolean isInstalled = ConfigPathResolver.getConfigFile().exists(); // Se asume que si no existe el fichero .properties generado durante la instalación, es que la app no está instalada

        if (!isInstalled) {
            boolean isSetupRequest = path.equals("/setup.html")
                    || path.startsWith("/api/setup/")
                    || path.startsWith("/css/")
                    || path.startsWith("/js/")
                    || path.startsWith("/img/")
                    || path.equals("/favicon.ico");

            if (isSetupRequest) {
                chain.doFilter(request, response);
            } else {
                res.sendRedirect("/setup.html");
            }
        } else {
            boolean isTryingToReinstall = path.equals("/setup.html")
                    || path.equals("/api/setup/test-db")
                    || path.equals("/api/setup/submit");

            if (isTryingToReinstall) {
                res.sendRedirect("/");
            } else {
                chain.doFilter(request, response);
            }
        }
    }
}