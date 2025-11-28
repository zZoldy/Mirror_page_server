/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.api;

import com.app.mirrorpage.api.dto.MoveRowRequest;
import com.app.mirrorpage.fs.PathResolver;
import com.app.mirrorpage.server.service.SheetEventBroadcaster;
import com.app.mirrorpage.server.tabel.CellLock;
import com.app.mirrorpage.server.tabel.CellLockRequest;
import com.app.mirrorpage.server.tabel.CellLockResponse;
import com.app.mirrorpage.server.tabel.CellLockService;
import com.app.mirrorpage.server.tabel.CellSaveRequest;
import com.app.mirrorpage.server.tabel.SheetCellChangeEvent;
import com.app.mirrorpage.server.tabel.SheetService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sheet")
public class SheetController {

    private final PathResolver resolver;
    private final CellLockService lockService;
    private final SheetService sheetService;
    private final SheetEventBroadcaster sheetEventBroadcaster;

    public SheetController(CellLockService lockService,
            PathResolver resolver, SheetService sheetService,
            SheetEventBroadcaster sheetEventBroadcaster) {
        this.lockService = lockService;
        this.resolver = resolver;
        this.sheetService = sheetService;
        this.sheetEventBroadcaster = sheetEventBroadcaster;
    }

    @PostMapping("/lock")
    public ResponseEntity<?> lock(@RequestBody CellLockRequest req,
            Authentication auth) {

        String user = auth.getName();

        CellLock lock = lockService.acquire(req.path(), req.row(), req.col(), user);
        if (lock == null) {
            String owner = lockService.getOwner(req.path(), req.row(), req.col());

            System.out.printf("[LOCK] RECUSADO path=%s row=%d col=%d ownerAtual=%s requisitante=%s%n",
                    req.path(), req.row(), req.col(), owner, user);

            // Resposta JSON
            record LockConflictResponse(String message, String owner) {

            }

            LockConflictResponse body = new LockConflictResponse(
                    "Cell already locked by another user",
                    owner
            );

            return ResponseEntity.status(409).body(body);
        }

        CellLockResponse resp = new CellLockResponse(
                lock.path,
                lock.row,
                lock.col,
                lock.owner,
                lock.expiresAt
        );
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestBody CellLockRequest req,
            Authentication auth) throws Exception {

        String user = auth.getName(); // usuário logado

        // Pega o dono atual do lock
        String ownerAtual = lockService.getOwner(req.path(), req.row(), req.col());

        System.out.printf(
                "[LOCK UNLOCK] pedido path=%s row=%d col=%d user=%s ownerAtual=%s%n",
                req.path(), req.row(), req.col(), user, ownerAtual
        );

        // verifica se quem está tentando liberar é o dono do lock
        if (!lockService.isOwner(req.path(), req.row(), req.col(), user)) {
            System.out.printf(
                    "[LOCK UNLOCK] NEGADO → Not lock owner (path=%s row=%d col=%d user=%s ownerAtual=%s)%n",
                    req.path(), req.row(), req.col(), user, ownerAtual
            );
            return ResponseEntity.status(403).body("Not lock owner");
        }

        // libera lock
        lockService.release(req.path(), req.row(), req.col(), user);

        System.out.printf(
                "[LOCK UNLOCK] OK → liberado path=%s row=%d col=%d user=%s%n",
                req.path(), req.row(), req.col(), user
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/save-cell")
    public ResponseEntity<?> saveCell(@RequestBody CellSaveRequest req,
            Authentication auth) {
        String user = auth.getName();

        // row/col vindos do CLIENTE = índices do TableModel (JTable)
        int modelRow = req.row();
        int col = req.col();

        System.out.printf(
                "[SAVE-CELL SERVER] path=%s modelRow=%d col=%d user=%s value='%s'%n",
                req.path(), modelRow, col, user, req.value()
        );

        try {
            Path filePath = resolver.resolveSafe(req.path());
            System.out.println("[SAVE-CELL SERVER] filePath=" + filePath);

            if (!Files.exists(filePath)) {
                System.out.println("[SAVE-CELL SERVER] ERRO: arquivo não existe");
                return ResponseEntity.notFound().build();
            }

            List<String> linhas = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            System.out.println("[SAVE-CELL SERVER] linhas.size=" + linhas.size());

            // 🔥 AQUI ESTÁ O PULO DO GATO:
            //  - linha 0 do arquivo = CABEÇALHO
            //  - linha 1 do arquivo = modelRow 0
            //  - linha 2 do arquivo = modelRow 1
            int fileRow = modelRow + 1;

            if (fileRow < 0 || fileRow >= linhas.size()) {
                System.out.printf(
                        "[SAVE-CELL SERVER] ERRO: fileRow fora do limite (fileRow=%d, size=%d)%n",
                        fileRow, linhas.size()
                );
                return ResponseEntity.badRequest().body("Row out of bounds");
            }

            String linha = linhas.get(fileRow);
            System.out.println("[SAVE-CELL SERVER] linha ANTES = '" + linha + "'");

            // CSV separado por ;
            String sep = ";";
            String[] cols = linha.split(java.util.regex.Pattern.quote(sep), -1);

            System.out.println("[SAVE-CELL SERVER] cols.length (antes) = " + cols.length);
            System.out.println("[SAVE-CELL SERVER] col pedida = " + col);

            if (col < 0) {
                return ResponseEntity.badRequest().body("Column out of bounds");
            }

            // Expande se precisar
            if (col >= cols.length) {
                int oldLen = cols.length;
                int newLen = col + 1;

                String[] novo = new String[newLen];
                System.arraycopy(cols, 0, novo, 0, oldLen);
                for (int i = oldLen; i < newLen; i++) {
                    novo[i] = "";
                }
                cols = novo;

                System.out.printf("[SAVE-CELL SERVER] expandindo colunas de %d para %d%n",
                        oldLen, newLen);
            }

            String newVal = (req.value() != null) ? req.value() : "";
            System.out.printf("[SAVE-CELL SERVER] alterando col=%d de '%s' para '%s'%n",
                    col, cols[col], newVal);

            cols[col] = newVal;

            String novaLinha = String.join(sep, cols);
            linhas.set(fileRow, novaLinha);

            Files.write(filePath, linhas, StandardCharsets.UTF_8);

            System.out.println("[SAVE-CELL SERVER] linha DEPOIS = '" + novaLinha + "'");
            System.out.println("[SAVE-CELL SERVER] arquivo salvo OK");

            // 🔔 Dispara evento para outros clientes usando o broadcaster
            SheetCellChangeEvent evt = new SheetCellChangeEvent(
                    req.path(), // ex: "/GCO/Prelim.csv"
                    modelRow, // índice do MODEL (igual o cliente usa)
                    col, // coluna
                    newVal,
                    user
            );

            System.out.println("[SAVE-CELL SERVER] chamando SheetEventBroadcaster com evt="
                    + evt + " e broadcaster=" + sheetEventBroadcaster.getClass().getName());

            sheetEventBroadcaster.sendCellChange(evt);

            return ResponseEntity.ok().build();

        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.internalServerError().body("Erro ao salvar célula");
        }
    }

    // ====== GET /api/sheet?path=...  -> usado pelo ApiClient.loadSheet ======
    @GetMapping
    public ResponseEntity<String> loadSheet(@RequestParam("path") String path) throws IOException {
        // o cliente manda "/BDBR/Prelim.csv", o service espera algo tipo "BDBR/Prelim.csv"
        String relPath = path.replaceFirst("^[\\\\/]+", "");

        String csv = sheetService.loadSheet(relPath);

        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(csv);
    }

    @PostMapping("/row/insert")
    public ResponseEntity<Void> insertRow(
            @RequestParam("path") String path,
            @RequestParam("afterRow") int afterRow,
            java.security.Principal principal
    ) throws IOException {

        String username = principal != null ? principal.getName() : "unknown";

        sheetService.insertRow(path, afterRow, username);

        // Mesmo esquema de edição: só status 204, sem corpo
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/moveRow")
    public ResponseEntity<Void> moveRow(@RequestBody MoveRowRequest req) throws Exception {
        sheetService.moveRow(req.path(), req.from(), req.to());
        return ResponseEntity.ok().build();
    }
}
