/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

import com.app.mirrorpage.fs.PathResolver;
import com.app.mirrorpage.server.service.SheetEventBroadcaster;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SheetService {

    private final PathResolver pathResolver;
    private final SheetEventBroadcaster broadcaster;

    public SheetService(PathResolver pathResolver, SheetEventBroadcaster broadcaster) {
        this.pathResolver = pathResolver;
        this.broadcaster = broadcaster;
    }

    public String loadSheet(String relPath) throws IOException {
        Path file = resolveSheet(relPath);
        if (!Files.exists(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void insertRow(String relPath, int afterRow, String username) throws IOException {
        Path file = resolveSheet(relPath);

        List<String> linhas = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>();

        if (linhas.isEmpty()) {
            // sem cabeçalho, nada a fazer
            return;
        }

        // Cabeçalho na linha 0
        String header = linhas.get(0);
        int numCols = header.split(";", -1).length;

        // ---------- CÁLCULO DO ÍNDICE DE INSERÇÃO NO ARQUIVO ----------
        // Se só tiver cabeçalho, vamos inserir logo abaixo dele
        if (linhas.size() == 1) {
            afterRow = 0; // primeira linha de dados da JTable
        }

        // Último índice de dados no arquivo (penúltima linha, pois a última é ENCERRAMENTO/TOTAL)
        // Ex.: header(0), dados(1..N), ENC( N+1 ) → lastDataIndex = N = linhas.size() - 2
        int lastDataIndex = Math.max(1, linhas.size() - 2);

        // Garante que o afterRow (modelRow) está dentro da faixa de dados
        // modelRow 0..N-1  → arquivo 1..N
        int clampedAfterRow = Math.max(0, Math.min(afterRow, lastDataIndex - 1));

        // Linha correspondente no ARQUIVO à linha selecionada no MODEL
        int baseIndex = clampedAfterRow + 1; // model 0 -> arquivo 1, model 1 -> arquivo 2, ...

        // Queremos inserir ABAIXO da selecionada
        int novaLinhaIndex = baseIndex + 1;  // logo depois da linha base

        // Não deixar inserir DEPOIS da linha ENCERRAMENTO.
        // Se novaLinhaIndex for maior que o índice da linha ENCERRAMENTO, 
        // clampa para antes dela.
        int encIndex = linhas.size() - 1; // última linha do arquivo
        if (novaLinhaIndex > encIndex) {
            novaLinhaIndex = encIndex; // insere antes do ENCERRAMENTO
        }

        // ---------- CÁLCULO DO num_pag (1ª coluna) ----------
        int numPag = 1;
        int idxLinhaAnterior = novaLinhaIndex - 1;

        if (idxLinhaAnterior >= 1 && idxLinhaAnterior < linhas.size()) {
            String linhaAnterior = linhas.get(idxLinhaAnterior);
            String[] colsAnt = linhaAnterior.split(";", -1);
            if (colsAnt.length > 0) {
                try {
                    int anterior = Integer.parseInt(colsAnt[0].trim());
                    numPag = anterior + 1;
                } catch (NumberFormatException e) {
                    // se não for número, mantém 1 (ou outro padrão)
                }
            }
        }

        // ---------- MONTA A NOVA LINHA (MESMO PADRÃO DA TABELA) ----------
        StringBuilder novaLinha = new StringBuilder();
        for (int i = 0; i < numCols; i++) {
            if (i > 0) {
                novaLinha.append(';');
            }

            if (i == 0) {
                // primeira coluna = num_pag sequencial
                novaLinha.append(numPag);
            } else if (i == 8 || i == 9 || i == 10) {
                // colunas de tempo hh:mm
                novaLinha.append("00:00");
            } else if (i == 13) {
                // total hh:mm:ss
                novaLinha.append("00:00:00");
            } else {
                novaLinha.append("");
            }
        }

        // ---------- INSERE NO ARQUIVO E SALVA ----------
        linhas.add(novaLinhaIndex, novaLinha.toString());

        Files.write(
                file,
                linhas,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        // Notifica os outros clientes que UMA LINHA FOI INSERIDA
        // afterRow aqui é o índice do MODEL (linha selecionada na JTable)
        SheetRowInsertedEvent ev = new SheetRowInsertedEvent(relPath, afterRow, username);
        broadcaster.sendRowInserted(ev);
    }

    public void moveRow(String path, int from, int to) throws Exception {
        // 1) Resolve path do CSV no filesystem
        Path abs = resolveSheet(path); // ex.: /srv/mirrorpage/Produtos/BDBR/Prelim.csv

        // 2) Carrega todas as linhas do CSV
        List<String> linhas = Files.readAllLines(abs);

        if (linhas.size() <= 1) {
            // só cabeçalho ou vazio, nada pra mover
            return;
        }

        // Supondo primeira linha = header, linhas de dados começam em 1
        int headerIndex = 0;
        int dataSize = linhas.size() - 1;

        if (from < 0 || from >= dataSize || to < 0 || to >= dataSize) {
            // índices inválidos → joga fora
            return;
        }

        // shift +1 porque no arquivo a linha 0 é header
        int realFrom = headerIndex + 1 + from;
        int realTo = headerIndex + 1 + to;

        if (realFrom == realTo) {
            return;
        }

        List<String> mut = new ArrayList<>(linhas);

        String linhaMovida = mut.remove(realFrom);
        // se removeu antes, o índice destino pode mudar
        if (realTo > realFrom) {
            realTo--; // lista ficou menor
        }
        mut.add(realTo, linhaMovida);

        // 3) Salva de volta o CSV
        Files.write(abs, mut);

        // 4) Notifica via WebSocket
        RowMoveEvent event = new RowMoveEvent(path, from, to);
        broadcaster.sendRowMoved(event);
    }

    /*──────── Excluir linha ────────*/
    public void deleteRow(String relPath, int rowIndex) throws IOException {
        Path file = resolveSheet(relPath);

        List<String> linhas = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>();

        if (linhas.isEmpty()) {
            return;
        }

        String headerLine = linhas.get(0);
        String[] header = headerLine.split(";", -1);

        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) {
            String linha = linhas.get(i);
            if (linha.isBlank()) {
                continue;
            }
            rows.add(linha.split(";", -1));
        }

        if (rowIndex < 0 || rowIndex >= rows.size()) {
            // fora do intervalo, não faz nada
            return;
        }

        // se houver linha TOTAL no final que não deve ser apagada,
        // você pode proteger aqui (exemplo):
        // int lastIndex = rows.size() - 1;
        // if (rowIndex == lastIndex && isLinhaTotal(rows.get(lastIndex))) return;
        rows.remove(rowIndex);

        // se precisar renumerar primeira coluna:
        // renumerarPrimeiraColuna(rows);
        // opcional: recalcular tempos/totais
        String csv = toCsv(header, rows);
        Files.writeString(file, csv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /*──────── Helpers ────────*/
    private Path resolveSheet(String relPath) {
        // adapta para o seu PathResolver real
        return pathResolver.resolveSafe(relPath);
    }

    private String toCsv(String[] header, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(join(header)).append('\n');
        for (String[] r : rows) {
            sb.append(join(r)).append('\n');
        }
        return sb.toString();
    }

    private String join(String[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(escape(arr[i]));
            if (i < arr.length - 1) {
                sb.append(';');
            }
        }
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) {
            s = "";
        }
        boolean needQuotes = s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return needQuotes ? "\"" + v + "\"" : v;
    }

}
