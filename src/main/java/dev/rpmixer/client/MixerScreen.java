package dev.rpmixer.client;

import dev.rpmixer.core.CategoryDef;
import dev.rpmixer.core.Categories;
import dev.rpmixer.core.OverlayBuilder;
import dev.rpmixer.core.PackInfo;
import dev.rpmixer.core.Status;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Menü mit Kategorie-Seitenleiste, Pack-Liste mit Miniaturvorschau und großem Vorschaufeld.
 * Eigene Optik über {@link UiButton}; Eingabe-Callbacks des Spiels werden nicht überschrieben.
 */
public final class MixerScreen extends Screen {
    private static final String ENTRY_NONE = "\u0000none";
    private static final String ENTRY_INHERIT = "\u0000inherit";

    private static final int C_TEXT = UiButton.TEXT;
    private static final int C_MUTED = UiButton.MUTED;
    private static final int C_GREEN = UiButton.GREEN;
    private static final int C_YELLOW = UiButton.YELLOW;
    private static final int C_RED = UiButton.RED;
    private static final int C_ORANGE = 0xFFFB923C;

    private record Entry(String id, PackInfo pack) {}

    private record Row(UiButton button, PackInfo pack) {}

    private final Screen parent;
    private final Map<String, String> pending = new LinkedHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private int catIdx = 0;
    private int matIdx = 0;
    private int page = 0;
    private int pageCount = 1;
    private int hiddenCount = 0;
    private boolean showAll = false;
    private boolean dirty = false;
    private boolean started = false;
    private String focusedPack = null;
    private Text statusText = Text.empty();
    private int statusColor = C_TEXT;

    // Layout (wird in rebuild() berechnet)
    private int winX0, winY0, winX1, winY1, headH, bodyTop, bodyBottom, footTop;
    private int sideX, sideW, listX, listW, prevX, prevW;

    private TextFieldWidget searchField;
    private TextFieldWidget nameField;
    private TextFieldWidget importField;

    public MixerScreen(Screen parent) {
        super(Text.translatable("rpm.title"));
        this.parent = parent;
        pending.putAll(RpmClient.config.active().selections);
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void init() {
        if (searchField == null) {
            searchField = new TextFieldWidget(this.textRenderer, 0, 0, 100, 16, Text.empty());
            searchField.setPlaceholder(Text.translatable("rpm.search"));
            searchField.setMaxLength(60);
            searchField.setChangedListener(s -> {
                page = 0;
                rebuild();
            });
            nameField = new TextFieldWidget(this.textRenderer, 0, 0, 84, 16, Text.empty());
            nameField.setMaxLength(40);
            nameField.setText(RpmClient.config.activeProfile);
            importField = new TextFieldWidget(this.textRenderer, 0, 0, 100, 16, Text.empty());
            importField.setPlaceholder(Text.translatable("rpm.import.placeholder"));
            importField.setMaxLength(400);
        }
        if (!started) {
            started = true;
            MappingSelfCheck.runOnce(this.client);
            RpmClient.rescanAsync(err -> {
                if (err != null) setStatus(Text.translatable("rpm.msg.rescan_failed", err), C_RED);
                else reportMissingReferences();
                rebuild();
            });
        }
        rebuild();
    }

    private CategoryDef cat() {
        return Categories.ALL.get(catIdx);
    }

    private String key() {
        return cat().keyFor(matIdx);
    }

    private UiButton btn(String label, UiButton.Style style, int x, int y, int w, int h,
                         net.minecraft.client.gui.widget.ButtonWidget.PressAction a) {
        return new UiButton(x, y, w, h, Text.literal(label), style, a);
    }

    private UiButton tbtn(String key, UiButton.Style style, int x, int y, int w, int h,
                          net.minecraft.client.gui.widget.ButtonWidget.PressAction a) {
        return new UiButton(x, y, w, h, Text.translatable(key), style, a);
    }

    private void rebuild() {
        if (this.client == null || searchField == null) return;
        boolean refocusSearch = searchField.isFocused();
        this.clearChildren();
        rows.clear();
        CategoryDef cat = cat();
        int w = this.width;
        int h = this.height;

        // ---- Fensterfläche
        int m = 6;
        winX0 = m;
        winY0 = m;
        winX1 = w - m;
        winY1 = h - m;
        headH = 26;
        footTop = winY1 - 54;
        bodyTop = winY0 + headH + 6;
        bodyBottom = footTop - 4;

        // ---- Kopfzeile: Profile (rechtsbündig)
        int hy = winY0 + 5;
        int rx = winX1 - 6;
        rx -= 44;
        this.addDrawableChild(tbtn("rpm.btn.export", UiButton.Style.NORMAL, rx, hy, 44, 16, b -> exportProfile()));
        rx -= 42;
        this.addDrawableChild(tbtn("rpm.btn.delete", UiButton.Style.DANGER, rx, hy, 40, 16, b -> deleteProfile()));
        rx -= 50;
        this.addDrawableChild(tbtn("rpm.btn.rename", UiButton.Style.NORMAL, rx, hy, 48, 16, b -> renameProfile()));
        rx -= 32;
        this.addDrawableChild(tbtn("rpm.btn.new", UiButton.Style.NORMAL, rx, hy, 30, 16, b -> newProfile()));
        rx -= 86;
        nameField.setX(rx);
        nameField.setY(hy);
        nameField.setWidth(84);
        this.addDrawableChild(nameField);
        rx -= 18;
        this.addDrawableChild(btn(">", UiButton.Style.NORMAL, rx, hy, 16, 16, b -> cycleProfile(1)));
        rx -= 18;
        this.addDrawableChild(btn("<", UiButton.Style.NORMAL, rx, hy, 16, 16, b -> cycleProfile(-1)));

        // ---- Seitenleiste: Kategorien
        sideX = winX0 + 8;
        sideW = 90;
        int n = Categories.ALL.size();
        int ch = Math.max(11, Math.min(17, (bodyBottom - bodyTop) / n));
        for (int i = 0; i < n; i++) {
            final int idx = i;
            CategoryDef c = Categories.ALL.get(i);
            boolean any = pending.keySet().stream().anyMatch(k -> k.equals(c.id) || k.startsWith(c.id + ":"));
            UiButton b = new UiButton(sideX, bodyTop + i * (ch + 1), sideW, ch,
                    Text.translatable("rpm.category." + c.id), UiButton.Style.CATEGORY, bb -> {
                        catIdx = idx;
                        matIdx = 0;
                        page = 0;
                        rebuild();
                    }).selected(i == catIdx).marked(any);
            this.addDrawableChild(b);
        }

        // ---- Vorschaufeld (rechts)
        prevW = Math.max(104, Math.min(150, w / 4));
        prevX = winX1 - 8 - prevW;

        // ---- Mitte: Suche, Material, Liste
        listX = sideX + sideW + 10;
        listW = prevX - 10 - listX;
        searchField.setX(listX);
        searchField.setY(bodyTop);
        searchField.setWidth(listW - 58);
        this.addDrawableChild(searchField);
        UiButton toggle = btn(Text.translatable(showAll ? "rpm.toggle.all.on" : "rpm.toggle.all.off").getString(),
                UiButton.Style.NORMAL, listX + listW - 56, bodyTop, 56, 16, b -> {
                    showAll = !showAll;
                    page = 0;
                    rebuild();
                });
        toggle.setTooltip(Tooltip.of(Text.translatable("rpm.tip.toggle")));
        this.addDrawableChild(toggle);

        int listTop = bodyTop + 20;
        if (cat.perMaterial) {
            this.addDrawableChild(btn("<", UiButton.Style.NORMAL, listX, listTop, 16, 14, b -> cycleMaterial(-1)));
            UiButton mat = btn(materialLabel(), UiButton.Style.NORMAL, listX + 18, listTop, listW - 36, 14, b -> { });
            mat.active = false;
            this.addDrawableChild(mat);
            this.addDrawableChild(btn(">", UiButton.Style.NORMAL, listX + listW - 16, listTop, 16, 14,
                    b -> cycleMaterial(1)));
            listTop += 18;
        }

        int pageBarY = bodyBottom - 14;
        int rowH = 26;
        int gap = 2;
        List<Entry> entries = buildEntries();
        int listBottom = pageBarY - 3;
        int pageSize = Math.max(1, (listBottom - listTop + gap) / (rowH + gap));
        pageCount = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));
        String previewKey = key();
        for (int i = page * pageSize; i < Math.min(entries.size(), (page + 1) * pageSize); i++) {
            Entry e = entries.get(i);
            int by = listTop + (i - page * pageSize) * (rowH + gap);
            UiButton b = makeRow(e, listX, by, listW, rowH, previewKey);
            this.addDrawableChild(b);
            rows.add(new Row(b, e.pack));
        }
        this.addDrawableChild(btn("<", UiButton.Style.NORMAL, listX, pageBarY, 22, 14, b -> {
            page = Math.max(0, page - 1);
            rebuild();
        }));
        this.addDrawableChild(btn(">", UiButton.Style.NORMAL, listX + 24, pageBarY, 22, 14, b -> {
            page = Math.min(pageCount - 1, page + 1);
            rebuild();
        }));

        // ---- Fußzeile
        int fy1 = footTop + 6;
        importField.setX(winX0 + 8);
        importField.setY(fy1);
        importField.setWidth(winX1 - winX0 - 16 - 74);
        this.addDrawableChild(importField);
        this.addDrawableChild(tbtn("rpm.btn.import", UiButton.Style.NORMAL, winX1 - 8 - 70, fy1, 70, 16,
                b -> importFromField()));
        int fy2 = fy1 + 20;
        int total = winX1 - winX0 - 16;
        int bw = (total - 5 * 3) / 6;
        String[] keys = {"rescan", "folder", "remove", "reset", "apply", "done"};
        net.minecraft.client.gui.widget.ButtonWidget.PressAction[] actions = {
                b -> rescan(), b -> openFolder(), b -> removeFocused(), b -> resetAll(), b -> apply(), b -> close()};
        for (int i = 0; i < 6; i++) {
            UiButton.Style st = keys[i].equals("apply") ? UiButton.Style.PRIMARY
                    : keys[i].equals("remove") ? UiButton.Style.DANGER : UiButton.Style.NORMAL;
            this.addDrawableChild(tbtn("rpm.btn." + keys[i], st, winX0 + 8 + i * (bw + 3), fy2, bw, 16, actions[i]));
        }

        if (refocusSearch) {
            this.setFocused(searchField);
            searchField.setFocused(true);
        }
    }

    private String materialLabel() {
        Text mt = matIdx == 0 ? Text.translatable("rpm.material.all")
                : Text.translatable("rpm.material." + Categories.MATERIALS.get(matIdx - 1));
        return Text.translatable("rpm.material.label", mt).getString();
    }

    private void cycleMaterial(int dir) {
        int n = Categories.MATERIALS.size() + 1;
        matIdx = (matIdx + dir + n) % n;
        page = 0;
        rebuild();
    }

    // ------------------------------------------------------------------ Liste

    private List<Entry> buildEntries() {
        List<Entry> list = new ArrayList<>();
        if (matIdx > 0) list.add(new Entry(ENTRY_INHERIT, null));
        list.add(new Entry(ENTRY_NONE, null));
        String sel = pending.get(key());
        if (sel != null && !OverlayBuilder.SKIP.equals(sel) && RpmClient.library.get(sel) == null) {
            list.add(new Entry(sel, null)); // fehlendes Pack sichtbar machen
        }
        String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
        var targets = cat().targetsFor(matIdx);
        hiddenCount = 0;
        for (PackInfo p : RpmClient.library.packs()) {
            if (!q.isEmpty() && !p.name.toLowerCase(Locale.ROOT).contains(q)) continue;
            boolean has = p.valid && p.status(targets) != Status.NONE;
            // Packs ohne die Textur werden ausgeblendet (außer dem aktuell gewählten oder bei "Alle")
            if (!has && !showAll && !p.id.equals(sel)) {
                hiddenCount++;
                continue;
            }
            list.add(new Entry(p.id, p));
        }
        return list;
    }

    private boolean isSelected(Entry e) {
        String sel = pending.get(key());
        return switch (e.id) {
            case ENTRY_INHERIT -> sel == null;
            case ENTRY_NONE -> matIdx > 0 ? OverlayBuilder.SKIP.equals(sel) : sel == null;
            default -> e.id.equals(sel);
        };
    }

    private UiButton makeRow(Entry e, int x, int y, int w, int h, String previewKey) {
        String title;
        String sub;
        int subColor = C_MUTED;
        boolean box = false;
        PreviewCache.Preview thumb = null;
        Text tip;

        if (e.id.equals(ENTRY_INHERIT)) {
            title = Text.translatable("rpm.entry.inherit").getString();
            sub = Text.translatable("rpm.entry.inherit.sub").getString();
            tip = Text.translatable("rpm.tip.inherit");
        } else if (e.id.equals(ENTRY_NONE)) {
            title = Text.translatable("rpm.entry.none").getString();
            sub = Text.translatable("rpm.entry.none.sub").getString();
            tip = Text.translatable("rpm.tip.none");
        } else if (e.pack == null) {
            title = Text.translatable("rpm.entry.missing", e.id).getString();
            sub = Text.translatable("rpm.tip.missing.short").getString();
            subColor = C_RED;
            tip = Text.translatable("rpm.tip.missing");
        } else {
            PackInfo p = e.pack;
            title = p.name;
            box = true;
            if (!p.valid) {
                sub = Text.translatable("rpm.status.invalid").getString() + ": " + RpmClient.scrub(p.error);
                subColor = C_RED;
                tip = Text.literal(RpmClient.scrub(p.error));
            } else {
                Status s = p.status(cat().targetsFor(matIdx));
                sub = Text.translatable("rpm.status." + s.name().toLowerCase(Locale.ROOT)).getString();
                subColor = s == Status.AVAILABLE ? C_GREEN : s == Status.PARTIAL ? C_YELLOW : C_MUTED;
                String path = p.previewPath(previewKey);
                if (path != null) thumb = PreviewCache.get(p, path);
                tip = Text.literal(p.id).append("\n")
                        .append(Text.translatable(p.inLibrary ? "rpm.tip.copy" : "rpm.tip.original"));
            }
        }
        UiButton b = new UiButton(x, y, w, h, Text.literal(title), UiButton.Style.ROW, bb -> choose(e))
                .selected(isSelected(e)).sub(sub, subColor).thumb(thumb, box);
        b.setTooltip(Tooltip.of(tip));
        return b;
    }

    private void choose(Entry e) {
        String k = key();
        switch (e.id) {
            case ENTRY_INHERIT -> pending.remove(k);
            case ENTRY_NONE -> {
                if (matIdx > 0) pending.put(k, OverlayBuilder.SKIP);
                else pending.remove(k);
            }
            default -> {
                pending.put(k, e.id);
                focusedPack = e.id;
            }
        }
        dirty = true;
        setStatus(Text.translatable("rpm.msg.pending"), C_ORANGE);
        rebuild();
    }

    // ------------------------------------------------------------------ Profile

    private void cycleProfile(int dir) {
        List<String> names = RpmClient.config.profileNames();
        int i = Math.max(0, names.indexOf(RpmClient.config.activeProfile));
        String next = names.get((i + dir + names.size()) % names.size());
        RpmClient.config.activeProfile = next;
        pending.clear();
        pending.putAll(RpmClient.config.active().selections);
        nameField.setText(next);
        dirty = true;
        saveConfig();
        setStatus(Text.translatable("rpm.msg.profile_loaded", next), C_GREEN);
        reportMissingReferences();
        rebuild();
    }

    private void newProfile() {
        String n = RpmClient.config.createProfile(nameField.getText(), pending);
        if (n == null) {
            setStatus(Text.translatable("rpm.msg.profile_name_invalid"), C_RED);
        } else {
            saveConfig();
            setStatus(Text.translatable("rpm.msg.profile_created", n), C_GREEN);
        }
        rebuild();
    }

    private void renameProfile() {
        String old = RpmClient.config.activeProfile;
        if (RpmClient.config.renameProfile(old, nameField.getText())) {
            saveConfig();
            setStatus(Text.translatable("rpm.msg.profile_renamed", RpmClient.config.activeProfile), C_GREEN);
        } else {
            setStatus(Text.translatable("rpm.msg.profile_name_invalid"), C_RED);
        }
        rebuild();
    }

    private void deleteProfile() {
        String name = RpmClient.config.activeProfile;
        if (RpmClient.config.profiles.size() <= 1) {
            setStatus(Text.translatable("rpm.msg.profile_last"), C_RED);
            return;
        }
        this.client.setScreen(new ConfirmScreen(yes -> {
            if (yes && RpmClient.config.deleteProfile(name)) {
                pending.clear();
                pending.putAll(RpmClient.config.active().selections);
                nameField.setText(RpmClient.config.activeProfile);
                saveConfig();
                setStatus(Text.translatable("rpm.msg.profile_deleted", name), C_GREEN);
            }
            this.client.setScreen(this);
        }, Text.translatable("rpm.confirm.profile.title"), Text.translatable("rpm.confirm.profile.msg", name)));
    }

    private void exportProfile() {
        String name = RpmClient.config.activeProfile;
        Map<String, String> snapshot = new LinkedHashMap<>(pending);
        String file = name.replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
        Path target = RpmClient.dataDir.resolve("exports").resolve(file);
        RpmClient.IO.submit(() -> {
            try {
                RpmClient.config.exportProfile(name, snapshot, target);
                MinecraftClient.getInstance().execute(() ->
                        setStatus(Text.translatable("rpm.msg.exported", "resourcepackmixer/exports/" + file), C_GREEN));
            } catch (IOException e) {
                String msg = RpmClient.scrub(e.getMessage());
                MinecraftClient.getInstance().execute(() -> setStatus(Text.translatable("rpm.msg.error", msg), C_RED));
            }
        });
    }

    private void saveConfig() {
        try {
            RpmClient.config.save(RpmClient.configFile);
        } catch (IOException e) {
            setStatus(Text.translatable("rpm.msg.error", RpmClient.scrub(e.getMessage())), C_RED);
        }
    }

    // ------------------------------------------------------------------ Aktionen

    private int countMissing() {
        int missing = 0;
        for (String v : pending.values()) {
            if (!OverlayBuilder.SKIP.equals(v) && RpmClient.library.get(v) == null) missing++;
        }
        return missing;
    }

    private void reportMissingReferences() {
        int missing = countMissing();
        if (missing > 0) setStatus(Text.translatable("rpm.msg.missing_refs", missing), C_ORANGE);
    }

    private void apply() {
        if (OverlayApplier.isBusy()) {
            setStatus(Text.translatable("rpm.msg.busy"), C_ORANGE);
            return;
        }
        RpmClient.config.active().selections = new LinkedHashMap<>(pending);
        saveConfig();
        setStatus(Text.translatable("rpm.msg.applying"), C_TEXT);
        OverlayApplier.apply(pending, o -> {
            if (!o.ok()) {
                setStatus(Text.translatable("busy".equals(o.error()) ? "rpm.msg.busy" : "rpm.msg.error", o.error()),
                        C_RED);
                return;
            }
            for (String w : o.warnings()) RpmClient.LOG.warn("Overlay: {}", w);
            for (String nte : o.notes()) RpmClient.LOG.info("Overlay: {}", nte);
            dirty = false;
            if (!o.overlayActive()) {
                setStatus(Text.translatable("rpm.msg.reset_done"), C_GREEN);
            } else if (!o.warnings().isEmpty()) {
                setStatus(Text.translatable("rpm.msg.applied_warn", o.files(), o.packs(), o.warnings().get(0)),
                        C_ORANGE);
            } else {
                setStatus(Text.translatable("rpm.msg.applied", o.files(), o.packs()), C_GREEN);
            }
        });
    }

    private void resetAll() {
        pending.clear();
        dirty = true;
        setStatus(Text.translatable("rpm.msg.reset_pending"), C_ORANGE);
        rebuild();
    }

    private void rescan() {
        setStatus(Text.translatable("rpm.msg.rescanning"), C_TEXT);
        RpmClient.rescanAsync(err -> {
            if (err != null) {
                setStatus(Text.translatable("rpm.msg.rescan_failed", err), C_RED);
            } else {
                setStatus(Text.translatable("rpm.msg.rescanned", RpmClient.library.packs().size()), C_GREEN);
                reportMissingReferences();
            }
            rebuild();
        });
    }

    private void openFolder() {
        Path dir = RpmClient.library.libraryDir();
        try {
            Files.createDirectories(dir);
            Util.getOperatingSystem().open(dir.toUri());
        } catch (IOException | RuntimeException e) {
            setStatus(Text.translatable("rpm.msg.error", RpmClient.scrub(e.getMessage())), C_RED);
        }
    }

    private void importFromField() {
        String raw = importField.getText().trim();
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1);
        if (raw.isEmpty()) {
            setStatus(Text.translatable("rpm.msg.import_empty"), C_ORANGE);
            return;
        }
        Path p;
        try {
            p = Path.of(raw);
        } catch (InvalidPathException e) {
            setStatus(Text.translatable("rpm.msg.import_bad_path"), C_RED);
            return;
        }
        if (raw.toLowerCase(Locale.ROOT).endsWith(".json")) importProfileFile(p);
        else importPaths(List.of(p));
    }

    /** Drag-and-drop aus dem Dateimanager (ohne @Override, damit eine abweichende Signatur nicht den Build bricht). */
    public void onFilesDropped(List<Path> paths) {
        importPaths(paths);
    }

    private void importPaths(List<Path> paths) {
        setStatus(Text.translatable("rpm.msg.importing"), C_TEXT);
        List<Path> copy = new ArrayList<>(paths);
        Set<String> hidden = new TreeSet<>(RpmClient.config.hiddenPacks);
        RpmClient.IO.submit(() -> {
            int ok = 0;
            String err = null;
            for (Path p : copy) {
                try {
                    RpmClient.library.importPack(p);
                    ok++;
                } catch (IOException | RuntimeException e) {
                    err = (p.getFileName() == null ? "?" : p.getFileName().toString()) + ": "
                            + RpmClient.scrub(e.getMessage());
                }
            }
            try {
                RpmClient.library.rescan(hidden);
            } catch (IOException | RuntimeException e) {
                err = RpmClient.scrub(e.getMessage());
            }
            int fok = ok;
            String ferr = err;
            MinecraftClient.getInstance().execute(() -> {
                if (ferr != null) setStatus(Text.translatable("rpm.msg.import_failed", ferr), C_RED);
                else setStatus(Text.translatable("rpm.msg.imported", fok), C_GREEN);
                rebuild();
            });
        });
    }

    private void importProfileFile(Path p) {
        RpmClient.IO.submit(() -> {
            try {
                String name = RpmClient.config.importProfile(p);
                MinecraftClient.getInstance().execute(() -> {
                    saveConfig();
                    setStatus(Text.translatable("rpm.msg.profile_imported", name), C_GREEN);
                    rebuild();
                });
            } catch (IOException e) {
                String msg = RpmClient.scrub(e.getMessage());
                MinecraftClient.getInstance().execute(() -> setStatus(Text.translatable("rpm.msg.error", msg), C_RED));
            }
        });
    }

    private void removeFocused() {
        if (focusedPack == null || RpmClient.library.get(focusedPack) == null) {
            setStatus(Text.translatable("rpm.msg.remove_select"), C_ORANGE);
            return;
        }
        String id = focusedPack;
        PackInfo info = RpmClient.library.get(id);
        boolean copy = info.inLibrary;
        this.client.setScreen(new ConfirmScreen(yes -> {
            this.client.setScreen(this);
            if (!yes) return;
            if (copy) {
                RpmClient.IO.submit(() -> {
                    try {
                        RpmClient.library.removeCopy(id);
                    } catch (IOException e) {
                        RpmClient.LOG.warn("Entfernen fehlgeschlagen: {}", RpmClient.scrub(e.getMessage()));
                    }
                    MinecraftClient.getInstance().execute(this::afterRemove);
                });
            } else {
                RpmClient.config.hiddenPacks.add(id); // Original bleibt unangetastet, nur ausgeblendet
                saveConfig();
                afterRemove();
            }
        }, Text.translatable("rpm.confirm.remove.title"),
                Text.translatable(copy ? "rpm.confirm.remove.copy" : "rpm.confirm.remove.hide", info.name)));
    }

    private void afterRemove() {
        focusedPack = null;
        RpmClient.rescanAsync(err -> {
            setStatus(Text.translatable("rpm.msg.removed"), C_GREEN);
            reportMissingReferences();
            rebuild();
        });
    }

    private void setStatus(Text t, int color) {
        statusText = t;
        statusColor = color;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    // ------------------------------------------------------------------ Zeichnen

    private static void panel(DrawContext ctx, int x0, int y0, int x1, int y1) {
        ctx.fill(x0, y0, x1, y1, 0xFF2A3050);
        ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFF151827);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(winX0 - 1, winY0 - 1, winX1 + 1, winY1 + 1, 0xFF3A4170);
        ctx.fill(winX0, winY0, winX1, winY1, 0xF20F1220);
        ctx.fill(winX0, winY0, winX1, winY0 + headH, 0xFF181C30);
        ctx.fill(winX0, winY0 + headH, winX1, winY0 + headH + 1, 0xFF34D399);
        panel(ctx, sideX - 3, bodyTop - 3, sideX + sideW + 3, bodyBottom + 3);
        panel(ctx, listX - 4, bodyTop - 3, listX + listW + 4, bodyBottom + 3);
        panel(ctx, prevX - 4, bodyTop - 3, prevX + prevW + 4, bodyBottom + 3);
        ctx.fill(winX0, footTop, winX1, winY1, 0xFF141728);
        ctx.fill(winX0, footTop, winX1, footTop + 1, 0xFF2A3050);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        TextRenderer tr = this.textRenderer;
        CategoryDef cat = cat();

        // Titel
        if (this.width >= 470) {
            ctx.drawTextWithShadow(tr, Text.translatable("rpm.title"), winX0 + 10, winY0 + 9, 0xFFFFFFFF);
        }

        // Hinweis unter der Liste
        int infoY = bodyBottom - 11;
        ctx.drawTextWithShadow(tr, Text.literal((page + 1) + "/" + pageCount), listX + 52, infoY, C_MUTED);
        if (hiddenCount > 0) {
            String hid = Text.translatable("rpm.info.hidden", hiddenCount).getString();
            ctx.drawTextWithShadow(tr, tr.trimToWidth(hid, listW - 80), listX + 80, infoY, C_MUTED);
        } else if (rows.size() <= (matIdx > 0 ? 2 : 1)) {
            ctx.drawTextWithShadow(tr, tr.trimToWidth(Text.translatable("rpm.info.empty").getString(), listW - 80),
                    listX + 80, infoY, C_ORANGE);
        }

        // Vorschaufeld
        PackInfo hover = null;
        for (Row r : rows) {
            if (r.pack != null && r.button.isHovered()) {
                hover = r.pack;
                break;
            }
        }
        String sel = pending.get(key());
        PackInfo selInfo = sel == null || OverlayBuilder.SKIP.equals(sel) ? null : RpmClient.library.get(sel);
        PackInfo shown = hover != null ? hover : selInfo;

        int py = bodyTop;
        ctx.drawTextWithShadow(tr, Text.translatable("rpm.preview.title"), prevX, py, 0xFF9AA4C8);
        py += 12;
        int box = Math.min(prevW, Math.max(48, Math.min(96, bodyBottom - bodyTop - 90)));
        ctx.fill(prevX, py, prevX + prevW, py + box, 0xFF2A3050);
        ctx.fill(prevX + 1, py + 1, prevX + prevW - 1, py + box - 1, 0xFF0B0D17);
        String label = Text.translatable("rpm.preview.none").getString();
        if (shown != null) {
            String path = shown.previewPath(key());
            if (path != null) {
                PreviewCache.Preview pv = PreviewCache.get(shown, path);
                if (pv.state == PreviewCache.State.READY) {
                    PreviewCache.draw(ctx, pv, prevX + 3, py + 3, prevW - 6, box - 6);
                    label = null;
                } else {
                    label = Text.translatable(pv.state == PreviewCache.State.LOADING
                            ? "rpm.preview.loading" : "rpm.preview.error").getString();
                }
            } else {
                label = Text.translatable("rpm.preview.notexture").getString();
            }
        }
        if (label != null) {
            for (OrderedText line : tr.wrapLines(Text.literal(label), prevW - 8)) {
                ctx.drawTextWithShadow(tr, line, prevX + 5, py + box / 2 - 4, C_MUTED);
                break;
            }
        }
        py += box + 6;

        if (shown != null) {
            py = wrap(ctx, Text.literal(shown.name), prevX, py, prevW, 0xFFFFFFFF, 2);
            if (shown.valid) {
                Status s = shown.status(cat.targetsFor(matIdx));
                int col = s == Status.AVAILABLE ? C_GREEN : s == Status.PARTIAL ? C_YELLOW : C_MUTED;
                py = wrap(ctx, Text.translatable("rpm.status." + s.name().toLowerCase(Locale.ROOT)), prevX, py,
                        prevW, col, 1);
                py = wrap(ctx, Text.translatable(shown.inLibrary ? "rpm.tip.copy" : "rpm.tip.original"), prevX, py,
                        prevW, C_MUTED, 2);
                if (cat.id.equals("sky") && shown.customSky) {
                    wrap(ctx, Text.translatable("rpm.info.customsky"), prevX, py, prevW, C_ORANGE, 3);
                }
            } else {
                wrap(ctx, Text.literal(RpmClient.scrub(shown.error)), prevX, py, prevW, C_RED, 3);
            }
        }

        // Fußzeile: Status
        String st = statusText.getString();
        int stW = this.width - 2 * (winX0 + 8) - (dirty ? 110 : 0);
        ctx.drawTextWithShadow(tr, tr.trimToWidth(st, stW), winX0 + 8, winY1 - 12, statusColor);
        if (dirty) {
            String d = Text.translatable("rpm.info.dirty").getString();
            ctx.drawTextWithShadow(tr, d, winX1 - 8 - tr.getWidth(d), winY1 - 12, C_ORANGE);
        }
    }

    private int wrap(DrawContext ctx, Text text, int x, int y, int width, int color, int maxLines) {
        int lines = 0;
        for (OrderedText line : this.textRenderer.wrapLines(text, width)) {
            if (lines++ >= maxLines || y > bodyBottom - 8) break;
            ctx.drawTextWithShadow(this.textRenderer, line, x, y, color);
            y += 10;
        }
        return y + 1;
    }
}
