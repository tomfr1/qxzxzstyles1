package dev.rpmixer.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

/** Persistente Einstellungen (Datei config/resourcepackmixer.json). */
public final class MixerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String PROFILE_FORMAT = "rpm-profile-1";

    public int version = 1;
    public String activeProfile = "Default";
    public Map<String, Profile> profiles = new LinkedHashMap<>();
    /** Ausgeblendete Packs aus dem resourcepacks-Ordner (die Originale bleiben unangetastet). */
    public Set<String> hiddenPacks = new TreeSet<>();

    public static MixerConfig load(Path file) {
        MixerConfig c = null;
        if (Files.isRegularFile(file)) {
            try {
                c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), MixerConfig.class);
            } catch (IOException | JsonParseException e) {
                // defekte Datei sichern und neu beginnen
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".broken"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                    // egal
                }
            }
        }
        if (c == null) c = new MixerConfig();
        c.normalize();
        return c;
    }

    private void normalize() {
        if (profiles == null) profiles = new LinkedHashMap<>();
        if (hiddenPacks == null) hiddenPacks = new TreeSet<>();
        for (Map.Entry<String, Profile> e : profiles.entrySet()) {
            if (e.getValue() == null) e.setValue(new Profile());
            if (e.getValue().selections == null) e.getValue().selections = new LinkedHashMap<>();
        }
        if (profiles.isEmpty()) profiles.put("Default", new Profile());
        if (activeProfile == null || !profiles.containsKey(activeProfile)) {
            activeProfile = profiles.keySet().iterator().next();
        }
    }

    /** Schreibt atomar (temporäre Datei + Verschieben). */
    public synchronized void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
        FileUtil.move(tmp, file);
    }

    public Profile active() {
        return profiles.get(activeProfile);
    }

    public List<String> profileNames() {
        return new ArrayList<>(profiles.keySet());
    }

    public static String validateName(String raw) {
        if (raw == null) return null;
        String n = raw.trim();
        if (n.isEmpty() || n.length() > 40) return null;
        if (n.matches(".*[\\p{Cntrl}/\\\\:*?\"<>|].*")) return null;
        return n;
    }

    public String createProfile(String name, Map<String, String> selections) {
        String n = validateName(name);
        if (n == null || profiles.containsKey(n)) return null;
        profiles.put(n, new Profile(selections));
        activeProfile = n;
        return n;
    }

    public boolean renameProfile(String oldName, String newName) {
        String n = validateName(newName);
        if (n == null || !profiles.containsKey(oldName) || profiles.containsKey(n)) return false;
        Map<String, Profile> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Profile> e : profiles.entrySet()) {
            copy.put(e.getKey().equals(oldName) ? n : e.getKey(), e.getValue());
        }
        profiles = copy;
        if (activeProfile.equals(oldName)) activeProfile = n;
        return true;
    }

    public boolean deleteProfile(String name) {
        if (profiles.size() <= 1 || !profiles.containsKey(name)) return false;
        profiles.remove(name);
        if (activeProfile.equals(name)) activeProfile = profiles.keySet().iterator().next();
        return true;
    }

    /** Export enthält nur Einstellungen und Pack-Referenzen, keine Texturen. */
    public void exportProfile(String name, Map<String, String> selections, Path file) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("format", PROFILE_FORMAT);
        o.addProperty("name", name);
        o.add("selections", GSON.toJsonTree(selections));
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(o), StandardCharsets.UTF_8);
    }

    /** Importiert ein exportiertes Profil; gibt den (ggf. eindeutig gemachten) Namen zurück. */
    public String importProfile(Path file) throws IOException {
        if (Files.size(file) > 1024 * 1024) throw new IOException("Profildatei zu groß");
        JsonObject o;
        try {
            o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Keine gültige Profildatei");
        }
        if (!o.has("format") || !PROFILE_FORMAT.equals(o.get("format").getAsString())
                || !o.has("selections") || !o.get("selections").isJsonObject()) {
            throw new IOException("Unbekanntes Profilformat");
        }
        Map<String, String> sel = new LinkedHashMap<>();
        for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("selections").entrySet()) {
            if (e.getValue().isJsonPrimitive() && e.getKey().length() < 64 && e.getValue().getAsString().length() < 300) {
                sel.put(e.getKey(), e.getValue().getAsString());
            }
        }
        String base = o.has("name") ? validateName(o.get("name").getAsString()) : null;
        if (base == null) base = "Import";
        String name = base;
        for (int i = 2; profiles.containsKey(name); i++) name = base + " (" + i + ")";
        profiles.put(name, new Profile(sel));
        return name;
    }
}
