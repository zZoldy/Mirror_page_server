package com.app.mirrorpage.server.config;

import com.app.mirrorpage.server.security.JwtService;
import com.app.mirrorpage.server.service.ActiveUserManager;
import com.app.mirrorpage.server.service.ServerLog;
import com.app.mirrorpage.server.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final ActiveUserManager activeUserManager;
    private final JwtService jwtService;
    private final UserService userService;
        private final ServerLog serverLog;

    // Injeção de dependências necessária para validar o token
    public AuthChannelInterceptor(@Lazy ActiveUserManager activeUserManager,
            JwtService jwtService,
            UserService userService,
            ServerLog serverLog) {
        this.activeUserManager = activeUserManager;
        this.jwtService = jwtService;
        this.userService = userService;
        this.serverLog = serverLog;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getCommand() != null) {

            // --- EVENTO DE CONEXÃO (CONNECT) ---
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {

                // 1. Pega o header Authorization
                String authHeader = accessor.getFirstNativeHeader("Authorization");
                String token = null;

                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7); // Remove "Bearer "
                }

                // 2. Validação usando SEU JwtService
                if (token != null && jwtService.isValid(token)) { // Usa isValid()

                    String username = jwtService.getUsername(token); // Usa getUsername()

                    if (username != null) {
                        // Carrega detalhes do usuário do banco para garantir que ainda existe/está ativo
                        UserDetails userDetails = userService.loadUserByUsername(username);

                        // Cria a autenticação do Spring Security
                        UsernamePasswordAuthenticationToken auth
                                = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        accessor.setUser(auth);

                        // 🔴 SUCESSO: Registra no ActiveUserManager
                        // Isso vai gerar o log: [AUTH] CONNECT: Usuario
                        activeUserManager.addSession(accessor.getSessionId(), username);
                        return message; // <--- SUCESSO: Deixa passar
                        // Debug opcional

                    }
                }

                serverLog.info("[AuthIncerceptor]", "Bloqueando: Token inválido ou ausente." );
                return null;
            } else if (StompCommand.SEND.equals(accessor.getCommand()) || StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {

                if (accessor.getUser() == null) {
                    serverLog.info("[AuthIncerceptor]", "Bloqueando ação: Usuário não autenticado");
                    return null; // <--- Bloqueia o envio de dados
                }
            }// --- EVENTO DE DESCONEXÃO (DISCONNECT) ---
            else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                // 🔴 Remove do ActiveUserManager 
                // Isso vai gerar o log: [AUTH] DISCONNECT e limpar locks/cronômetros
                activeUserManager.removeSession(accessor.getSessionId());
            }
        }

        return message;
    }
}
