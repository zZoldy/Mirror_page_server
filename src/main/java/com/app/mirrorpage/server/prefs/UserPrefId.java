/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.prefs;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class UserPrefId implements Serializable {
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "pref_key", nullable = false, length = 100) private String key;
    public UserPrefId() {}
    public UserPrefId(Long userId, String key) { this.userId = userId; this.key = key; }
    public Long getUserId() { return userId; }
    public String getKey() { return key; }
    @Override public boolean equals(Object o){/*…*/
        return false;
    } @Override public int hashCode(){/*…*/
        return 0;
    }
}
