/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

public class ChangeBatchDto {
    public java.util.List<ChangeDto> events;
    public long cursor;

    public ChangeBatchDto(java.util.List<ChangeDto> events, long cursor) {
        this.events = events;
        this.cursor = cursor;
    }
}