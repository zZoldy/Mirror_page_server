/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

public record SheetRowInsertedEvent(
        String path, // mesmo path da planilha: "BDBR/Prelim.csv"
        int afterRow, // linha depois da qual foi inserida
        String user // quem inseriu (opcional, mas é útil)
        ) {

}
