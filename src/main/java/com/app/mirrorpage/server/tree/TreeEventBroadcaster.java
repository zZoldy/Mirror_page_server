package com.app.mirrorpage.server.tree;

import com.app.mirrorpage.fs.PathResolver;
import com.app.mirrorpage.fs.TreeChangeBus.ChangeDto;
import java.nio.file.Path;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TreeEventBroadcaster {

    private final Path ROOT;
    private final SimpMessagingTemplate messaging; // [NOVO] Injeção

    // [ALTERADO] Adicione SimpMessagingTemplate ao construtor
    public TreeEventBroadcaster(PathResolver resolver, SimpMessagingTemplate messaging) {
        this.ROOT = resolver.getRoot();
        this.messaging = messaging;
    }

    private String toLogicalPath(java.nio.file.Path fullPath) {
        java.nio.file.Path root = ROOT;
        java.nio.file.Path rel = root.relativize(fullPath);
        String p = "/" + rel.toString().replace(java.io.File.separatorChar, '/');
        p = p.replaceAll("/{2,}", "/");
        if (p.isBlank()) {
            return "/";
        }
        return p;
    }

    public void onCreate(java.nio.file.Path fullPath, boolean dir) {
        String path = toLogicalPath(fullPath);
        broadcast("CREATE", path, null, dir); // [NOVO] Dispara o aviso
    }

    public void onDelete(java.nio.file.Path fullPath, boolean dir) {
        String path = toLogicalPath(fullPath);
        broadcast("DELETE", path, null, dir); // [NOVO]
    }

    public void onModify(java.nio.file.Path fullPath, boolean dir) {
        String path = toLogicalPath(fullPath);
        broadcast("UPDATE", path, null, dir); // [NOVO]
    }

    // [NOVO] Método auxiliar para enviar via WebSocket
    private void broadcast(String type, String path, String newPath, boolean isDir) {
        // Cria o objeto que será convertido em JSON
        ChangeDto dto = new ChangeDto(type, path, newPath, String.valueOf(isDir), isDir);

        // Envia para o tópico público
        messaging.convertAndSend("/topic/tree-changes", dto);
    }
}
