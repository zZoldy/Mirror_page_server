/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

import java.time.Instant;


public class CellLock {
    public final String path;
    public final int row;
    public final int col;
    public final String owner;
    public final Instant expiresAt;

    public CellLock(String path, int row, int col, String owner, Instant expiresAt) {
        this.path = path;
        this.row = row;
        this.col = col;
        this.owner = owner;
        this.expiresAt = expiresAt;
    }
}
