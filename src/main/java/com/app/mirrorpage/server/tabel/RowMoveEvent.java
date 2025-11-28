/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

public record RowMoveEvent(
        String path, // ex.: "/BDBR/Prelim.csv"
        int from, // índice de origem (model)
        int to // índice de destino (model)
        ) {
}
