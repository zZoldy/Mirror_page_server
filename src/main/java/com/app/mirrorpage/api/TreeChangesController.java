/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.server.tree.InMemoryChangeStore;
import com.app.mirrorpage.api.dto.ChangeBatchDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tree")
public class TreeChangesController {

    private final InMemoryChangeStore store;

    public TreeChangesController(InMemoryChangeStore store) {
        this.store = store;
    }

    @GetMapping("/changes")
    public ChangeBatchDto getChanges(@RequestParam(name = "since", defaultValue = "0") long since) {
        return store.findSince(since);
    }
}
