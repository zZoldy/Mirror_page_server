/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.prefs;

import com.app.mirrorpage.server.user.UserAccountService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class TemaService {

    private final UserPreferenceRepository repo;
    private final UserAccountService users;

    public TemaService(UserPreferenceRepository repo, UserAccountService users) {
        this.repo = repo;
        this.users = users;
    }

    public String obterTema(Authentication auth) {
        Long uid = users.loadUserIdByUsername(auth.getName());
        return repo.findByIdUserIdAndIdKey(uid, "tema")
                   .map(UserPreference::getValue)
                   .orElse("default");
    }

    public void salvarTema(Authentication auth, String valor) {
        Long uid = users.loadUserIdByUsername(auth.getName());
        repo.upsert(uid, "tema", valor);
    }
}
