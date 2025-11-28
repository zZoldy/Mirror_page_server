/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.server.prefs.TemaService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

record ValorDto(String value) {}

@RestController
@RequestMapping("/api/me/tema")
public class TemaController {
    private final TemaService service;
    public TemaController(TemaService service){ this.service=service; }

    @GetMapping
    public ValorDto get(Authentication auth) {
        return new ValorDto(service.obterTema(auth));      // { "value": "..." }
    }

    @PutMapping
    public ResponseEntity<Void> put(@RequestBody ValorDto body, Authentication auth) {
        service.salvarTema(auth, body.value());            // salva ("tema", <valor>)
        return ResponseEntity.noContent().build();         // 204
    }
}
