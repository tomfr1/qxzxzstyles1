package dev.rpmixer.core;

/** Sicherheitsgrenzen für Pack-Import und Overlay-Erzeugung. */
public final class Limits {
    private Limits() {}

    public static final int MAX_ENTRIES = 60_000;
    public static final long MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024; // 2 GiB entpackt
    public static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;      // 256 MiB je Datei
    public static final long MIN_SIZE_FOR_RATIO = 1024 * 1024;          // Ratio-Check erst ab 1 MiB
    public static final long MAX_RATIO = 200;                           // entpackt / komprimiert
    public static final int MAX_PATH_LENGTH = 260;
    public static final long MAX_OVERLAY_BYTES = 1024L * 1024 * 1024;   // 1 GiB Overlay
}
