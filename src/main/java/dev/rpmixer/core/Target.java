package dev.rpmixer.core;

import java.util.List;

/**
 * Kleinste auswählbare Einheit (z. B. "hotbar" oder "axe:iron").
 *
 * <ul>
 *   <li>{@code groups}: Statusprüfung. Jede Gruppe ist erfüllt, wenn mindestens eine Alternative im Pack existiert.
 *       Alternativen mit Endung '*' sind Präfix-Tests.</li>
 *   <li>{@code files}: Einzeldateien, die (samt .mcmeta) übernommen werden, wenn vorhanden.</li>
 *   <li>{@code prefixes}: Alle Dateien mit diesem Pfadpräfix werden übernommen.</li>
 *   <li>{@code ids}: Item-/Block-IDs ("minecraft:mace"); deren Item-Definition, Modelle, Blockstates und
 *       referenzierte Texturen werden aufgelöst.</li>
 * </ul>
 */
public final class Target {
    public final String key;
    public final String parentKey;
    public final List<List<String>> groups;
    public final List<String> files;
    public final List<String> prefixes;
    public final List<String> ids;

    public Target(String key, String parentKey, List<List<String>> groups, List<String> files,
                  List<String> prefixes, List<String> ids) {
        this.key = key;
        this.parentKey = parentKey;
        this.groups = groups;
        this.files = files;
        this.prefixes = prefixes;
        this.ids = ids;
    }
}
