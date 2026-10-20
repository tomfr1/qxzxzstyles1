package dev.rpmixer.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** Ein benanntes Profil: Auswahlschlüssel (z. B. "hotbar", "axe:iron") -> Pack-ID oder {@link OverlayBuilder#SKIP}. */
public final class Profile {
    public Map<String, String> selections = new LinkedHashMap<>();

    public Profile() {}

    public Profile(Map<String, String> sel) {
        this.selections = new LinkedHashMap<>(sel);
    }
}
