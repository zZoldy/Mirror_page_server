/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.service;

import com.app.mirrorpage.server.tabel.RowMoveEvent;
import com.app.mirrorpage.server.tabel.SheetCellChangeEvent;
import com.app.mirrorpage.server.tabel.SheetRowInsertedEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SheetEventBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public SheetEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendCellChange(SheetCellChangeEvent ev) {
        String topic = "/topic/sheet/" + toTopic(ev.path());
        System.out.println("[WS] CellChangeEvent para " + topic
                + " path=" + ev.path()
                + " row=" + ev.row()
                + " col=" + ev.col()
                + " value=" + ev.value());
        messagingTemplate.convertAndSend(topic, ev);
    }

    public void sendRowInserted(SheetRowInsertedEvent ev) {
        // **DICA IMPORTANTE**:
        // use OUTRO tópico para não misturar JSON de tipos diferentes
        String topic = "/topic/sheet/" + toTopic(ev.path());
        System.out.println("[WS] RowInsertedEvent para " + topic
                + " path=" + ev.path()
                + " afterRow=" + ev.afterRow());
        messagingTemplate.convertAndSend(topic, ev);
    }

    public void sendRowMoved(RowMoveEvent ev) {
        String topic = "/topic/sheet/" + toTopic(ev.path());
        System.out.println("[WS] RowMovedEvent para " + topic
                + " path=" + ev.path()
                + " from=" + ev.from()
                + " to=" + ev.to());
        messagingTemplate.convertAndSend(topic, ev);
    }

    private String toTopic(String path) {
        // Mesmo esquema que você já usa (tirar barras, espaços etc.)
        return path.replace("\\", "/").replace("/", "_");
    }
}
