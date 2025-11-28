/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.prefs;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity @Table(name = "user_preferences")
public class UserPreference {
    @EmbeddedId private UserPrefId id;
    @Column(name = "pref_value", nullable = false, length = 255) private String value;
    @Column(name = "updated_at", insertable = false, updatable = false) private Instant updatedAt;
    public UserPreference() {}
    public UserPreference(Long userId, String key, String value){ this.id=new UserPrefId(userId,key); this.value=value; }
    public UserPrefId getId(){ return id; }
    public String getValue(){ return value; }
    public void setValue(String v){ this.value=v; }
    public Instant getUpdatedAt(){ return updatedAt; }
}
