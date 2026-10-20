package dev.rpmixer.core;

/** Pfadvalidierung (Zip-Slip / Path-Traversal-Schutz). */
public final class PackPaths {
    private PackPaths() {}

    /** Liefert den Pfad unverändert zurück, wenn er sicher ist, sonst null. */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > Limits.MAX_PATH_LENGTH) return null;
        if (raw.indexOf('\\') >= 0 || raw.indexOf('\0') >= 0 || raw.indexOf(':') >= 0) return null;
        if (raw.startsWith("/")) return null;
        for (String seg : raw.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".") || seg.equals("..")) return null;
        }
        return raw;
    }

    public static String safeForLog(String s) {
        if (s == null) return "?";
        String t = s.length() > 80 ? s.substring(0, 80) + "…" : s;
        return t.replaceAll("\\p{Cntrl}", "?");
    }
}
