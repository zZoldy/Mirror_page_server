package com.app.mirrorpage.server.tabel;

import com.app.mirrorpage.fs.PathResolver;
import com.app.mirrorpage.server.service.SheetEventBroadcaster;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Service
public class SheetService {

    private final PathResolver pathResolver;
    private final SheetEventBroadcaster broadcaster;
    private final CellLockService lockService; // Injeção do serviço de locks

    public SheetService(PathResolver pathResolver,
                        SheetEventBroadcaster broadcaster,
                        CellLockService lockService) {
        this.pathResolver = pathResolver;
        this.broadcaster = broadcaster;
        this.lockService = lockService;
    }

    /**
     * Carrega o conteúdo do CSV como String única.
     */
    public String loadSheet(String relPath) throws IOException {
        Path file = resolveSheet(relPath);
        if (!Files.exists(file)) {
            return "";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * Insere uma nova linha, empurra locks para baixo e renumera.
     */
    public void insertRow(String relPath, int afterRow, String username) throws IOException {
        Path file = resolveSheet(relPath);

        List<String> linhas = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : new ArrayList<>();

        // Validação mínima de estrutura
        if (linhas.size() < 2) return;

        int fixedDataIndex = 1; // Linha Fixa (Índice 1)

        // Calcula onde inserir (afterRow vem do cliente, +2 para pular Header e cair depois da selecionada)
        int novaLinhaIndex = afterRow + 2;

        // Proteção: Não inserir antes dos dados móveis
        if (novaLinhaIndex <= fixedDataIndex) {
            novaLinhaIndex = fixedDataIndex + 1;
        }
        // Proteção: Não inserir depois do rodapé
        if (novaLinhaIndex >= linhas.size()) {
            novaLinhaIndex = linhas.size() - 1;
        }

        // Monta a nova linha (Ex: "0;...;00:00")
        String header = linhas.get(0);
        int numCols = header.split(";", -1).length;
        String novaLinha = criarLinhaVazia(numCols);

        // 1. Insere na lista
        linhas.add(novaLinhaIndex, novaLinha);

        // 2. Empurra os Locks para baixo (+1)
        lockService.shiftLocks(relPath, novaLinhaIndex, 1);

        // 3. Renumera páginas (da primeira linha móvel até antes do rodapé)
        int footerIndex = linhas.size() - 1;
        renumerarPaginas(linhas, fixedDataIndex + 1, footerIndex - 1);

        // 4. Salva e Notifica
        Files.write(file, linhas, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        
        SheetRowInsertedEvent ev = new SheetRowInsertedEvent(relPath, afterRow, username);
        broadcaster.sendRowInserted(ev);
    }

    /**
     * Move uma linha de lugar, validando locks e renumerando.
     */
    public void moveRow(String path, int from, int to, String username) throws Exception {
        Path abs = resolveSheet(path);
        List<String> linhas = Files.readAllLines(abs, StandardCharsets.UTF_8);

        if (linhas.size() < 4) return;

        // 1. Valida se a linha de origem está livre de locks de outros usuários
        validarLinhaLivre(path, from, username, linhas);

        int headerIndex = 0;
        int fixedDataIndex = 1;
        int footerIndex = linhas.size() - 1;

        // Índices reais no arquivo
        int realFrom = headerIndex + 1 + from;
        int realTo   = headerIndex + 1 + to;

        // Proteções de Segurança (Topo e Rodapé)
        if (realFrom <= fixedDataIndex || realTo <= fixedDataIndex) return;
        if (realFrom >= footerIndex || realTo >= footerIndex) return;
        if (realFrom == realTo) return;

        // 2. Executa o Movimento
        List<String> mut = new ArrayList<>(linhas);
        String linhaMovida = mut.remove(realFrom);
        
        // Adiciona exatamente na posição de destino (sem decremento)
        mut.add(realTo, linhaMovida);

        // OBS: No movimento, o lock "viaja" com a linha? 
        // Geralmente em drag&drop soltamos o lock. Se precisar mover lock, seria complexo aqui.
        // Assumimos que ao mover, os locks daquela linha específica são liberados ou mantidos na posição física.
        // Se quiser mover locks: lockService.moveLock(path, realFrom, realTo); (Lógica customizada necessária)

        // 3. Renumera
        renumerarPaginas(mut, fixedDataIndex + 1, footerIndex - 1);

        // 4. Salva e Notifica
        Files.write(abs, mut, StandardCharsets.UTF_8);
        broadcaster.sendRowMoved(new RowMoveEvent(path, from, to, username));
    }

    /**
     * Exclui uma linha, puxa locks para cima e renumera.
     */
    public void deleteRow(String path, int modelRow, String username) throws Exception {
        Path abs = resolveSheet(path);
        List<String> linhas = Files.readAllLines(abs, StandardCharsets.UTF_8);

        if (linhas.size() < 3) return;

        // 1. Valida se a linha tem locks ativos
        validarLinhaLivre(path, modelRow, username, linhas);

        int headerIndex = 0;
        int fixedDataIndex = 1;
        int footerIndex = linhas.size() - 1;

        int fileIndex = headerIndex + 1 + modelRow;

        // Proteções
        if (fileIndex <= fixedDataIndex) throw new IllegalArgumentException("Linha fixa não pode ser excluída.");
        if (fileIndex >= footerIndex) throw new IllegalArgumentException("Rodapé não pode ser excluído.");

        // 2. Remove a linha
        List<String> mut = new ArrayList<>(linhas);
        mut.remove(fileIndex);

        // 3. Puxa os Locks de baixo para cima (-1)
        // Começando da linha que ocupou o lugar da excluída
        lockService.shiftLocks(path, fileIndex + 1, -1);

        // 4. Renumera
        int novoFooterIndex = mut.size() - 1;
        renumerarPaginas(mut, fixedDataIndex + 1, novoFooterIndex - 1);

        // 5. Salva e Notifica
        Files.write(abs, mut, StandardCharsets.UTF_8);
        broadcaster.sendRowDeleted(new RowDeletedEvent(path, modelRow, username));
    }

    // =========================================================================
    // MÉTODOS AUXILIARES PRIVADOS
    // =========================================================================

    private Path resolveSheet(String relPath) {
        return pathResolver.resolveSafe(relPath);
    }

    /**
     * Garante que a Coluna 0 tenha numeração sequencial (1, 2, 3...)
     */
    private void renumerarPaginas(List<String> linhas, int startRow, int endRow) {
        int numeroPagina = 1;
        String sep = ";";

        for (int i = startRow; i <= endRow; i++) {
            String linha = linhas.get(i);
            String[] cols = linha.split(java.util.regex.Pattern.quote(sep), -1);

            if (cols.length > 0) {
                String novoNum = String.valueOf(numeroPagina);
                if (!cols[0].equals(novoNum)) {
                    cols[0] = novoNum;
                    linhas.set(i, String.join(sep, cols));
                }
            }
            numeroPagina++;
        }
    }

    /**
     * Verifica se existe algum lock de OUTRO usuário na linha.
     */
    private void validarLinhaLivre(String path, int modelRow, String username, List<String> linhas) {
        if (linhas.isEmpty()) return;
        String header = linhas.get(0);
        int numCols = header.split(";", -1).length;

        for (int col = 0; col < numCols; col++) {
            String owner = lockService.getOwner(path, modelRow, col);
            if (owner != null && !owner.equals(username)) {
                throw new IllegalStateException("Linha bloqueada. Coluna " + (col + 1) + " em edição por: " + owner);
            }
        }
    }

    private String criarLinhaVazia(int numCols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < numCols; i++) {
            if (i > 0) sb.append(';');
            if (i == 0) sb.append("0"); // Num Pag provisório
            else if (i == 8 || i == 9 || i == 10) sb.append("00:00");
            else if (i == 13) sb.append("00:00:00");
            else sb.append("");
        }
        return sb.toString();
    }
}