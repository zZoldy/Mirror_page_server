/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

public class RowInsertRequest {

    private String path;
    private int afterRow;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getAfterRow() {
        return afterRow;
    }

    public void setAfterRow(int afterRow) {
        this.afterRow = afterRow;
    }
}
