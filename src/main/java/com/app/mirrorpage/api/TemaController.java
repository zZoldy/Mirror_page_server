/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.server.domain.user.User;
import com.app.mirrorpage.server.prefs.TemaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Mantemos o record como está
record ValorDto(String value) {}

@RestController
@RequestMapping("/api/me/tema")
public class TemaController {

    private final TemaService service;

    public TemaController(TemaService service) {
        this.service = service;
    }

    @GetMapping
    public ValorDto get(@AuthenticationPrincipal User user) {
        // Se o utilizador vier nulo (erro de filtro), devolvemos o padrão para não crashar
        if (user == null) return new ValorDto("ESCURO");

        // Passamos o objeto User diretamente para o serviço
        return new ValorDto(service.obterTema(user));
    }

    @PutMapping
    public ResponseEntity<Void> put(@RequestBody ValorDto body, @AuthenticationPrincipal User user) {
        if (user != null) {
            service.salvarTema(user, body.value());
        }
        return ResponseEntity.noContent().build();
    }
}
