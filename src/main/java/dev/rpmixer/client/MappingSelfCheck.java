package dev.rpmixer.client;

import dev.rpmixer.core.CategoryDef;
import dev.rpmixer.core.Categories;
import dev.rpmixer.core.Target;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Prüft einmalig, ob die Pfade der Zuordnungstabelle (Categories) in den geladenen Ressourcen vorkommen.
 * Fehlende Pfade deuten auf eine abweichende Ressourcenstruktur der Zielversion hin und werden geloggt.
 */
final class MappingSelfCheck {
    private static boolean done;

    private MappingSelfCheck() {}

    static void runOnce(MinecraftClient mc) {
        if (done) return;
        done = true;
        ResourceManager rm = mc.getResourceManager();
        List<String> missing = new ArrayList<>();
        for (CategoryDef c : Categories.ALL) {
            for (Target t : c.targets) {
                for (List<String> g : t.groups) {
                    boolean checkable = false;
                    boolean found = false;
                    for (String alt : g) {
                        if (alt.endsWith("*")) continue;
                        checkable = true;
                        if (exists(rm, alt)) {
                            found = true;
                            break;
                        }
                    }
                    if (checkable && !found) missing.add(t.key + " -> " + g);
                }
            }
        }
        if (missing.isEmpty()) {
            RpmClient.LOG.info("Mapping-Selbsttest: alle Kategorie-Pfade in den geladenen Ressourcen gefunden.");
        } else {
            RpmClient.LOG.warn("Mapping-Selbsttest: {} Pfadgruppen nicht gefunden (Categories.java prüfen):",
                    missing.size());
            for (String m : missing) RpmClient.LOG.warn("  fehlt: {}", m);
        }
    }

    private static boolean exists(ResourceManager rm, String assetPath) {
        if (!assetPath.startsWith("assets/")) return false;
        String[] p = assetPath.substring("assets/".length()).split("/", 2);
        if (p.length < 2) return false;
        Identifier id = Identifier.tryParse(p[0] + ":" + p[1]);
        return id != null && rm.getResource(id).isPresent();
    }
}
