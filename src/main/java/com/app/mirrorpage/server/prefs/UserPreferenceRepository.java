/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.prefs;

import jakarta.transaction.Transactional;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, UserPrefId> {

    // Busca por (user_id, pref_key) usando o ID embutido (UserPrefId)
    Optional<UserPreference> findByIdUserIdAndIdKey(Long userId, String key);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO user_preferences (user_id, pref_key, pref_value)
        VALUES (?1, ?2, ?3)
        ON DUPLICATE KEY UPDATE pref_value = VALUES(pref_value)
        """, nativeQuery = true)
    int upsert(Long userId, String prefKey, String prefValue);
}