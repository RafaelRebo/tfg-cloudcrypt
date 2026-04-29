package com.example.util;

import org.springframework.stereotype.Component;

@Component
public class PathUtils {

    public String sanitize(String path) {
        if (path == null || path.isEmpty()) return "/";
        String sanitized = path.replace("..", "");
        if (!sanitized.startsWith("/")) sanitized = "/" + sanitized;
        sanitized = sanitized.replaceAll("[<>:\"|?*]", "");
        return normalize(sanitized);
    }

    public String normalize(String path) {
        if (path == null || path.isEmpty() || path.equals("/")) return "/";
        String p = path.replaceAll("/+", "/");
        if (p.endsWith("/") && p.length() > 1) p = p.substring(0, p.length() - 1);
        return p;
    }

    public String join(String base, String name) {
        String b = normalize(base);
        return normalize((b.equals("/") ? "" : b) + "/" + name);
    }
}