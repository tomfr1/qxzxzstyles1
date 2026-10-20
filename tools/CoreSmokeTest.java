import dev.rpmixer.core.*;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Läuft ohne Minecraft: java -cp <classes>:<gson.jar> tools/CoreSmokeTest.java */
public class CoreSmokeTest {
    static int fails = 0;

    static void check(boolean ok, String what) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
        if (!ok) fails++;
    }

    static void zip(Path out, Map<String, String> files) throws Exception {
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(out))) {
            for (var e : files.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }

    public static void main(String[] a) throws Exception {
        Path t = Files.createTempDirectory("rpmtest");
        Path rp = Files.createDirectories(t.resolve("resourcepacks"));
        Path lib = t.resolve("library");
        String M = "assets/minecraft/";

        // Pack A: Hotbar + Crystals (mit Modell/Texturabhängigkeit) + Schwert-Textur + Inventar
        Map<String, String> A = new LinkedHashMap<>();
        A.put("pack.mcmeta", "{\"pack\":{\"pack_format\":75,\"description\":\"A\"}}");
        A.put(M + "textures/gui/sprites/hud/hotbar.png", "HOTBAR_A");
        A.put(M + "textures/gui/sprites/hud/hotbar_selection.png", "SEL_A");
        A.put(M + "textures/gui/container/inventory.png", "INV_A");
        A.put(M + "textures/entity/end_crystal/end_crystal.png", "CRY_A");
        A.put(M + "textures/item/end_crystal.png", "CRYITEM_A");
        A.put(M + "items/end_crystal.json", "{\"model\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/end_crystal\"}}");
        A.put(M + "models/item/end_crystal.json", "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/end_crystal\"}}");
        A.put(M + "models/item/generated.json", "{\"SHOULD\":\"NEVER_BE_COPIED\"}");
        A.put(M + "textures/item/diamond_sword.png", "SWORD_A");
        A.put(M + "textures/item/diamond_sword.png.mcmeta", "{\"animation\":{}}");
        A.put(M + "textures/item/iron_sword.png", "IRONSWORD_A");
        A.put(M + "textures/gui/sprites/hud/heart/full.png", "HEART_A_MUST_NOT_COPY");
        Path pa = rp.resolve("PackA.zip");
        zip(pa, A);

        // Pack B: Hotbar anders + Anker (blockstate -> Modelle -> Texturen) + Custom-Sky-Format
        Map<String, String> B = new LinkedHashMap<>();
        B.put("pack.mcmeta", "{\"pack\":{\"pack_format\":75,\"description\":\"B\"}}");
        B.put(M + "textures/gui/sprites/hud/hotbar.png", "HOTBAR_B");
        B.put(M + "textures/entity/end_crystal/end_crystal.png", "CRY_B");
        B.put(M + "blockstates/respawn_anchor.json",
                "{\"variants\":{\"charges=0\":{\"model\":\"minecraft:block/respawn_anchor_0\"},\"charges=1\":{\"model\":\"minecraft:block/respawn_anchor_1\"}}}");
        B.put(M + "models/block/respawn_anchor_0.json",
                "{\"parent\":\"minecraft:block/cube\",\"textures\":{\"top\":\"minecraft:block/respawn_anchor_top_off\",\"side\":\"minecraft:block/respawn_anchor_side0\"}}");
        B.put(M + "textures/block/respawn_anchor_top_off.png", "ANCH_TOP_B");
        B.put(M + "textures/block/respawn_anchor_side0.png", "ANCH_S0_B");
        B.put(M + "optifine/sky/world0/sky1.properties", "source=sky.png");
        Path pb = rp.resolve("PackB.zip");
        zip(pb, B);

        // Böses Pack: Zip-Slip
        Map<String, String> E = new LinkedHashMap<>();
        E.put("pack.mcmeta", "{}");
        E.put("../../evil.txt", "x");
        E.put(M + "textures/gui/sprites/hud/hotbar.png", "H_EVIL");
        Path pe = rp.resolve("Evil.zip");
        zip(pe, E);

        // Kaputtes Pack
        Files.writeString(rp.resolve("Broken.zip"), "this is not a zip");
        // Pack ohne pack.mcmeta
        Path nomc = rp.resolve("NoMcmeta.zip");
        zip(nomc, Map.of(M + "textures/item/mace.png", "x"));

        PackLibrary pl = new PackLibrary(lib, rp, "ResourcePackMixer_Overlay");
        pl.rescan(Set.of());
        check(pl.packs().size() == 5, "5 Packs erkannt (inkl. kaputt/ohne mcmeta): " + pl.packs().size());
        check(!pl.get("rp/Broken.zip").valid, "kaputtes ZIP als ungültig markiert: " + pl.get("rp/Broken.zip").error);
        check(!pl.get("rp/NoMcmeta.zip").valid, "Pack ohne pack.mcmeta ungültig");
        check(pl.get("rp/Evil.zip").valid && !pl.get("rp/Evil.zip").warnings.isEmpty(), "Zip-Slip-Eintrag übersprungen + Warnung");
        check(!Files.exists(t.resolve("evil.txt")) && !Files.exists(t.getParent().resolve("evil.txt")), "keine Datei außerhalb geschrieben");

        PackInfo ia = pl.get("rp/PackA.zip"), ib = pl.get("rp/PackB.zip");
        check(ia.status(Categories.byId("hotbar").targets) == Status.AVAILABLE, "A: Hotbar verfügbar");
        check(ia.status(Categories.byId("inventory").targets) == Status.PARTIAL, "A: Inventar teilweise (kein Creative)");
        check(ia.status(Categories.byId("anchor").targets) == Status.NONE, "A: Anker nicht vorhanden");
        check(ib.status(Categories.byId("anchor").targets) == Status.PARTIAL, "B: Anker teilweise");
        check(ia.status(Categories.byId("sword").targets) == Status.PARTIAL, "A: Schwert teilweise (nur 2 Materialien)");
        check(ib.customSky && !ia.customSky, "Custom-Sky nur bei B erkannt");


        // Vorschau
        check(ia.previewPath("hotbar") != null && ia.previewPath("hotbar").endsWith("hud/hotbar.png"), "Vorschaupfad Hotbar");
        check(ia.previewPath("sword") != null && ia.previewPath("sword").endsWith("iron_sword.png"), "Vorschau Schwert (Eisen bevorzugt)");
        check(ia.previewPath("sword:diamond") != null && ia.previewPath("sword:wooden") == null, "Vorschau je Material");
        check(ib.previewPath("glowstone") == null, "Kein Vorschaupfad, wenn Textur fehlt");
        check("HOTBAR_B".equals(new String(ib.readBytes(ib.previewPath("hotbar"), 1024))), "readBytes aus ZIP");
        boolean big = false;
        try { ia.readBytes(ia.previewPath("hotbar"), 3); } catch (Exception e) { big = true; }
        check(big, "readBytes lehnt zu große Dateien ab");
        boolean bad = false;
        try { ia.readBytes("../../etc/passwd", 100); } catch (Exception e) { bad = true; }
        check(bad, "readBytes lehnt Pfade außerhalb des Index ab");

        // Kombination: Hotbar aus A, Crystals aus B, Inventar aus A, Schwert: A (Kategorie) aber iron explizit SKIP,
        // Anker aus B
        Map<String, String> sel = new LinkedHashMap<>();
        sel.put("hotbar", "rp/PackA.zip");
        sel.put("crystals", "rp/PackB.zip");
        sel.put("inventory", "rp/PackA.zip");
        sel.put("sword", "rp/PackA.zip");
        sel.put("sword:iron", OverlayBuilder.SKIP);
        sel.put("anchor", "rp/PackB.zip");
        sel.put("trident", "rp/Fehlt.zip");
        Path out = t.resolve("build");
        var res = OverlayBuilder.build(out, sel, pl::get, 75);
        String root = M;
        check("HOTBAR_A".equals(Files.readString(out.resolve(root + "textures/gui/sprites/hud/hotbar.png"))), "Hotbar kommt aus A");
        check("CRY_B".equals(Files.readString(out.resolve(root + "textures/entity/end_crystal/end_crystal.png"))), "Crystal-Entity kommt aus B");
        check(!Files.exists(out.resolve(root + "textures/item/end_crystal.png")), "Crystal-Item-Icon: B hat keins -> A wird NICHT beigemischt");
        check("INV_A".equals(Files.readString(out.resolve(root + "textures/gui/container/inventory.png"))), "Inventar aus A");
        check(!Files.exists(out.resolve(root + "textures/gui/sprites/hud/heart/full.png")), "Herzen aus A NICHT übernommen");
        check(!Files.exists(out.resolve(root + "models/item/generated.json")), "Vorlagen-Modell item/generated nicht kopiert");
        check(Files.exists(out.resolve(root + "textures/item/diamond_sword.png.mcmeta")), ".mcmeta-Begleitdatei übernommen");
        check(!Files.exists(out.resolve(root + "textures/item/iron_sword.png")), "iron_sword per SKIP ausgenommen");
        check(Files.exists(out.resolve(root + "blockstates/respawn_anchor.json"))
                && Files.exists(out.resolve(root + "models/block/respawn_anchor_0.json"))
                && Files.exists(out.resolve(root + "textures/block/respawn_anchor_top_off.png")), "Anker: Blockstate->Modell->Textur aufgelöst");
        check(!Files.exists(out.resolve("optifine")) && out.resolve(M).toFile().exists(), "Custom-Sky-Dateien nicht übernommen");
        check(res.warnings.stream().anyMatch(w -> w.contains("Fehlt.zip")), "Fehlendes Pack erzeugt Warnung, kein Absturz");
        check(Files.exists(out.resolve("pack.mcmeta")), "pack.mcmeta des Overlays geschrieben");
        System.out.println("      Warnungen: " + res.warnings);
        System.out.println("      Hinweise:  " + res.notes);

        // Atomarer Austausch + Wiederholung
        Path target = rp.resolve("ResourcePackMixer_Overlay");
        FileUtil.replaceDirectory(out, target, t.resolve("trash/old"));
        check(Files.exists(target.resolve("pack.mcmeta")) && !Files.exists(out), "Overlay installiert");
        var res2 = OverlayBuilder.build(t.resolve("build2"), Map.of("hotbar", "rp/PackB.zip"), pl::get, 75);
        FileUtil.replaceDirectory(t.resolve("build2"), target, t.resolve("trash/old"));
        check("HOTBAR_B".equals(Files.readString(target.resolve(M + "textures/gui/sprites/hud/hotbar.png")))
                && !Files.exists(target.resolve(M + "textures/gui/container/inventory.png")), "Austausch ersetzt vollständig, keine Reste");
        pl.rescan(Set.of());
        check(pl.get("rp/ResourcePackMixer_Overlay") == null, "Overlay taucht nicht in der Pack-Bibliothek auf");

        // Konflikt: dieselbe Datei aus zwei Packs
        Map<String, String> sel2 = new LinkedHashMap<>();
        sel2.put("trident", "rp/PackA.zip"); // A hat kein trident -> nichts
        var res3 = OverlayBuilder.build(t.resolve("build3"), sel2, pl::get, 75);
        check(res3.files == 0, "Leere Auswahl -> keine Dateien (Overlay entfällt)");

        // Import (Kopie) + Entfernen löscht nur die Kopie
        String id = pl.importPack(pa);
        pl.rescan(Set.of());
        check(id.startsWith("lib/") && pl.get(id) != null && Files.exists(pa), "Import erzeugt Kopie, Original bleibt");
        pl.removeCopy(id);
        pl.rescan(Set.of());
        check(pl.get(id) == null && Files.exists(pa), "Entfernen löscht nur Kopie");
        boolean threw = false;
        try { pl.importPack(pe.resolveSibling("Broken.zip")); } catch (Exception e) { threw = true; }
        check(threw, "Import kaputter ZIP wird abgelehnt");
        boolean threw2 = false;
        try { pl.removeCopy("rp/PackA.zip"); } catch (Exception e) { threw2 = true; }
        check(threw2 && Files.exists(pa), "Original aus resourcepacks lässt sich nicht löschen");

        // Config / Profile
        Path cfgFile = t.resolve("cfg.json");
        MixerConfig c = MixerConfig.load(cfgFile);
        c.createProfile("Crystal PvP", sel);
        c.save(cfgFile);
        MixerConfig c2 = MixerConfig.load(cfgFile);
        check("Crystal PvP".equals(c2.activeProfile) && c2.active().selections.get("hotbar").equals("rp/PackA.zip"), "Config überlebt Neustart");
        check(c2.renameProfile("Crystal PvP", "Survival") && c2.profiles.containsKey("Survival"), "Profil umbenennen");
        c2.exportProfile("Survival", c2.active().selections, t.resolve("exp/s.json"));
        String imported = c2.importProfile(t.resolve("exp/s.json"));
        check(c2.profiles.containsKey(imported) && !imported.equals("Survival"), "Profil-Import ohne Namenskollision: " + imported);
        check(!Files.readString(t.resolve("exp/s.json")).contains("HOTBAR_A"), "Export enthält keine Texturen");
        check(c2.deleteProfile(imported) && !c2.deleteProfile("gibtsnicht"), "Profil löschen");
        Files.writeString(cfgFile, "{kaputt");
        check(MixerConfig.load(cfgFile).profiles.size() == 1, "Defekte Config -> sicherer Default");

        System.out.println(fails == 0 ? "\nALLE TESTS BESTANDEN" : "\nFEHLER: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
