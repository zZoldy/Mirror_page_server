/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

import java.time.Instant;

public record CellLockResponse(
        String path,
        int row,
        int col,
        String owner,
        Instant expiresAtMillis
        ) {

}
