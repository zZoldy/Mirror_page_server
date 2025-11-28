/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tree;

import com.app.mirrorpage.fs.PathResolver;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class TreeEventBroadcaster {

    private final Path ROOT;

    private final InMemoryChangeStore buffer;

    public TreeEventBroadcaster(InMemoryChangeStore buffer, PathResolver resolver) {
        this.ROOT = resolver.getRoot();   // 💯 sempre funcionaf
        this.buffer = buffer;
    }

    private String toLogicalPath(java.nio.file.Path fullPath) {
        java.nio.file.Path root = ROOT;
        java.nio.file.Path rel = root.relativize(fullPath);
        String p = "/" + rel.toString().replace(java.io.File.separatorChar, '/');
        // normalização estilo FsTree.normalizePath:
        p = p.replaceAll("/{2,}", "/");
        if (p.isBlank()) {
            return "/";
        }
        return p;
    }

    public void onCreate(java.nio.file.Path fullPath, boolean dir) {
        String path = toLogicalPath(fullPath);
        buffer.append("CREATE", path, null, dir);
    }

    public void onDelete(java.nio.file.Path fullPath, boolean dir) {
        String path = toLogicalPath(fullPath);
        buffer.append("DELETE", path, null, dir);
    }

    public void onModify(java.nio.file.Path fullPath, boolean dir) {
        String path = toLogicalPath(fullPath);
        buffer.append("UPDATE", path, null, dir);
    }

    // se quiser suportar rename, você teria que detectar isso pela combinação DELETE+CREATE
}
