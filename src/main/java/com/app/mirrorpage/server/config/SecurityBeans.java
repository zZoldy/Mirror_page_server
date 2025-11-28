/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());        // produção
        encoders.put("noop", NoOpPasswordEncoder.getInstance());    // texto puro

        DelegatingPasswordEncoder delegate =
                new DelegatingPasswordEncoder("bcrypt", encoders);

        // Fallback padrão para valores sem {id}: trata como bcrypt
        delegate.setDefaultPasswordEncoderForMatches(encoders.get("bcrypt"));

        // Fallback extra: se não parece bcrypt e não tem {id}, compara como texto puro
        return new PasswordEncoder() {
            @Override public String encode(CharSequence raw) { return delegate.encode(raw); }
            @Override public boolean matches(CharSequence raw, String encoded) {
                if (encoded == null) return false;
                if (encoded.startsWith("{bcrypt}") || encoded.startsWith("$2")) {
                    return delegate.matches(raw, encoded); // BCrypt
                }
                if (encoded.startsWith("{noop}")) {
                    return encoded.substring(6).equals(raw.toString()); // {noop}senha
                }
                // Sem prefixo e não-BCrypt → texto puro
                return encoded.equals(raw.toString());
            }
        };
    }
}
