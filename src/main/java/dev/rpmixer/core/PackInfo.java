package dev.rpmixer.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Eingelesenes Pack mit Dateiindex. Unveränderlich; wird in {@link PackLibrary} gecacht. */
public final class PackInfo {
    public enum Kind { ZIP, DIR }

    public final String id;
    public final String name;
    public final Path location;
    public final Kind kind;
    /** true = lokale Kopie in der Mod-Bibliothek, false = Pack aus dem resourcepacks-Ordner (Original). */
    public final boolean inLibrary;
    public final boolean valid;
    public final String error;
    public final NavigableSet<String> paths;
    public final List<String> warnings;
    public final boolean customSky;
    final String signature;

    private PackInfo(String id, String name, Path location, Kind kind, boolean inLibrary, boolean valid,
                     String error, NavigableSet<String> paths, List<String> warnings, boolean customSky,
                     String signature) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.kind = kind;
        this.inLibrary = inLibrary;
        this.valid = valid;
        this.error = error;
        this.paths = paths;
        this.warnings = warnings;
        this.customSky = customSky;
        this.signature = signature;
    }

    public static PackInfo scan(Path location, String id, String name, Kind kind, boolean inLibrary,
                                String signature) {
        try (PackFiles pf = PackFiles.open(location)) {
            NavigableSet<String> paths = new TreeSet<>(pf.paths());
            boolean sky = false;
            for (String p : paths) {
                if (Categories.CUSTOM_SKY.matcher(p).matches()) {
                    sky = true;
                    break;
                }
            }
            if (!paths.contains("pack.mcmeta")) {
                return new PackInfo(id, name, location, kind, inLibrary, false,
                        "pack.mcmeta fehlt im Wurzelverzeichnis (evtl. liegt alles in einem Unterordner)",
                        Collections.emptyNavigableSet(), List.copyOf(pf.warnings()), false, signature);
            }
            return new PackInfo(id, name, location, kind, inLibrary, true, null, paths,
                    List.copyOf(pf.warnings()), sky, signature);
        } catch (IOException | RuntimeException e) {
            return new PackInfo(id, name, location, kind, inLibrary, false,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    Collections.emptyNavigableSet(), List.of(), false, signature);
        }
    }


    public String signature() {
        return signature;
    }

    /**
     * Liest EINE Datei aus dem Pack (für Vorschaubilder), ohne das ganze Pack neu zu indizieren.
     * Nur Pfade aus dem geprüften Index sind erlaubt; die Größe ist auf {@code max} Bytes begrenzt.
     */
    public byte[] readBytes(String path, long max) throws IOException {
        if (!valid || !paths.contains(path)) throw new FileNotFoundException(PackPaths.safeForLog(path));
        if (kind == Kind.DIR) {
            Path f = location.resolve(path).normalize();
            if (!f.startsWith(location.normalize())) throw new IOException("Ungültiger Pfad");
            if (Files.size(f) > max) throw new IOException("Datei zu groß");
            return Files.readAllBytes(f);
        }
        try (ZipFile z = new ZipFile(location.toFile())) {
            ZipEntry e = z.getEntry(path);
            if (e == null) throw new FileNotFoundException(PackPaths.safeForLog(path));
            try (InputStream in = new LimitedInputStream(z.getInputStream(e), max)) {
                return in.readAllBytes();
            }
        }
    }

    /** Erster vorhandener Vorschaupfad für den Auswahlschlüssel (z. B. "hotbar", "axe", "axe:iron") oder null. */
    public String previewPath(String key) {
        if (!valid) return null;
        for (String c : Categories.previewCandidates(key)) if (paths.contains(c)) return c;
        return null;
    }

    public boolean has(String alternative) {
        if (alternative.endsWith("*")) return hasPrefix(alternative.substring(0, alternative.length() - 1));
        return paths.contains(alternative);
    }

    public boolean hasPrefix(String prefix) {
        String c = paths.ceiling(prefix);
        return c != null && c.startsWith(prefix);
    }

    public List<String> withPrefix(String prefix) {
        List<String> out = new ArrayList<>();
        for (String s : paths.tailSet(prefix, true)) {
            if (!s.startsWith(prefix)) break;
            out.add(s);
        }
        return out;
    }

    public Status status(List<Target> targets) {
        if (!valid) return Status.NONE;
        int total = 0, ok = 0;
        for (Target t : targets) {
            for (List<String> group : t.groups) {
                total++;
                for (String alt : group) {
                    if (has(alt)) {
                        ok++;
                        break;
                    }
                }
            }
        }
        if (total == 0 || ok == 0) return Status.NONE;
        return ok == total ? Status.AVAILABLE : Status.PARTIAL;
    }
}
