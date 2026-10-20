package dev.rpmixer.client;

import dev.rpmixer.core.FileUtil;
import dev.rpmixer.core.OverlayBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourcePackManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Baut das Overlay im Hintergrund, tauscht es atomar in den resourcepacks-Ordner und löst genau EINEN Reload
 * über MinecraftClient#reloadResources aus. Gleichzeitige Aufrufe werden abgewiesen.
 */
public final class OverlayApplier {
    private OverlayApplier() {}

    public record Outcome(boolean ok, boolean overlayActive, int files, int packs,
                          List<String> warnings, List<String> notes, String error) {}

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    public static boolean isBusy() {
        return BUSY.get();
    }

    /** {@code callback} läuft im Client-Thread. */
    public static void apply(Map<String, String> selections, Consumer<Outcome> callback) {
        if (!BUSY.compareAndSet(false, true)) {
            callback.accept(new Outcome(false, false, 0, 0, List.of(), List.of(), "busy"));
            return;
        }
        Map<String, String> sel = new LinkedHashMap<>(selections);
        MinecraftClient mc = MinecraftClient.getInstance();
        RpmClient.IO.submit(() -> {
            try {
                Path build = RpmClient.dataDir.resolve("build");
                Path trash = RpmClient.dataDir.resolve("trash").resolve("overlay-old");
                Path target = RpmClient.resourcePacksDir.resolve(RpmClient.OVERLAY_DIR);
                FileUtil.deleteTree(build);
                OverlayBuilder.Result r = OverlayBuilder.build(build, sel, RpmClient.library::get,
                        RpmClient.PACK_FORMAT);
                boolean active = r.files > 0;
                if (active) {
                    FileUtil.replaceDirectory(build, target, trash);
                } else {
                    FileUtil.deleteTree(build);
                }
                List<String> warnings = new ArrayList<>();
                for (String w : r.warnings) warnings.add(RpmClient.scrub(w));
                Outcome o = new Outcome(true, active, r.files, r.usedPacks.size(), warnings, r.notes, null);
                mc.execute(() -> switchPackAndReload(mc, o, callback));
            } catch (Exception e) {
                RpmClient.LOG.error("Overlay konnte nicht erzeugt werden: {}", RpmClient.scrub(e.getMessage()));
                BUSY.set(false);
                String msg = RpmClient.scrub(e.getMessage());
                mc.execute(() -> callback.accept(new Outcome(false, false, 0, 0, List.of(), List.of(), msg)));
            }
        });
    }

    /** Läuft im Client-Thread. */
    private static void switchPackAndReload(MinecraftClient mc, Outcome o, Consumer<Outcome> callback) {
        try {
            ResourcePackManager mgr = mc.getResourcePackManager();
            mgr.scanPacks();
            List<String> ids = new ArrayList<>(mgr.getEnabledIds());
            ids.remove(RpmClient.OVERLAY_ID);
            if (o.overlayActive()) ids.add(RpmClient.OVERLAY_ID); // letzte Position = höchste Priorität
            mgr.setEnabledProfiles(ids);
            mc.options.resourcePacks.remove(RpmClient.OVERLAY_ID);
            if (o.overlayActive()) mc.options.resourcePacks.add(RpmClient.OVERLAY_ID);
            mc.options.write();
            mc.reloadResources().whenComplete((v, t) -> {
                if (t != null) RpmClient.LOG.error("Reload fehlgeschlagen: {}", RpmClient.scrub(t.getMessage()));
                if (!o.overlayActive()) {
                    // Overlay erst NACH dem Reload löschen, damit keine geöffnete Pack-Datei im Weg ist
                    RpmClient.IO.submit(() -> {
                        try {
                            FileUtil.deleteTree(RpmClient.resourcePacksDir.resolve(RpmClient.OVERLAY_DIR));
                        } catch (Exception e) {
                            RpmClient.LOG.warn("Altes Overlay nicht löschbar: {}", RpmClient.scrub(e.getMessage()));
                        }
                    });
                }
                BUSY.set(false);
                mc.execute(() -> callback.accept(o));
            });
        } catch (RuntimeException e) {
            BUSY.set(false);
            RpmClient.LOG.error("Pack-Wechsel fehlgeschlagen: {}", RpmClient.scrub(e.getMessage()));
            callback.accept(new Outcome(false, false, 0, 0, List.of(), List.of(), RpmClient.scrub(e.getMessage())));
        }
    }
}
