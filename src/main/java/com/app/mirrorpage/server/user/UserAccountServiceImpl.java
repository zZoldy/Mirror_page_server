/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.user;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UserAccountServiceImpl implements UserAccountService {
    private final JdbcTemplate jdbc;
    public UserAccountServiceImpl(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override
    public Long loadUserIdByUsername(String username){
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
    }
}