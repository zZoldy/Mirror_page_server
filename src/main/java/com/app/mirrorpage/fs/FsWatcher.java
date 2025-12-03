/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.app.mirrorpage.fs;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class FsWatcher {

    private final Path ROOT;

    public FsWatcher(PathResolver resolver) {
        this.ROOT = resolver.getRoot();   // 💯 sempre funciona
    }

    private final Map<Path, WatchKey> keys = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        new Thread(this::watchLoop, "mirrorpage-fs-watcher").start();
    }

    private void watchLoop() {
        try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
            registerAll(ROOT, watcher);

            while (true) {
                WatchKey key = watcher.take();
                Path dir = (Path) key.watchable();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    Path name = (Path) event.context();
                    Path changed = dir.resolve(name).normalize();

                    String relPath = "/" + ROOT.relativize(changed).toString().replace("\\", "/");

                    // 🛑 FILTRO: Use toLowerCase() para garantir que pegue "laudas" ou "Laudas"
                    if (relPath.toLowerCase().startsWith("/laudas")) {
                        continue;
                    }

                    if (Files.isDirectory(changed) && kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        registerAll(changed, watcher); // monitora novas subpastas (exceto laudas, pois o filtro acima já barrou)
                    }

                    String type = switch (kind.name()) {
                        case "ENTRY_CREATE" ->
                            "CREATE";
                        case "ENTRY_DELETE" ->
                            "DELETE";
                        case "ENTRY_MODIFY" ->
                            "MODIFY";
                        default ->
                            kind.name();
                    };

                    String parent = "/" + ROOT.relativize(changed.getParent()).toString().replace("\\", "/");
                    if ("/.".equals(parent)) {
                        parent = "/";
                    }

                    TreeChangeBus.publish(new TreeChangeBus.ChangeDto(type, relPath, null, parent, Files.isDirectory(changed)));
                    System.out.println("[FS] " + type + " " + relPath);
                }
                key.reset();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void registerAll(Path start, WatchService watcher) throws IOException {
        Files.walk(start)
                .filter(Files::isDirectory)
                .filter(p -> {
                    String rel = "/" + ROOT.relativize(p).toString().replace("\\", "/");
                    return !rel.startsWith("/laudas");
                })
                .forEach(p -> {
                    try {
                        WatchKey k = p.register(watcher,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY);
                        keys.put(p, k);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
    }
}
