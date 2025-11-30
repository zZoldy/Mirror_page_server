/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.service;

import com.app.mirrorpage.server.domain.user.Role;
import com.app.mirrorpage.server.domain.user.User;
import com.app.mirrorpage.server.repo.RoleRepository;
import com.app.mirrorpage.server.repo.UserRepository;
import jakarta.transaction.Transactional;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    /**
     * Cria um novo usuário com senha BCrypt e roles existentes no banco.
     */
    @Transactional
    public User createUser(String username, String rawPassword, Collection<String> roleNames) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException("password deve ter pelo menos 6 caracteres");
        }
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("Usuário já existe: " + username);
        }

        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(rawPassword)); // <- BCrypt
        u.setEnabled(true);

        Set<Role> resolvedRoles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            resolvedRoles = roleNames.stream()
                    .map(name -> roles.findByName(name)
                    .orElseThrow(() -> new IllegalArgumentException("Role inexistente: " + name)))
                    .collect(Collectors.toSet());
        }
        u.setRoles(resolvedRoles);

        return users.save(u);
    }

    /**
     * Verifica se as credenciais são válidas. Retorna o User se ok, ou lança
     * exceção/retorna null se falhar.
     */
    public User authenticate(String username, String rawPassword) {
        return users.findByUsername(username)
                .filter(u -> u.isEnabled()) // Verifica se está ativo
                .filter(u -> encoder.matches(rawPassword, u.getPassword())) // Compara Hash BCrypt
                .orElse(null);
    }
}
