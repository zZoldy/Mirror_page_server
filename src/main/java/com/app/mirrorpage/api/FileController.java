/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.fs.PathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final PathResolver resolver;

    public FileController(PathResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/read")
    public ResponseEntity<String> read(@RequestParam("path") String pathRelativo) throws IOException {
        Path p = resolver.resolveSafe(pathRelativo);

        if (!Files.exists(p) || !Files.isRegularFile(p)) {
            return ResponseEntity.notFound().build();
        }

        // lê inteiro, devolve string
        String conteudo = Files.readString(p, StandardCharsets.UTF_8);
        return ResponseEntity.ok(conteudo);
    }
}
