package dev.rpmixer.client;

import dev.rpmixer.core.MixerConfig;
import dev.rpmixer.core.PackLibrary;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class RpmClient implements ClientModInitializer {
    public static final String MOD_ID = "resourcepackmixer";
    public static final Logger LOG = LoggerFactory.getLogger("ResourcePackMixer");

    /** Name des generierten Overlay-Packs im resourcepacks-Ordner. */
    public static final String OVERLAY_DIR = "ResourcePackMixer_Overlay";
    /** Pack-ID, unter der Minecraft das Overlay führt ("file/" + Ordnername). */
    public static final String OVERLAY_ID = "file/" + OVERLAY_DIR;
    /** Ressourcenpack-Format von 1.21.11 (bei Versionswechsel prüfen!). */
    public static final int PACK_FORMAT = 75;

    public static MixerConfig config;
    public static PackLibrary library;
    public static Path configFile;
    public static Path dataDir;
    public static Path gameDir;
    public static Path resourcePacksDir;

    /** Ein einziger Hintergrund-Thread: serialisiert alle Dateioperationen, nichts davon läuft im Render-Thread. */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "rpm-io");
        t.setDaemon(true);
        return t;
    });

    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        gameDir = FabricLoader.getInstance().getGameDir();
        configFile = FabricLoader.getInstance().getConfigDir().resolve("resourcepackmixer.json");
        dataDir = gameDir.resolve("resourcepackmixer");
        resourcePacksDir = gameDir.resolve("resourcepacks");
        config = MixerConfig.load(configFile);
        library = new PackLibrary(dataDir.resolve("library"), resourcePacksDir, OVERLAY_DIR);
        rescanAsync(err -> { });

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.resourcepackmixer.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (!(client.currentScreen instanceof MixerScreen)) {
                    client.setScreen(new MixerScreen(client.currentScreen));
                }
            }
        });
    }

    /** Liest die Bibliothek im Hintergrund neu ein. {@code done} läuft im Client-Thread (Argument null = Erfolg). */
    public static void rescanAsync(Consumer<String> done) {
        Set<String> hidden = new TreeSet<>(config.hiddenPacks);
        IO.submit(() -> {
            String err = null;
            try {
                library.rescan(hidden);
            } catch (IOException | RuntimeException e) {
                LOG.warn("Rescan fehlgeschlagen: {}", scrub(e.getMessage()));
                err = scrub(e.getMessage());
            }
            String fe = err;
            MinecraftClient.getInstance().execute(() -> done.accept(fe));
        });
    }

    /** Entfernt den Spielordner-Pfad aus Meldungen (keine persönlichen Pfade in Logs/UI). */
    public static String scrub(String s) {
        if (s == null) return "unbekannter Fehler";
        String t = s;
        if (gameDir != null) t = t.replace(gameDir.toString(), "<game>");
        String home = System.getProperty("user.home");
        if (home != null && !home.isEmpty()) t = t.replace(home, "~");
        return t;
    }
}
