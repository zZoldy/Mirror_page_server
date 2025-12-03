package com.app.mirrorpage.server.prefs;

import com.app.mirrorpage.server.domain.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemaService {

    private final UserPreferenceRepository repo;
    
    // 🗑️ REMOVIDO: private final UserAccountService users; (Causador do erro)

    public TemaService(UserPreferenceRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public String obterTema(User user) {
        // Usamos user.getId() diretamente da memória! Sem SQL extra.
        return repo.findByIdUserIdAndIdKey(user.getId(), "tema")
                   .map(UserPreference::getValue)
                   .orElse("ESCURO"); // Valor padrão seguro
    }

    @Transactional
    public void salvarTema(User user, String valor) {
        if (valor == null) valor = "ESCURO";
        repo.upsert(user.getId(), "tema", valor);
    }
}