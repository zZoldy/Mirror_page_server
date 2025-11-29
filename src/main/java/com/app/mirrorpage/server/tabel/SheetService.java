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
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SheetService {

    private final PathResolver pathResolver;
    private final SheetEventBroadcaster broadcaster;
    private final CellLockService cellLockService;

    public SheetService(PathResolver pathResolver,
            SheetEventBroadcaster broadcaster,
            CellLockService cellLockService) {
        this.pathResolver = pathResolver;
        this.broadcaster = broadcaster;
        this.cellLockService = cellLockService;
    }

    public String loadSheet(String relPath) throws IOException {
        Path file = resolveSheet(relPath);
        if (!Files.exists(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

// Em SheetService.java
    public void insertRow(String relPath, int afterRow, String username) throws IOException {
        Path file = resolveSheet(relPath);

        List<String> linhas = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>();

        // Precisa ter estrutura mínima (Header + Fixa + Rodapé)
        if (linhas.isEmpty()) {
            return;
        }

        // Índices de estrutura
        int fixedDataIndex = 1; // Linha Fixa (não insere antes dela)

        // Cálculo do índice de inserção no Arquivo Físico
        // afterRow (Visual) + 2 = Posição logo após a linha selecionada
        // Ex: Selecionou Linha Fixa (0) -> Insere no índice 2
        int novaLinhaIndex = afterRow + 2;

        // Proteção: Não deixar inserir DEPOIS do rodapé
        // Se o índice calculado cair no rodapé ou depois, ajusta para ANTES dele.
        if (novaLinhaIndex >= linhas.size()) {
            novaLinhaIndex = linhas.size() - 1;
        }

        // Proteção: Não inserir antes dos dados móveis
        if (novaLinhaIndex <= fixedDataIndex) {
            novaLinhaIndex = fixedDataIndex + 1;
        }

        String header = linhas.get(0);
        int numCols = header.split(";", -1).length;

        // ===== 1. Monta a Nova Linha =====
        StringBuilder novaLinha = new StringBuilder();
        for (int i = 0; i < numCols; i++) {
            if (i > 0) {
                novaLinha.append(';');
            }

            if (i == 0) {
                // Coloca "0" temporariamente. O renumerarPaginas vai corrigir.
                novaLinha.append("0");
            } else if (i == 8 || i == 9 || i == 10) {
                novaLinha.append("00:00");
            } else if (i == 13) {
                novaLinha.append("00:00:00");
            } else {
                novaLinha.append("");
            }
        }

        // ===== 2. Insere na Lista =====
        linhas.add(novaLinhaIndex, novaLinha.toString());

        // ===== 3. Renumera Tudo Automaticamente =====
        // Agora que a lista cresceu, definimos o limite
        int footerIndex = linhas.size() - 1;

        // Renumera da primeira linha móvel (2) até antes do rodapé
        renumerarPaginas(linhas, fixedDataIndex + 1, footerIndex - 1);

        cellLockService.shiftLocks(relPath, fixedDataIndex, 1);

        // ===== 4. Salva no Disco =====
        // Escreve a lista corrigida e numerada
        Files.write(file, linhas, StandardCharsets.UTF_8);

        // ===== 5. Notifica =====
        SheetRowInsertedEvent ev = new SheetRowInsertedEvent(relPath, afterRow, username);
        broadcaster.sendRowInserted(ev);
    }

    public void moveRow(String path, int from, int to, String username) throws Exception {
        Path abs = resolveSheet(path);
        List<String> linhas = Files.readAllLines(abs, StandardCharsets.UTF_8);

        if (linhas.size() < 4) {
            return; // Mínimo: Header, Fixa, 1 Dado, Rodapé
        }

        // --- 1. VALIDAÇÃO DE INTERVALO (A Correção Crítica) ---
        // Verifica se existe algum lock na Origem, no Destino, OU em qualquer linha entre eles.
        // Isso impede que o movimento desalinhe a edição de outro usuário.
        int start = Math.min(from, to);
        int end = Math.max(from, to);

        for (int i = start; i <= end; i++) {
            try {
                // Se encontrar um lock de OUTRA pessoa, estoura erro e cancela tudo.
                validarLinhaLivre(path, i, username, linhas);
            } catch (IllegalStateException e) {
                throw new IllegalStateException("Movimento bloqueado: A linha " + (i + 1)
                        + " está em uso no momento. Aguarde a edição terminar.");
            }
        }

        int headerIndex = 0;
        int fixedDataIndex = 1;
        int footerIndex = linhas.size() - 1;

        // Cálculo dos índices reais no arquivo físico
        int realFrom = headerIndex + 1 + from;
        int realTo = headerIndex + 1 + to;

        // --- PROTEÇÕES ---
        if (realFrom <= fixedDataIndex || realTo <= fixedDataIndex) {
            return;
        }
        if (realFrom >= footerIndex || realTo >= footerIndex) {
            return;
        }
        if (realFrom == realTo) {
            return;
        }

        // --- MOVIMENTO EXATO ---
        List<String> mut = new ArrayList<>(linhas);

        // 1. Remove da posição antiga
        String linhaMovida = mut.remove(realFrom);

        // 2. Insere na posição de destino
        mut.add(realTo, linhaMovida);

        // --- RENUMERAÇÃO ---
        renumerarPaginas(mut, fixedDataIndex + 1, footerIndex - 1);

        // --- ATENÇÃO AOS LOCKS ---
        // Removemos a chamada 'shiftLocks' aqui.
        // Motivo: Como validamos acima que NÃO HÁ locks no intervalo afetado, 
        // não precisamos deslocar locks de ninguém. 
        // Se o próprio usuário que moveu tinha locks, eles seriam invalidados ou 
        // liberados pelo front ao soltar o mouse. É mais seguro não mexer no mapa de locks aqui.
        // Salva e notifica
        Files.write(abs, mut, StandardCharsets.UTF_8);
        broadcaster.sendRowMoved(new RowMoveEvent(path, from, to, username));
    }

    /**
     * Método auxiliar para garantir a sequência 1, 2, 3... na primeira coluna.
     */
    private void renumerarPaginas(List<String> linhas, int startRow, int endRow) {
        int numeroPagina = 1; // Contador sequencial
        String sep = ";";     // Seu separador CSV

        for (int i = startRow; i <= endRow; i++) {
            String linha = linhas.get(i);

            // Divide a linha preservando colunas vazias (-1)
            String[] cols = linha.split(java.util.regex.Pattern.quote(sep), -1);

            // Se a linha for válida, força a Coluna 0 a ser o número sequencial
            if (cols.length > 0) {
                String novoNum = String.valueOf(numeroPagina);

                // Só altera e recria a string se o número estiver errado
                if (!cols[0].equals(novoNum)) {
                    cols[0] = novoNum;
                    linhas.set(i, String.join(sep, cols));
                }
            }
            numeroPagina++;
        }
    }

    public void deleteRow(String path, int modelRow, String username) throws Exception {
        Path abs = resolveSheet(path);
        List<String> linhas = Files.readAllLines(abs, StandardCharsets.UTF_8);

        // Estrutura Mínima: Header(0) + Fixa(1) + Rodapé(Size-1)
        if (linhas.size() < 3) {
            return;
        }

        validarLinhaLivre(path, modelRow, username, linhas);

        // --- 1. VALIDAR ÍNDICES ---
        int headerIndex = 0;
        int fixedDataIndex = 1;      // Linha Fixa (Model Row 0)
        int footerIndex = linhas.size() - 1;

        // Converte ModelRow (da tabela) para FileRow (do arquivo)
        // Model 0 = File 1
        // Model 1 = File 2
        int fileIndex = headerIndex + 1 + modelRow;

        // Proteção: Não deletar Linha Fixa
        if (fileIndex <= fixedDataIndex) {
            throw new IllegalArgumentException("Não é permitido excluir a linha fixa de topo.");
        }

        // Proteção: Não deletar Rodapé ou fora dos limites
        if (fileIndex >= footerIndex) {
            throw new IllegalArgumentException("Não é permitido excluir o rodapé.");
        }

        // --- 3. EXECUÇÃO ---
        List<String> mut = new ArrayList<>(linhas);

        // Remove a linha
        mut.remove(fileIndex);

        // O arquivo diminuiu de tamanho, então o rodapé agora é um índice menor
        int novoFooterIndex = mut.size() - 1;

        // --- 4. RENUMERAR ---
        // Renumera da primeira linha móvel (2) até antes do rodapé
        // Importante: Como removemos uma linha, os números de baixo precisam subir.
        // O método renumerarPaginas vai sobrescrever a coluna 0 sequencialmente (1, 2, 3...)
        renumerarPaginas(mut, fixedDataIndex + 1, novoFooterIndex - 1);

        cellLockService.shiftLocks(path, modelRow, -1);

        // --- 5. SALVAR E NOTIFICAR ---
        Files.write(abs, mut, StandardCharsets.UTF_8);

        // Avisa que a linha 'modelRow' foi deletada
        broadcaster.sendRowDeleted(new RowDeletedEvent(path, modelRow, username));
    }

    /*──────── Helpers ────────*/
    private Path resolveSheet(String relPath) {
        // adapta para o seu PathResolver real
        return pathResolver.resolveSafe(relPath);
    }

    // Certifique-se de que este método auxiliar está EXATAMENTE assim:
    private void validarLinhaLivre(String path, int modelRow, String username, List<String> linhas) {
        if (linhas.isEmpty()) {
            return;
        }

        // Descobre quantas colunas existem lendo o cabeçalho
        String header = linhas.get(0);
        int numCols = header.split(";", -1).length;

        // Varre TODAS as colunas dessa linha
        for (int col = 0; col < numCols; col++) {
            // Pergunta ao LockService quem é o dono
            String owner = cellLockService.getOwner(path, modelRow, col);

            // Se tem dono e NÃO sou eu, BLOQUEIA!
            if (owner != null && !owner.equals(username)) {
                // Esta mensagem é a que vai aparecer no seu JOptionPane
                throw new IllegalStateException("Linha bloqueada. Coluna " + (col + 1) + " em edição por: " + owner);
            }
        }
    }

}
