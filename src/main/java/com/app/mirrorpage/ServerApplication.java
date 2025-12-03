/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.app.mirrorpage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {
    public static void main(String[] args) {
        System.out.println("### MirrorPage SERVER - CELLSYNC BUILD 0.0###");
        SpringApplication.run(ServerApplication.class, args);
    }
}
