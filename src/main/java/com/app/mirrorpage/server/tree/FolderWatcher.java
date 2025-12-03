package com.app.mirrorpage.server.tree;

import com.app.mirrorpage.fs.PathResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import java.nio.file.*;

@Service
public class FolderWatcher implements InitializingBean {

    private final Path ROOT_FS;
    private final WatchService watchService;
    private final TreeEventBroadcaster broadcaster;

    public FolderWatcher(TreeEventBroadcaster broadcaster, PathResolver resolver) throws java.io.IOException {
        this.ROOT_FS = resolver.getRoot().normalize();   // vem do application.yml
        this.watchService = FileSystems.getDefault().newWatchService();
        this.broadcaster = broadcaster;

        System.out.println("[FolderWatcher] ROOT_FS = " + ROOT_FS.toAbsolutePath());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // registra raiz e subpastas
        Files.walk(ROOT_FS)
                .filter(Files::isDirectory)
                .filter(p -> {
                    String rel = ROOT_FS.relativize(p).toString().replace("\\", "/");
                    return !rel.toLowerCase().startsWith("laudas") && !rel.toLowerCase().startsWith("/laudas");
                })
                .forEach(dir -> {
                    try {
                        dir.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_DELETE,
                                StandardWatchEventKinds.ENTRY_MODIFY
                        );
                        System.out.println("[FolderWatcher] monitorando: " + dir);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

        Thread t = new Thread(this::loop, "mirrorpage-folder-watcher");
        t.setDaemon(true);
        t.start();
    }

    private void loop() {
        System.out.println("[FolderWatcher] iniciado...");
        while (true) {
            try {
                WatchKey key = watchService.take(); // bloqueia até ter evento
                Path dir = (Path) key.watchable();

                for (WatchEvent<?> ev : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = ev.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    Path name = (Path) ev.context();
                    Path fullPath = dir.resolve(name).normalize();

                    // 🛑 FILTRO 2: Ignorar eventos vindos da pasta 'laudas'
                    String relPath = ROOT_FS.relativize(fullPath).toString().replace("\\", "/");
                    if (relPath.toLowerCase().startsWith("laudas") || relPath.toLowerCase().startsWith("/laudas")) {
                        continue;
                    }
                    
                    boolean isDir = Files.isDirectory(fullPath);

                    System.out.println("[FolderWatcher] " + kind.name() + " em " + fullPath);

                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        broadcaster.onCreate(fullPath, isDir);

                        // se criou uma pasta, começa a monitorar ela também
                        if (isDir) {
                            try {
                                fullPath.register(
                                        watchService,
                                        StandardWatchEventKinds.ENTRY_CREATE,
                                        StandardWatchEventKinds.ENTRY_DELETE,
                                        StandardWatchEventKinds.ENTRY_MODIFY
                                );
                                System.out.println("[FolderWatcher] nova pasta monitorada: " + fullPath);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                    } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        broadcaster.onDelete(fullPath, isDir);

                    } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        broadcaster.onModify(fullPath, isDir);
                    }
                }

                key.reset();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
