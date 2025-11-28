/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.fs;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PathResolver {

    private final Path root;

    public PathResolver(@Value("${mirrorpage.root}") String rootDir) {
        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
    }

    public Path getRoot() {
        return root;
    }

    public Path resolveSafe(String apiPath) {
        if (apiPath == null || apiPath.isBlank() || "/".equals(apiPath)) {
            return root;
        }

        try {
            apiPath = java.net.URLDecoder.decode(apiPath, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Logar erro ou ignorar se tiver certeza que não precisa
        }

        String clean = apiPath.replace("\\", "/");
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }

        Path p = root.resolve(clean).normalize().toAbsolutePath();

        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Caminho fora da raiz");
        }
        return p;
    }
}
