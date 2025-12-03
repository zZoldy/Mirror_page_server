package com.app.mirrorpage.server.tree;

import com.app.mirrorpage.fs.PathResolver;
import com.app.mirrorpage.fs.TreeChangeBus.ChangeDto;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TreeEventBroadcaster {

    private final Path ROOT;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate; // O carteiro do WebSocket

    public TreeEventBroadcaster(PathResolver resolver) {
        this.ROOT = resolver.getRoot();
    }

    private String toLogicalPath(Path fullPath) {
        Path rel = ROOT.relativize(fullPath);
        String p = "/" + rel.toString().replace(java.io.File.separatorChar, '/');
        return p.replaceAll("/{2,}", "/");
    }

    public void onCreate(Path fullPath, boolean dir) {
        broadcast("CREATE", fullPath, dir);
    }

    public void onDelete(Path fullPath, boolean dir) {
        broadcast("DELETE", fullPath, dir);
    }

    public void onModify(Path fullPath, boolean dir) {
        broadcast("MODIFY", fullPath, dir);
    }

    private void broadcast(String type, Path fullPath, boolean isDir) {
        String path = toLogicalPath(fullPath);
        
        // Cria o objeto que será transformado em JSON
        // Ajuste os parâmetros conforme o construtor do seu ChangeDto no servidor
        ChangeDto event = new ChangeDto(type, path, null, null, isDir);

        // Envia para o tópico que seu AppSocketClient está escutando
        System.out.println("[WS Tree] Enviando " + type + " -> " + path);
        messagingTemplate.convertAndSend("/topic/tree-changes", event);
    }
}
