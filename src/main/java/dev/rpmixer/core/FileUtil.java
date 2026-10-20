package dev.rpmixer.core;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

public final class FileUtil {
    private FileUtil() {}

    /** Löscht einen Ordnerbaum, ohne Symlinks zu folgen. */
    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.delete(f);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Verschiebt möglichst atomar; fällt bei Bedarf auf normales Verschieben zurück. */
    public static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to);
        }
    }

    /**
     * Ersetzt {@code target} durch {@code fresh}. Der alte Stand wird zuerst nach {@code trash} verschoben und
     * bei einem Fehler wiederhergestellt, sodass nie ein halb fertiger Zustand zurückbleibt.
     */
    public static void replaceDirectory(Path fresh, Path target, Path trash) throws IOException {
        Files.createDirectories(trash.getParent());
        deleteTree(trash);
        boolean hadOld = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (hadOld) move(target, trash);
        try {
            move(fresh, target);
        } catch (IOException | RuntimeException e) {
            if (hadOld) move(trash, target);
            throw e;
        }
        deleteTree(trash);
    }
}
