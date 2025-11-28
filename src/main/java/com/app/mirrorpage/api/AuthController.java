/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.server.domain.user.Role;
import com.app.mirrorpage.server.domain.user.User;
import com.app.mirrorpage.server.repo.UserRepository;
import com.app.mirrorpage.server.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {

    }

    public record AuthResponse(String accessToken, String refreshToken, List<String> roles) {

    }

    public record RefreshRequest(@NotBlank String refreshToken) {

    }

    public record RefreshResponse(String accessToken) {

    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@RequestBody LoginRequest req) {
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

    // ========== [NOVO] Refresh do access token ==========
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
