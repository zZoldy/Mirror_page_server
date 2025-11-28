/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.fs;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author Z D K
 */
public class TreeChangeBus {

    public static record ChangeDto(String type, String path, String newPath, String dir, boolean isDir) {}
    public static record ChangeBatchDto(long cursor, List<ChangeDto> events) {}

    private static final List<ChangeDto> EVENTS = new CopyOnWriteArrayList<>();
    private static final AtomicLong CURSOR = new AtomicLong(0);

    public static void publish(ChangeDto change) {
        EVENTS.add(change);
        if (EVENTS.size() > 5000) EVENTS.remove(0); // evita overflow
        CURSOR.incrementAndGet();
    }

    public static ChangeBatchDto fetchSince(long cursor) {
        long current = CURSOR.get();
        if (cursor >= current) {
            return new ChangeBatchDto(current, List.of());
        }

        List<ChangeDto> newer = EVENTS.subList(
                (int) Math.max(0, EVENTS.size() - (current - cursor)),
                EVENTS.size()
        );

        return new ChangeBatchDto(current, List.copyOf(newer));
    }
}