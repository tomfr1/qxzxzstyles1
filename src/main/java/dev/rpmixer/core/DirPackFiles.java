package dev.rpmixer.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.stream.Stream;

/** Entpackter Pack-Ordner. Symlinks werden nicht verfolgt. */
public final class DirPackFiles implements PackFiles {
    private final Path root;
    private final TreeSet<String> paths = new TreeSet<>();
    private final List<String> warnings = new ArrayList<>();

    public DirPackFiles(Path root) throws IOException {
        this.root = root;
        long total = 0;
        int count = 0;
        try (Stream<Path> s = Files.walk(root)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                if (!Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)) continue;
                if (++count > Limits.MAX_ENTRIES) {
                    throw new IOException("Zu viele Dateien im Pack (Limit " + Limits.MAX_ENTRIES + ")");
                }
                String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                String norm = PackPaths.normalize(rel);
                if (norm == null) {
                    warnings.add("Unsicherer Pfad übersprungen: " + PackPaths.safeForLog(rel));
                    continue;
                }
                long size = Files.size(p);
                if (size > Limits.MAX_ENTRY_BYTES) throw new IOException("Datei zu groß: " + norm);
                total += size;
                if (total > Limits.MAX_TOTAL_BYTES) throw new IOException("Gesamtgröße über dem Limit");
                paths.add(norm);
            }
        }
    }

    @Override
    public NavigableSet<String> paths() {
        return paths;
    }

    @Override
    public InputStream open(String path) throws IOException {
        if (!paths.contains(path)) throw new IOException("Nicht im Pack: " + path);
        return new LimitedInputStream(Files.newInputStream(root.resolve(path)), Limits.MAX_ENTRY_BYTES);
    }

    @Override
    public List<String> warnings() {
        return warnings;
    }

    @Override
    public void close() {
        // nichts zu schließen
    }
}
