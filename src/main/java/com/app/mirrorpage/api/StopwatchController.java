package com.app.mirrorpage.api;

import com.app.mirrorpage.api.dto.StopwatchEvent;
import com.app.mirrorpage.server.tabel.SheetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stopwatch")
public class StopwatchController {

    private final SheetService sheetService;

    public StopwatchController(SheetService sheetService) {
        this.sheetService = sheetService;
    }

    // GET /api/stopwatch/broadcast?path=...
    @GetMapping("/broadcast")
    public ResponseEntity<StopwatchEvent> getStopwatchState(@RequestParam("path") String path) {
        // Normalização simples para garantir que a chave do mapa bata
        // Se o client manda "/Pasta/Arq.csv", usamos isso como chave.

        StopwatchEvent state = sheetService.getStopwatchState(path);

        if (state == null) {
            return ResponseEntity.notFound().build(); // Retorna 404 se não tiver timer rodando
        }
        return ResponseEntity.ok(state);
    }

// POST /api/stopwatch/broadcast
    @PostMapping("/broadcast")
    public ResponseEntity<Void> postStopwatchEvent(@RequestBody StopwatchEvent ev) {
        sheetService.handleStopwatchEvent(ev);
        return ResponseEntity.ok().build();
    }
}
