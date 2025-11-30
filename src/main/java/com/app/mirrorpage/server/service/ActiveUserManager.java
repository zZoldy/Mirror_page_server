package com.app.mirrorpage.server.service;

import com.app.mirrorpage.server.tabel.CellLockService;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class ActiveUserManager {

    // Injeção do serviço de Locks para poder limpar quando o usuário sair
    private final CellLockService lockService;
    // Mapa Seguro: ID da Sessão -> Nome do Usuário
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    public ActiveUserManager(CellLockService lockService) {
        this.lockService = lockService;
    }

    public void addSession(String sessionId, String username) {
        // Se chegou aqui, o Interceptor já garantiu que não é duplicado.
        activeSessions.put(sessionId, username);
        System.out.println("[AUTH] CONNECT: " + username);
    }

    /**
     * Remove a sessão e dispara a limpeza de locks. Chamado automaticamente
     * quando a conexão cai.
     */
    public void removeSession(String sessionId) {
        String username = activeSessions.remove(sessionId);

        if (username != null) {
            System.out.println("[AUTH] DISCONNECT: O usuário '" + username + "' saiu.");

            // 🔴 AQUI É O PULO DO GATO:
            // "Ei LockService, o fulano saiu. Solte tudo que ele estava segurando!"
            lockService.releaseAllLocksByUser(username);
        }
    }

    /**
     * Retorna o nome do usuário dono de uma sessão específica.
     */
    public String getUser(String sessionId) {
        return activeSessions.get(sessionId);
    }

    public boolean isUserConnected(String username) {
        return activeSessions.containsValue(username);
    }
}
