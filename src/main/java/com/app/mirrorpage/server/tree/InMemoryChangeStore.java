/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tree;

import com.app.mirrorpage.api.dto.ChangeBatchDto;
import com.app.mirrorpage.api.dto.ChangeDto;
import org.springframework.stereotype.Component;

@Component
public class InMemoryChangeStore {

    private final java.util.List<ChangeDto> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicLong cursorSeq = new java.util.concurrent.atomic.AtomicLong(0);

    public void append(String type, String path, String newPath, boolean dir) {
        ChangeDto dto = new ChangeDto();
        dto.type   = type;
        dto.path   = path;
        dto.newPath = newPath;
        dto.dir    = dir;
        dto.cursor = cursorSeq.incrementAndGet();
        events.add(dto);
    }

    public ChangeBatchDto findSince(long since) {
        java.util.List<ChangeDto> list = events.stream()
                .filter(e -> e.cursor > since)
                .toList();

        long currentCursor = cursorSeq.get();
        return new ChangeBatchDto(list, currentCursor);
    }
}
