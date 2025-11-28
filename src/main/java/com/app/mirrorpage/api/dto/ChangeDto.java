/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

public class ChangeDto {
    public String type;    // CREATE | UPDATE | DELETE | RENAME | MODIFY
    public String path;
    public String newPath; // só para RENAME
    public boolean dir;
    public long cursor;
}
