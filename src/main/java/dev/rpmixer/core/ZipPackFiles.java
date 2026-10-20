package dev.rpmixer.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** ZIP-Pack. Es wird nie entpackt; Einträge werden nur gestreamt. */
public final class ZipPackFiles implements PackFiles {
    private final ZipFile zip;
    private final TreeSet<String> paths = new TreeSet<>();
    private final Map<String, ZipEntry> entries = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();

    public ZipPackFiles(Path file) throws IOException {
        this.zip = new ZipFile(file.toFile());
        try {
            long total = 0;
            int count = 0;
            Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                if (++count > Limits.MAX_ENTRIES) {
                    throw new IOException("Zu viele Dateien im Pack (Limit " + Limits.MAX_ENTRIES + ")");
                }
                String p = PackPaths.normalize(e.getName());
                if (p == null) {
                    warnings.add("Unsicherer Pfad übersprungen: " + PackPaths.safeForLog(e.getName()));
                    continue;
                }
                long size = e.getSize();
                long comp = e.getCompressedSize();
                if (size > Limits.MAX_ENTRY_BYTES) throw new IOException("Datei zu groß: " + p);
                if (size > Limits.MIN_SIZE_FOR_RATIO && comp > 0 && size / comp > Limits.MAX_RATIO) {
                    throw new IOException("Auffälliges Kompressionsverhältnis: " + p);
                }
                total += Math.max(size, 0);
                if (total > Limits.MAX_TOTAL_BYTES) throw new IOException("Entpackte Gesamtgröße über dem Limit");
                paths.add(p);
                entries.put(p, e);
            }
        } catch (IOException | RuntimeException ex) {
            zip.close();
            throw ex;
        }
    }

    @Override
    public NavigableSet<String> paths() {
        return paths;
    }

    @Override
    public InputStream open(String path) throws IOException {
        ZipEntry e = entries.get(path);
        if (e == null) throw new FileNotFoundException(path);
        return new LimitedInputStream(zip.getInputStream(e), Limits.MAX_ENTRY_BYTES);
    }

    @Override
    public List<String> warnings() {
        return warnings;
    }

    @Override
    public void close() {
        try {
            zip.close();
        } catch (IOException ignored) {
            // nichts zu tun
        }
    }
}
