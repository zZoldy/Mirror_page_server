/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

public record SheetCellChangeEvent(
        String path,
        int row,      // índice do MODEL (JTable)
        int col,      // índice da coluna
        String value, // novo valor
        String user   // usuário que fez a alteração
) {}
