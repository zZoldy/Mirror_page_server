/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.fs;

import com.app.mirrorpage.api.dto.TreeNodeDto;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TreeService {

    @Autowired
    private final PathResolver resolver;

    public TreeService(PathResolver resolver) {
        this.resolver = resolver;
    }

    public List<TreeNodeDto> list(String apiPath) throws IOException {
        Path dir = resolver.resolveSafe(apiPath);
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new NoSuchFileException("Diretório não encontrado: " + apiPath);
        }

        List<TreeNodeDto> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                boolean isDir = Files.isDirectory(p);
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                long size = isDir ? 0L : attrs.size();
                long mtime = attrs.lastModifiedTime().toMillis();

                String rel = "/" + resolver.getRoot().relativize(p.toAbsolutePath().normalize()).toString().replace("\\", "/");

                out.add(new TreeNodeDto(name, rel, isDir, size, mtime));
            }
        }

        out.sort(Comparator.comparing(TreeNodeDto::isDir, Comparator.reverseOrder()).thenComparing(n -> n.name.toLowerCase()));
        return out;
    }

    // ===== [ADD] salvar/criar arquivo texto (CSV, etc.) =====
    /**
     * Cria/atualiza um arquivo de texto no mirrorpage.root.
     *
     * @param apiPath caminho lógico vindo da API/cliente (ex.:
     * "/BDBR/formato.csv")
     * @param content conteúdo em UTF-8
     */
    public void save_file(String apiPath, String content) throws IOException {
        String norm = normalizeLogical(apiPath);

        // Resolve dentro do root e impede path traversal (o próprio resolver faz o sandboxing)
        Path target = resolver.resolveSafe(norm);

        // Garante diretório pai
        Path parent = target.getParent();
        if (parent == null) {
            throw new InvalidPathException(apiPath, "Caminho não possui diretório pai");
        }
        Files.createDirectories(parent);

        // Escreve o conteúdo (cria ou trunca)
        Files.writeString(
                target,
                content == null ? "" : content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    /**
     * Normaliza caminhos lógicos aceitando variações e removendo "/Produtos" se
     * vier.
     */
    private static String normalizeLogical(String p) {
        if (p == null || p.isBlank()) {
            return "/";
        }
        p = p.trim();
        if ("/Produtos".equals(p)) {
            return "/";
        }
        if (p.startsWith("/Produtos/")) {
            p = p.substring("/Produtos".length());
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        // colapsa barras duplicadas
        return p.replaceAll("/{2,}", "/");
    }
}
