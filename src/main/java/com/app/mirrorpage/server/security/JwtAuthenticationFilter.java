/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/ping",
            "/ws"
    );

    private final JwtService jwt;

    public JwtAuthenticationFilter(JwtService jwt) {
        this.jwt = jwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        final String path = request.getRequestURI();

        // 1) CORS preflight: nunca autenticar
        if (isCorsPreflight(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2) Rotas públicas: passam direto, sem autenticar
        if (isPublicPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3) Se não tem Bearer, segue sem autenticação
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            if (jwt.isValid(token)) {
                Jws<Claims> claims = jwt.parse(token);

                String username = claims.getBody().getSubject();
                List<String> roles = jwt.getRoles(token); // ex.: ["SUPORTE","REDACAO"]

                var authorities = (roles == null ? List.<SimpleGrantedAuthority>of()
                        : roles.stream()
                                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r) // garante prefixo
                                .map(SimpleGrantedAuthority::new)
                                .toList());

                var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // popula o SecurityContext
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().setAuthentication(auth);
            } else {
                // token inválido/expirado: limpa contexto e segue
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
        } catch (JwtException | IllegalArgumentException ex) {
            // parsing/assinatura/expiração falhou: não autentica e segue
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        // actuator/health etc. (se usar)
        if (path.startsWith("/actuator")) {
            return true;
        }
        // página de erro do Spring
        if ("/error".equals(path)) {
            return true;
        }
        return false;
    }

    private boolean isCorsPreflight(HttpServletRequest req) {
        return "OPTIONS".equalsIgnoreCase(req.getMethod())
                && req.getHeader("Origin") != null
                && req.getHeader("Access-Control-Request-Method") != null;
    }
}
