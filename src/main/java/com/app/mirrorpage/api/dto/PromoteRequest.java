/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api.dto;

public record PromoteRequest(
    String sourcePath,  // Ex: "/BDBR/Prelim.csv"
    int sourceRow,      // Ex: 5
    String targetPath   // Ex: "/BDBR/Final.csv"
) {}