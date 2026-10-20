package dev.rpmixer.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lokale Pack-Bibliothek.
 * <ul>
 *   <li>"lib/&lt;Datei&gt;": von dieser Mod importierte Kopie (Ordner libraryDir)</li>
 *   <li>"rp/&lt;Datei&gt;": Original im resourcepacks-Ordner (wird nur gelesen, nie verändert)</li>
 * </ul>
 */
public final class PackLibrary {
    private final Path libraryDir;
    private final Path resourcePacksDir;
    private final String overlayDirName;
    private final Map<String, PackInfo> cache = new HashMap<>();
    private volatile Map<String, PackInfo> byId = Map.of();
    private volatile List<PackInfo> sorted = List.of();

    public PackLibrary(Path libraryDir, Path resourcePacksDir, String overlayDirName) {
        this.libraryDir = libraryDir;
        this.resourcePacksDir = resourcePacksDir;
        this.overlayDirName = overlayDirName;
    }

    public List<PackInfo> packs() {
        return sorted;
    }

    public PackInfo get(String id) {
        return byId.get(id);
    }

    public Path libraryDir() {
        return libraryDir;
    }

    /** Liest beide Quellen neu ein. Unveränderte ZIPs werden aus dem Cache übernommen. */
    public synchronized List<PackInfo> rescan(Set<String> hidden) throws IOException {
        Files.createDirectories(libraryDir);
        Map<String, PackInfo> fresh = new HashMap<>();
        scanDir(libraryDir, "lib", true, hidden, fresh);
        if (Files.isDirectory(resourcePacksDir)) scanDir(resourcePacksDir, "rp", false, hidden, fresh);
        cache.clear();
        cache.putAll(fresh);
        List<PackInfo> list = new ArrayList<>(fresh.values());
        list.sort(Comparator.comparing((PackInfo p) -> p.name.toLowerCase(Locale.ROOT)).thenComparing(p -> p.id));
        byId = Map.copyOf(fresh);
        sorted = List.copyOf(list);
        return sorted;
    }

    private void scanDir(Path dir, String prefix, boolean lib, Set<String> hidden, Map<String, PackInfo> out)
            throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                String fn = p.getFileName().toString();
                if (fn.startsWith(".") || fn.endsWith(".tmp") || fn.equals(overlayDirName)) continue;
                boolean isDir = Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS);
                boolean isZip = !isDir && Files.isRegularFile(p) && fn.toLowerCase(Locale.ROOT).endsWith(".zip");
                if (!isDir && !isZip) continue;
                String id = prefix + "/" + fn;
                if (!lib && hidden.contains(id)) continue;
                String name = isZip ? fn.substring(0, fn.length() - 4) : fn;
                String sig = isZip ? Files.getLastModifiedTime(p).toMillis() + "|" + Files.size(p)
                        : "dir|" + System.nanoTime(); // Ordner immer neu einlesen
                PackInfo old = cache.get(id);
                if (old != null && old.signature.equals(sig)) {
                    out.put(id, old);
                } else {
                    out.put(id, PackInfo.scan(p, id, name, isZip ? PackInfo.Kind.ZIP : PackInfo.Kind.DIR, lib, sig));
                }
            }
        }
    }

    /**
     * Importiert ZIP oder Ordner als lokale Kopie in die Bibliothek (die Quelle wird nur gelesen).
     * Rückgabe: Pack-ID der Kopie. Wirft IOException mit verständlicher Meldung bei ungültigen Packs.
     */
    public synchronized String importPack(Path source) throws IOException {
        if (!Files.exists(source)) throw new IOException("Pfad existiert nicht");
        boolean isDir = Files.isDirectory(source);
        // Prüfung inkl. aller Limits (wirft bei ZIP-Bomben / zu vielen Dateien)
        try (PackFiles pf = PackFiles.open(source)) {
            if (!pf.paths().contains("pack.mcmeta")) throw new IOException("pack.mcmeta fehlt im Wurzelverzeichnis");
            Files.createDirectories(libraryDir);
            String base = sanitize(source.getFileName().toString());
            if (!isDir && !base.toLowerCase(Locale.ROOT).endsWith(".zip")) base = base + ".zip";
            Path target = uniqueTarget(base);
            Path tmp = libraryDir.resolve(target.getFileName() + ".tmp");
            FileUtil.deleteTree(tmp);
            try {
                if (isDir) {
                    for (String rel : pf.paths()) {
                        Path dest = tmp.resolve(rel).normalize();
                        if (!dest.startsWith(tmp.normalize())) continue;
                        Files.createDirectories(dest.getParent());
                        try (java.io.InputStream in = pf.open(rel)) {
                            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    Files.createDirectories(tmp);
                } else {
                    Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
                }
                FileUtil.move(tmp, target);
            } catch (IOException | RuntimeException e) {
                FileUtil.deleteTree(tmp);
                throw e;
            }
            return "lib/" + target.getFileName();
        }
    }

    private Path uniqueTarget(String base) {
        Path t = libraryDir.resolve(base);
        if (!Files.exists(t, LinkOption.NOFOLLOW_LINKS)) return t;
        String stem = base.toLowerCase(Locale.ROOT).endsWith(".zip") ? base.substring(0, base.length() - 4) : base;
        String ext = base.substring(stem.length());
        for (int i = 2; ; i++) {
            Path c = libraryDir.resolve(stem + " (" + i + ")" + ext);
            if (!Files.exists(c, LinkOption.NOFOLLOW_LINKS)) return c;
        }
    }

    static String sanitize(String name) {
        String n = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (n.startsWith(".")) n = "_" + n.substring(1);
        if (n.isEmpty()) n = "pack";
        return n.length() > 100 ? n.substring(0, 100) : n;
    }

    /** Löscht NUR eine Bibliothekskopie. Originale im resourcepacks-Ordner werden hier nie angefasst. */
    public synchronized void removeCopy(String id) throws IOException {
        if (!id.startsWith("lib/")) throw new IOException("Nur Bibliothekskopien können gelöscht werden");
        String fn = id.substring(4);
        if (PackPaths.normalize(fn) == null || fn.contains("/")) throw new IOException("Ungültige ID");
        Path p = libraryDir.resolve(fn).normalize();
        if (!p.startsWith(libraryDir.normalize())) throw new IOException("Ungültiger Pfad");
        FileUtil.deleteTree(p);
    }

    public static String fileSeparatorSafe(String s) {
        return s.replace(File.separatorChar, '/');
    }
}
