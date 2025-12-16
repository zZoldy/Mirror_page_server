/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

public class StopwatchEvent {

    private String action;          // "START", "PAUSE", "RESET"
    private String user;
    private String path;            // Caminho do arquivo (ex: "BDBR/Prelim.csv")
    private long accumulatedTime;   // Tempo acumulado (ms) até o momento da ação
    private long eventTimestamp;    // Hora que o evento ocorreu (para compensar latência se quiser)
    private boolean sync;
    private long timestamp;

    public StopwatchEvent() {
    }

// 👇 Construtor atualizado
    public StopwatchEvent(String action, String user, String path, long accumulatedTime, boolean sync, long timestamp) {
        this.action = action;
        this.user = user;
        this.path = path;
        this.accumulatedTime = accumulatedTime;
        this.sync = sync;
        this.timestamp = timestamp;
    }

    // Getters e Setters para timestamp
    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    // Getters e Setters
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getAccumulatedTime() {
        return accumulatedTime;
    }

    public void setAccumulatedTime(long accumulatedTime) {
        this.accumulatedTime = accumulatedTime;
    }

    public long getEventTimestamp() {
        return eventTimestamp;
    }

    public void setEventTimestamp(long eventTimestamp) {
        this.eventTimestamp = eventTimestamp;
    }

    public boolean isSync() {
        return sync;
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }
}
