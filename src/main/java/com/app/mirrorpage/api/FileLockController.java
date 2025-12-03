/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.api.dto.FileLockEvent;
import com.app.mirrorpage.server.domain.user.User;
import com.app.mirrorpage.server.service.FileLockService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lock/file")
public class FileLockController {

    private final FileLockService lockService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate; // O Carteiro do WebSocket

    public FileLockController(FileLockService lockService) {
        this.lockService = lockService;
    }

    @PostMapping("/lock")
    public ResponseEntity<?> lock(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        // 🛡️ PROTEÇÃO CONTRA USUÁRIO NULO
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Usuário não autenticado."));
        }
        String path = body.get("path");
        if (path == null) {
            return ResponseEntity.badRequest().build();
        }

        boolean success = lockService.tryLock(path, user.getUsername());

        if (success) {
            broadcastLockChange(path, user.getUsername(), true, false);
            return ResponseEntity.ok(Map.of("granted", true, "owner", user.getUsername()));
        } else {
            String owner = lockService.getOwner(path);
            return ResponseEntity.status(409).body(Map.of("granted", false, "owner", owner));
        }
    }

    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        // 🛡️ PROTEÇÃO TAMBÉM NO UNLOCK
        if (user == null) {
            return ResponseEntity.status(401).build();
        }
        String path = body.get("path");
        if (path != null) {
            lockService.unlock(path, user.getUsername());
            broadcastLockChange(path, null, false, true);
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/notify-update")
    public ResponseEntity<?> notifyUpdate(@RequestBody Map<String, String> body, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        String path = body.get("path");

        // 🛠️ CORREÇÃO: Chama o método que acabamos de criar (apenas path e user)
        if (lockService.isOwner(path, user.getUsername())) {
            // Locked=true, Changed=true
            broadcastLockChange(path, user.getUsername(), true, true);
        }

        return ResponseEntity.ok().build();
    }

// Atualize a assinatura do método auxiliar
    private void broadcastLockChange(String path, String owner, boolean locked, boolean contentChanged) {
        messagingTemplate.convertAndSend("/topic/locks",
                new FileLockEvent(path, owner, locked, contentChanged));
    }
}
