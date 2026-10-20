package dev.rpmixer.client;

import dev.rpmixer.core.PackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Lädt Vorschau-Texturen aus Packs: Dekodieren im Hintergrund-Thread, Registrieren und Zeichnen im Client-Thread.
 * Nur über den Client-Thread (Render) aufrufen.
 */
final class PreviewCache {
    enum State { LOADING, READY, MISSING }

    static final class Preview {
        volatile State state = State.LOADING;
        volatile Identifier id;
        volatile int w;
        volatile int h;
    }

    private static final int MAX_ENTRIES = 80;
    private static final long MAX_BYTES = 4L * 1024 * 1024;
    private static final Map<String, Preview> CACHE = new LinkedHashMap<>();
    private static int counter = 0;
    private static boolean warned = false;

    private PreviewCache() {}

    static Preview get(PackInfo pack, String path) {
        String key = pack.id + "|" + pack.signature() + "|" + path;
        Preview p = CACHE.get(key);
        if (p != null) return p;
        p = new Preview();
        CACHE.put(key, p);
        evict();
        final Preview fp = p;
        RpmClient.IO.submit(() -> load(pack, path, fp));
        return p;
    }

    private static void evict() {
        while (CACHE.size() > MAX_ENTRIES) {
            Iterator<Map.Entry<String, Preview>> it = CACHE.entrySet().iterator();
            Preview old = it.next().getValue();
            it.remove();
            old.state = State.MISSING;
            Identifier id = old.id;
            if (id != null) {
                try {
                    MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
                } catch (RuntimeException ignored) {
                    // egal
                }
            }
        }
    }

    private static void load(PackInfo pack, String path, Preview fp) {
        try {
            byte[] data = pack.readBytes(path, MAX_BYTES);
            NativeImage img = NativeImage.read(new ByteArrayInputStream(data));
            if (img.getWidth() > 2048 || img.getHeight() > 4096) {
                img.close();
                fp.state = State.MISSING;
                return;
            }
            MinecraftClient.getInstance().execute(() -> finish(fp, img));
        } catch (IOException | RuntimeException e) {
            fp.state = State.MISSING;
        }
    }

    /** Client-Thread. */
    private static void finish(Preview fp, NativeImage img) {
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            Identifier id = Identifier.of(RpmClient.MOD_ID, "preview/p" + (counter++));
            AbstractTexture tex = makeTexture(img, id.toString());
            if (tex == null) {
                img.close();
                fp.state = State.MISSING;
                return;
            }
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            fp.w = w;
            fp.h = h;
            fp.id = id;
            fp.state = State.READY;
        } catch (RuntimeException e) {
            fp.state = State.MISSING;
            if (!warned) {
                warned = true;
                RpmClient.LOG.warn("Vorschau nicht möglich: {}", RpmClient.scrub(e.getMessage()));
            }
        }
    }

    /**
     * Der Konstruktor von NativeImageBackedTexture hat sich zwischen Versionen geändert
     * ((NativeImage) / (String, NativeImage) / (Supplier, NativeImage)). Per Reflection wird der passende gewählt.
     */
    @SuppressWarnings("unchecked")
    private static AbstractTexture makeTexture(NativeImage img, String name) {
        for (Constructor<?> c : NativeImageBackedTexture.class.getConstructors()) {
            Class<?>[] t = c.getParameterTypes();
            try {
                if (t.length == 1 && t[0] == NativeImage.class) {
                    return (AbstractTexture) c.newInstance(img);
                }
                if (t.length == 2 && t[1] == NativeImage.class) {
                    if (t[0] == String.class) return (AbstractTexture) c.newInstance(name, img);
                    if (Supplier.class.isAssignableFrom(t[0])) {
                        Supplier<String> s = () -> name;
                        return (AbstractTexture) c.newInstance(s, img);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (!warned) {
                    warned = true;
                    RpmClient.LOG.warn("Textur-Konstruktor fehlgeschlagen: {}", RpmClient.scrub(e.getMessage()));
                }
            }
        }
        return null;
    }

    /** Zeichnet die Vorschau eingepasst in das Rechteck (Client-Thread). Animationsstreifen: nur das erste Bild. */
    static void draw(DrawContext ctx, Preview p, int x, int y, int w, int h) {
        Identifier id = p.id;
        if (p.state != State.READY || id == null || p.w <= 0 || p.h <= 0) return;
        int rw = p.w;
        int rh = (p.h > p.w && p.h % p.w == 0) ? p.w : p.h;
        float s = Math.min((float) w / rw, (float) h / rh);
        int dw;
        int dh;
        if (s >= 1f) {
            int k = (int) s; // ganzzahlig skalieren = scharfe Pixel
            dw = rw * k;
            dh = rh * k;
        } else {
            dw = Math.max(1, Math.round(rw * s));
            dh = Math.max(1, Math.round(rh * s));
        }
        int dx = x + (w - dw) / 2;
        int dy = y + (h - dh) / 2;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, id, dx, dy, 0f, 0f, dw, dh, rw, rh, p.w, p.h);
    }
}
