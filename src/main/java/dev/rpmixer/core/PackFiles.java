package dev.rpmixer.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.NavigableSet;

/** Nur-Lese-Sicht auf ein Pack (ZIP oder Ordner). Das Original wird nie verändert. */
public interface PackFiles extends AutoCloseable {
    /** Alle sicheren Dateipfade relativ zur Pack-Wurzel ("assets/minecraft/..."). */
    NavigableSet<String> paths();

    InputStream open(String path) throws IOException;

    /** Hinweise (z. B. übersprungene unsichere Pfade). */
    List<String> warnings();

    @Override
    void close();

    static PackFiles open(Path location) throws IOException {
        if (Files.isDirectory(location)) return new DirPackFiles(location);
        return new ZipPackFiles(location);
    }
}
