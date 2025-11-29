/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class CellLockService {

    // Mapa em memória: chave -> lock
    private final Map<String, CellLock> locks = new ConcurrentHashMap<>();

    // TTL do lock (ex: 2 minutos)
    private static final Duration TTL = Duration.ofMinutes(2);

    private String key(String path, int row, int col) {
        // 🔴 MUITO IMPORTANTE: usar SEMPRE o mesmo path que vem do controller
        // Nada de normalizar diferente em cada lugar.
        return "lock:" + path + ":r" + row + ":c" + col;
    }

    public synchronized CellLock acquire(String path, int row, int col, String owner) {
        String k = key(path, row, col);
        Instant now = Instant.now();

        CellLock existing = locks.get(k);

        if (existing != null) {
            // se expirou, remove e cria novo
            if (existing.expiresAt.isBefore(now)) {
                locks.remove(k);
            } else {
                // se NÃO expirou e o dono é outro -> não concede lock
                if (!existing.owner.equals(owner)) {
                    return null;
                }
                // se é o MESMO dono, renova TTL
                CellLock renewed = new CellLock(path, row, col, owner, now.plus(TTL));
                locks.put(k, renewed);
                return renewed;
            }
        }

        // cria novo lock
        CellLock lock = new CellLock(path, row, col, owner, now.plus(TTL));
        locks.put(k, lock);

        System.out.printf("[LOCK SERVICE] acquire OK key=%s owner=%s expires=%s%n",
                k, owner, lock.expiresAt);

        return lock;
    }

    public boolean isOwner(String path, int row, int col, String user) {
        String k = key(path, row, col);
        CellLock lock = locks.get(k);
        if (lock == null) {
            return false;
        }
        if (lock.expiresAt.isBefore(Instant.now())) {
            locks.remove(k);
            return false;
        }
        return lock.owner.equals(user);
    }

    public String getOwner(String path, int row, int col) {
        String k = key(path, row, col);
        CellLock lock = locks.get(k);
        if (lock == null) {
            System.out.printf("[LOCK SERVICE] getOwner key=%s -> null%n", k);
            return null;
        }
        if (lock.expiresAt.isBefore(Instant.now())) {
            locks.remove(k);
            System.out.printf("[LOCK SERVICE] getOwner key=%s -> expirado%n", k);
            return null;
        }
        System.out.printf("[LOCK SERVICE] getOwner key=%s -> %s%n", k, lock.owner);
        return lock.owner;
    }

    public void release(String path, int row, int col, String user) {
        String k = key(path, row, col);
        CellLock lock = locks.get(k);
        if (lock == null) {
            System.out.printf("[LOCK SERVICE] release key=%s -> já não existe%n", k);
            return;
        }
        if (!lock.owner.equals(user)) {
            System.out.printf("[LOCK SERVICE] release key=%s negado. owner=%s user=%s%n",
                    k, lock.owner, user);
            return;
        }
        locks.remove(k);
        System.out.printf("[LOCK SERVICE] release OK key=%s owner=%s%n", k, user);
    }

    // Em CellLockService.java
    /**
     * Desloca os locks de lugar quando linhas são inseridas ou removidas.
     *
     * * @param path Caminho do arquivo
     * @param startRow A partir de qual linha o deslocamento começa (inclusive)
     * @param shiftAmount Quantas posições mover (+1 para insert, -1 para
     * delete)
     */
    public synchronized void shiftLocks(String path, int startRow, int shiftAmount) {
        // Lista temporária para não dar erro de concorrência ao modificar o mapa
        List<CellLock> locksToMove = new java.util.ArrayList<>();
        List<String> keysToRemove = new java.util.ArrayList<>();

        // 1. Encontra todos os locks afetados
        locks.forEach((k, lock) -> {
            // Verifica se é do mesmo arquivo e se está na zona de impacto
            if (lock.path.equals(path) && lock.row >= startRow) {
                locksToMove.add(lock);
                keysToRemove.add(k);
            }
        });

        // 2. Remove os locks antigos do mapa
        keysToRemove.forEach(locks::remove);

        // 3. Reinsere os locks na nova posição
        for (CellLock oldLock : locksToMove) {
            int newRow = oldLock.row + shiftAmount;

            // Proteção: não criar locks em linhas negativas (ex: erro de lógica)
            if (newRow < 0) {
                continue;
            }

            // Gera a nova chave
            String newKey = key(path, newRow, oldLock.col);

            // Cria um NOVO objeto Lock com a linha atualizada
            // (Assumindo que sua classe CellLock é imutável ou tem campos final)
            CellLock newLock = new CellLock(
                    path,
                    newRow,
                    oldLock.col,
                    oldLock.owner,
                    oldLock.expiresAt
            );

            locks.put(newKey, newLock);

            System.out.printf("[LOCK SHIFT] Movido lock de %s: Row %d -> %d%n",
                    oldLock.owner, oldLock.row, newRow);
        }
    }
}
