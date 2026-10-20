package dev.rpmixer.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Versionsbezogene Zuordnung Kategorie -> Ressourcen (Ziel: Minecraft 1.21.11).
 * Diese Tabelle ist die einzige Stelle, die bei einem Versionswechsel angepasst werden muss.
 * Die Pfade sind aus dem Wissen über das 1.21.x-Ressourcenformat abgeleitet und werden zur Laufzeit
 * vom Mapping-Selbsttest gegen die echten Vanilla-Ressourcen geprüft (siehe MappingSelfCheck).
 */
public final class Categories {
    private Categories() {}

    public static final List<String> MATERIALS =
            List.of("wooden", "stone", "copper", "iron", "golden", "diamond", "netherite");
    public static final List<String> TOOLS = List.of("axe", "pickaxe", "shovel", "hoe");

    public static final List<CategoryDef> ALL;

    /** Custom-Sky-Formate, die ohne Zusatz-Rendering NICHT funktionieren. */
    public static final Pattern CUSTOM_SKY = Pattern.compile(
            "^assets/[^/]+/(optifine|mcpatcher)/sky/.*|^assets/[^/]+/sky/.*\\.json$|^assets/[^/]+/fabricskyboxes/.*");

    private static String a(String p) {
        return "assets/minecraft/" + p;
    }

    private static List<String> l(String... s) {
        return List.of(s);
    }

    private static Target simple(String key, List<List<String>> groups, List<String> files,
                                 List<String> prefixes, List<String> ids) {
        return new Target(key, null, groups, files, prefixes, ids);
    }

    static {
        List<CategoryDef> c = new ArrayList<>();

        String env = "textures/environment/";
        c.add(new CategoryDef("sky", false, List.of(simple("sky",
                List.of(l(a(env + "sun.png"), a(env + "celestial/sun.png")),
                        l(a(env + "moon_phases.png"), a(env + "celestial/moon/*"))),
                l(a(env + "sun.png"), a(env + "moon_phases.png"), a(env + "end_sky.png"), a(env + "clouds.png")),
                l(a(env + "celestial/")),
                l()))));

        String hud = "textures/gui/sprites/hud/";
        c.add(new CategoryDef("hotbar", false, List.of(simple("hotbar",
                List.of(l(a(hud + "hotbar.png")), l(a(hud + "hotbar_selection.png"))),
                l(a(hud + "hotbar.png"), a(hud + "hotbar_selection.png"),
                        a(hud + "hotbar_offhand_left.png"), a(hud + "hotbar_offhand_right.png")),
                l(a(hud + "hotbar_attack_indicator_")),
                l()))));

        String cont = "textures/gui/container/";
        String spr = "textures/gui/sprites/container/";
        c.add(new CategoryDef("inventory", false, List.of(simple("inventory",
                List.of(l(a(cont + "inventory.png")),
                        l(a(cont + "creative_inventory/*"), a(spr + "creative_inventory/*"))),
                l(a(cont + "inventory.png"),
                        a("textures/item/empty_armor_slot_helmet.png"),
                        a("textures/item/empty_armor_slot_chestplate.png"),
                        a("textures/item/empty_armor_slot_leggings.png"),
                        a("textures/item/empty_armor_slot_boots.png"),
                        a("textures/item/empty_armor_slot_shield.png")),
                l(a(cont + "creative_inventory/"), a(spr + "creative_inventory/"), a(spr + "inventory/")),
                l()))));

        c.add(new CategoryDef("crystals", false, List.of(simple("crystals",
                List.of(l(a("textures/entity/end_crystal/end_crystal.png")),
                        l(a("textures/item/end_crystal.png"), a("items/end_crystal.json"))),
                l(a("textures/item/end_crystal.png")),
                l(a("textures/entity/end_crystal/")),
                l("minecraft:end_crystal")))));

        for (String tool : TOOLS) c.add(materialCategory(tool, tool));
        c.add(materialCategory("sword", "sword"));

        c.add(new CategoryDef("mace", false, List.of(simple("mace",
                List.of(l(a("textures/item/mace.png"), a("items/mace.json"))),
                l(a("textures/item/mace.png")),
                l(),
                l("minecraft:mace")))));

        c.add(new CategoryDef("trident", false, List.of(simple("trident",
                List.of(l(a("textures/entity/trident.png")),
                        l(a("textures/item/trident.png"), a("items/trident.json"))),
                l(a("textures/entity/trident.png"), a("textures/entity/trident_riptide.png"),
                        a("textures/item/trident.png")),
                l(),
                l("minecraft:trident")))));

        String blk = "textures/block/";
        c.add(new CategoryDef("anchor", false, List.of(simple("anchor",
                List.of(l(a(blk + "respawn_anchor_top.png")), l(a(blk + "respawn_anchor_top_off.png")),
                        l(a(blk + "respawn_anchor_bottom.png")), l(a(blk + "respawn_anchor_side0.png")),
                        l(a(blk + "respawn_anchor_side1.png")), l(a(blk + "respawn_anchor_side2.png")),
                        l(a(blk + "respawn_anchor_side3.png")), l(a(blk + "respawn_anchor_side4.png"))),
                l(),
                l(a(blk + "respawn_anchor_")),
                l("minecraft:respawn_anchor")))));

        c.add(new CategoryDef("glowstone", false, List.of(simple("glowstone",
                List.of(l(a(blk + "glowstone.png"))),
                l(a(blk + "glowstone.png")),
                l(),
                l("minecraft:glowstone")))));

        ALL = List.copyOf(c);
    }

    private static CategoryDef materialCategory(String catId, String toolName) {
        List<Target> ts = new ArrayList<>();
        for (String m : MATERIALS) {
            String id = m + "_" + toolName;
            ts.add(new Target(catId + ":" + m, catId,
                    List.of(l(a("textures/item/" + id + ".png"), a("items/" + id + ".json"),
                            a("models/item/" + id + ".json"))),
                    l(a("textures/item/" + id + ".png")),
                    l(),
                    l("minecraft:" + id)));
        }
        return new CategoryDef(catId, true, List.copyOf(ts));
    }


    private static final List<String> PREVIEW_MATERIAL_ORDER =
            List.of("iron", "diamond", "netherite", "golden", "stone", "wooden", "copper");

    /** Repräsentative Texturen je Auswahlschlüssel für die Vorschau (in Prioritätsreihenfolge). */
    public static List<String> previewCandidates(String key) {
        String id = key;
        String mat = null;
        int i = key.indexOf(':');
        if (i >= 0) {
            id = key.substring(0, i);
            mat = key.substring(i + 1);
        }
        String env = "textures/environment/";
        switch (id) {
            case "sky":
                return l(a(env + "sun.png"), a(env + "celestial/sun.png"), a(env + "moon_phases.png"),
                        a(env + "end_sky.png"));
            case "hotbar":
                return l(a("textures/gui/sprites/hud/hotbar.png"));
            case "inventory":
                return l(a("textures/gui/container/inventory.png"));
            case "crystals":
                return l(a("textures/item/end_crystal.png"), a("textures/entity/end_crystal/end_crystal.png"));
            case "mace":
                return l(a("textures/item/mace.png"));
            case "trident":
                return l(a("textures/item/trident.png"), a("textures/entity/trident.png"));
            case "anchor":
                return l(a("textures/block/respawn_anchor_side4.png"), a("textures/block/respawn_anchor_top.png"),
                        a("textures/block/respawn_anchor_side0.png"));
            case "glowstone":
                return l(a("textures/block/glowstone.png"));
            default:
                break;
        }
        if (TOOLS.contains(id) || id.equals("sword")) {
            List<String> out = new ArrayList<>();
            if (mat != null) {
                out.add(a("textures/item/" + mat + "_" + id + ".png"));
            } else {
                for (String m : PREVIEW_MATERIAL_ORDER) out.add(a("textures/item/" + m + "_" + id + ".png"));
            }
            return out;
        }
        return List.of();
    }

    public static CategoryDef byId(String id) {
        for (CategoryDef d : ALL) if (d.id.equals(id)) return d;
        return null;
    }
}
