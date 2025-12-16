package com.app.mirrorpage.api;

import com.app.mirrorpage.server.domain.user.Role;
import com.app.mirrorpage.server.domain.user.User;
import com.app.mirrorpage.server.repo.UserRepository;
import com.app.mirrorpage.server.security.JwtService;
import com.app.mirrorpage.server.service.ActiveUserManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {

    }

    // 🔴 1. ADICIONADO 'username' NA RESPOSTA
    public record AuthResponse(String accessToken, String refreshToken, List<String> roles) {

    }

    public record RefreshRequest(@NotBlank String refreshToken) {

    }

    public record RefreshResponse(String accessToken) {

    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final ActiveUserManager activeUserManager;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, ActiveUserManager activeUserManager) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.activeUserManager = activeUserManager;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@RequestBody LoginRequest req) {

// 1. Lógica de Bloqueio (Lançando Exceção)
        if (activeUserManager.isUserConnected(req.username())) {
            // Isso vai gerar o erro 409 e parar a execução aqui
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este usuário já está conectado em outra sessão.");
        }

        User u = users.findByUsername(req.username())
                .orElseThrow(() -> new IllegalArgumentException("Usuário ou senha inválidos"));

        if (!u.isEnabled() || !encoder.matches(req.password(), u.getPassword())) {
            throw new IllegalArgumentException("Usuário ou senha inválidos");
        }

        String access = jwt.generateAccessToken(u);
        String refresh = jwt.generateRefreshToken(u);
        List<String> roles = u.getRoles().stream().map(Role::getName).toList();

        return new AuthResponse(access, refresh, roles);
    }

    // ... (Método refresh permanece igual) ...
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public RefreshResponse refresh(@RequestBody RefreshRequest req) {
        if (req == null || req.refreshToken() == null || req.refreshToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "refreshToken obrigatório");
        }
        if (!jwt.isValid(req.refreshToken()) || !jwt.isRefreshToken(req.refreshToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token inválido");
        }

        String username = jwt.getUsername(req.refreshToken());
        User u = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado"));

        String newAccess = jwt.generateAccessToken(u);
        return new RefreshResponse(newAccess);
    }
}
