/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

/**
 *
 * @author Z D K
 */
public class TreeNodeDto {

    public String name;
    public String path;
    public boolean dir;
    public long size;
    public long mtime;

    public TreeNodeDto(String name, String path, boolean dir, long size, long mtime) {
        this.name = name;
        this.path = path;
        this.dir = dir;
        this.size = size;
        this.mtime = mtime;
    }

    public boolean isDir() {
        return dir;
    }
}
