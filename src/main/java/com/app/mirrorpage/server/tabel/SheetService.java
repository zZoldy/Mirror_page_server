/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.server.tabel;

import com.app.mirrorpage.fs.PathResolver;
import com.app.mirrorpage.server.service.SheetEventBroadcaster;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

// Adicionado 'synchronized' para evitar conflito entre usuários
    public synchronized void insertRow(String relPath, int afterRow, String username) throws IOException {
        Path file = resolveSheet(relPath);

        List<String> linhas = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>();

        if (linhas.isEmpty()) {
            return;
        }

        int fixedDataIndex = 1;

        // afterRow vem do cliente. +2 para pular Header e cair depois da selecionada.
        int novaLinhaIndex = afterRow + 2;

        // Proteções de índice
        if (novaLinhaIndex >= linhas.size()) {
            novaLinhaIndex = linhas.size() - 1; // Antes do rodapé
        }
        if (novaLinhaIndex <= fixedDataIndex) {
            novaLinhaIndex = fixedDataIndex + 1; // Depois da fixa
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

        int lockStartIndex = novaLinhaIndex - 1;

        // ===== 3. Ajusta Locks =====
        // [CORREÇÃO] Move locks apenas da posição inserida para baixo
        cellLockService.shiftLocks(relPath, lockStartIndex, 1);

        // ===== 4. Renumera =====
        int footerIndex = linhas.size() - 1;
        // [CORREÇÃO] Começa de fixedDataIndex (1) para garantir sequencia 1, 2, 3...
        renumerarPaginas(linhas, fixedDataIndex + 1, linhas.size() - 2);

        // ===== 5. Salva =====
        Files.write(file, linhas, StandardCharsets.UTF_8);

        // ===== 6. Notifica =====
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

    public List<String> listarPastasRaiz() {
        // Resolve o caminho
        Path dirProdutos = pathResolver.resolveSafe("");

        // --- LOGS DE RASTREAMENTO ---
        System.out.println("========================================");
        System.out.println("[SERVER DEBUG] 1. Tentando ler pastas em:");
        System.out.println("   -> " + dirProdutos.toAbsolutePath());
        System.out.println("[SERVER DEBUG] 2. A pasta existe? " + Files.exists(dirProdutos));
        System.out.println("[SERVER DEBUG] 3. É um diretório? " + Files.isDirectory(dirProdutos));

        if (Files.exists(dirProdutos)) {
            try {
                System.out.println("[SERVER DEBUG] 4. Conteúdo encontrado:");
                Files.list(dirProdutos).forEach(p -> System.out.println("   - " + p.getFileName()));
            } catch (IOException e) {
                System.out.println("   (Erro ao listar conteúdo)");
            }
        }
        System.out.println("========================================");
        // -----------------------------

        if (!Files.exists(dirProdutos) || !Files.isDirectory(dirProdutos)) {
            return Collections.emptyList();
        }

        try (Stream<Path> stream = Files.list(dirProdutos)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
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

    public synchronized void copyRowToFinal(String sourcePath, int sourceRow, String targetPath, String user) throws IOException {

        // --- 1. PREPARAÇÃO DA ORIGEM (PRELIM) ---
        Path srcCsv = pathResolver.resolveSafe(sourcePath);
        if (!Files.exists(srcCsv)) {
            throw new FileNotFoundException("Prelim não encontrado");
        }

        List<String> srcLines = Files.readAllLines(srcCsv, StandardCharsets.UTF_8);

        // Validações básicas
        if (sourceRow + 1 >= srcLines.size()) {
            throw new IllegalArgumentException("A linha de origem não existe mais.");
        }
        validarLinhaLivre(sourcePath, sourceRow, user, srcLines);

        // --- LÓGICA DO CONTADOR (INCREMENTA COLUNA 1) ---
        String lineContent = srcLines.get(sourceRow + 1);
        String[] columns = lineContent.split(";", -1);
        int numCols = srcLines.get(0).split(";", -1).length; // Total colunas pelo header

        // Expande array se necessário
        if (columns.length < numCols) {
            String[] newCols = new String[numCols];
            System.arraycopy(columns, 0, newCols, 0, columns.length);
            for (int i = 0; i < numCols; i++) {
                if (newCols[i] == null) {
                    newCols[i] = "";
                }
            }
            columns = newCols;
        }

        // Incrementa
        int contador = 0;
        try {
            if (!columns[1].trim().isEmpty()) {
                contador = Integer.parseInt(columns[1].trim());
            }
        } catch (Exception e) {
            contador = 0;
        }

        contador++;
        columns[1] = String.valueOf(contador);
        String lineContentUpdated = String.join(";", columns);

        // Salva Prelim
        srcLines.set(sourceRow + 1, lineContentUpdated);
        Files.write(srcCsv, srcLines, StandardCharsets.UTF_8);

        // --- 2. TRATAMENTO DO DESTINO (FINAL) COM RODAPÉ FIXO ---
        Path tgtCsv = pathResolver.resolveSafe(targetPath);

        // Se não existir, cria Header + Rodapé inicial
        if (!Files.exists(tgtCsv)) {
            Files.createFile(tgtCsv);
            String header = srcLines.get(0);
            String footer = ";".repeat(Math.max(0, numCols - 1)); // Rodapé vazio inicial
            // Se tiver um texto padrão de rodapé, coloque aqui
            Files.writeString(tgtCsv, header + "\n" + footer);
        }

        List<String> tgtLines = Files.readAllLines(tgtCsv, StandardCharsets.UTF_8);

        // 🟢 PASSO A: REMOVER O RODAPÉ (Última linha)
        // Guardamos ela na memória para colocar de volta no fim
        String fixedFooterRow = "";
        if (tgtLines.size() > 1) { // Tem Header + Pelo menos 1 linha
            int lastIndex = tgtLines.size() - 1;
            fixedFooterRow = tgtLines.remove(lastIndex); // Remove a última linha da lista
        } else {
            // Fallback se o arquivo estiver corrompido (só header)
            fixedFooterRow = ";".repeat(Math.max(0, numCols - 1));
        }

        // 🟢 PASSO B: PREENCHER VAZIOS (PADDING)
        // O alvo é sourceRow + 1 (por causa do header 0)
        int targetListIndex = sourceRow + 1;

        // Enquanto a lista (sem o rodapé) for menor que o índice alvo, enche de linhas vazias
        while (tgtLines.size() <= targetListIndex) {
            String emptyLine = ";".repeat(Math.max(0, numCols - 1));
            tgtLines.add(emptyLine);
        }

        // 🟢 PASSO C: VERIFICAR LOCK E INSERIR
        // Verifica se alguém está editando a linha onde vamos escrever
        validarLinhaLivre(targetPath, sourceRow, user, tgtLines);

        // Sobrescreve a linha alvo com os dados novos
        tgtLines.set(targetListIndex, lineContentUpdated);

        // 🟢 PASSO D: DEVOLVER O RODAPÉ
        // Adiciona a linha fixa no final de tudo
        tgtLines.add(fixedFooterRow);

        // Salva o Final
        Files.write(tgtCsv, tgtLines, StandardCharsets.UTF_8);

        // --- 3. CÓPIA DA LAUDA ---
        Path srcLaudaDir = resolveLaudaDir(sourcePath);
        Path tgtLaudaDir = resolveLaudaDir(targetPath);
        if (!Files.exists(tgtLaudaDir)) {
            Files.createDirectories(tgtLaudaDir);
        }

        Path srcTxt = srcLaudaDir.resolve(sourceRow + ".txt");
        Path tgtTxt = tgtLaudaDir.resolve(sourceRow + ".txt");

        if (Files.exists(srcTxt)) {
            Files.copy(srcTxt, tgtTxt, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.deleteIfExists(tgtTxt);
        }

        System.out.println("[COPY] Sucesso. Linha " + sourceRow + " inserida. Rodapé empurrado para linha " + (tgtLines.size() - 1));
    }

    // 👇 ADICIONE ESTE MÉTODO PRIVADO NA SUA CLASSE SheetService
    // Ele converte "/BDBR/Prelim.csv" em um Path para ".../laudas/_BDBR_Prelim"
    private Path resolveLaudaDir(String csvRelPath) {
        // 1. Remove a extensão .csv
        String cleanName = csvRelPath.replace(".csv", "");

        // 2. Troca as barras / ou \ por _ (para criar um nome de pasta plano)
        // Ex: "BDBR/Prelim" vira "BDBR_Prelim"
        String folderName = cleanName.replaceAll("[\\\\/]", "_");

        // 3. Garante que começa com _ (padrão que vimos nos seus logs)
        if (!folderName.startsWith("_")) {
            folderName = "_" + folderName;
        }

        // 4. Usa o pathResolver para pegar o caminho completo dentro da pasta "laudas"
        return pathResolver.resolveSafe("laudas/" + folderName);
    }
}
