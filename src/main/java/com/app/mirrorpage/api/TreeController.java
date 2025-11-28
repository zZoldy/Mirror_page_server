/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.api.dto.TreeNodeDto;
import com.app.mirrorpage.fs.TreeService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tree")
public class TreeController {

    private final TreeService service;

    public TreeController(TreeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TreeNodeDto>> list(@RequestParam(value = "path", required = false) String path) throws Exception {
        return ResponseEntity.ok(service.list(path));
    }

    // ====== [NOVO] Salvar/criar arquivo texto (CSV) ======
    @PostMapping(value = "/file", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> createOrUpdate(@RequestBody SaveFileReq req) throws Exception {
        service.save_file(req.path(), req.content());
        return ResponseEntity.ok().build();
    }

    // DTO do corpo do POST
    public record SaveFileReq(String path, String content) {

    }

}
