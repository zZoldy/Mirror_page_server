/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

public record UnlockAndSaveCellRequest(
        String path,
        int row,
        int col,
        String value
) { }
