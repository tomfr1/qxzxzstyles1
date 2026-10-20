# Resource Pack Mixer (Fabric, Minecraft 1.21.11, nur Client)

Kombiniert einzelne Texturkategorien (Hotbar, Inventar, Crystals, Werkzeuge …) aus verschiedenen lokalen
Resourcepacks. Die Mod erzeugt ein **Overlay-Pack** mit nur den gewählten Ressourcen und legt es mit höchster
Priorität über deine normalen Packs. Originalpacks werden nie verändert. Alles offline, keine Telemetrie.

## Ehrlicher Status

| Teil | Status |
|---|---|
| Kernlogik (`dev.rpmixer.core`: Pack-Reader, Limits, Kategorien, Abhängigkeitsauflösung, Overlay, Profile) | **kompiliert und getestet** (`tools/CoreSmokeTest.java`, 38 Prüfungen, ohne Minecraft) |
| Minecraft-Teil (`dev.rpmixer.client`: Keybind, Menü, Pack-Manager, Reload) | **NICHT kompiliert**, in dieser Umgebung war kein Zugriff auf Fabric-Maven/Yarn. Von Hand gegen Yarn 1.21.11 geschrieben. |
| Zuordnungstabelle `Categories.java` | aus Wissen über das 1.21.x-Format abgeleitet, **nicht gegen die Vanilla-JAR geprüft**; der Mapping-Selbsttest loggt beim ersten Menüöffnen fehlende Pfade |
| Ingame-Verhalten | **nicht getestet** |

Wahrscheinlichste Stellen für Compile-Fehler (Yarn-Namen in 1.21.11 prüfen):
`KeyBinding.Category.create(...)` und der 4-Argument-Konstruktor von `KeyBinding` (Kategorien-Umbau ab 1.21.9),
`GameOptions.resourcePacks` / `write()`, `ResourcePackManager.setEnabledProfiles/getEnabledIds/scanPacks`,
`TextFieldWidget.setPlaceholder`, `Util.getOperatingSystem().open(URI)`, `textRenderer.wrapLines`.
Neu im Menü-Umbau (Vorschau): `RenderPipelines.GUI_TEXTURED` und `DrawContext.drawTexture(...)` (PreviewCache),
`TextureManager.registerTexture/destroyTexture`, `NativeImage.read`, `Screen.renderBackground`-Override und der
protected Konstruktor von `ButtonWidget` (UiButton). Der Konstruktor von `NativeImageBackedTexture` wird per
Reflection gewählt, weil er sich zwischen Versionen geändert hat.
Die Fehlermeldung nennt jeweils die Zeile; meist ist es nur ein umbenannter Methodenname.

## Annahmen und Versionen (1.21.11)

Java 21, Fabric Loader 0.18.4, Fabric API 0.139.4+1.21.11, Yarn 1.21.11+build.4, Loom 1.14.
1.21.11 ist die letzte obfuscated Version (Yarn), ab 26.1 ändert sich das Build-Setup grundlegend.
Gradle-Version: die zur Loom-Version passende aus dem Fabric Example Mod (Branch 1.21.11) verwenden.
Keine Kompatibilität mit anderen Minecraft-Versionen behauptet.

## Architektur

- **Overlay als generierter Ordner-Pack** (`resourcepacks/ResourcePackMixer_Overlay`) statt virtuellem Provider:
  nutzt nur die öffentliche Pack-Manager-API, braucht keine Mixins auf Pack-Interna und überlebt Neustarts über
  `options.txt`. Nachteil: Der Pack ist im Vanilla-Menü sichtbar (nicht umsortieren, die Mod setzt ihn bei
  „Anwenden“ wieder ganz nach oben).
- Bau im Hintergrund-Thread in `resourcepackmixer/build`, dann Austausch per Verschieben (alter Stand wird bei
  Fehler wiederhergestellt). Danach genau **ein** `reloadResources()`; parallele Anwenden-Aufrufe werden abgewiesen.
- Bibliothek: importierte **lokale Kopien** (`resourcepackmixer/library`) plus erkannte Packs aus `resourcepacks`
  (nur gelesen). Nur Kopien lassen sich löschen (mit Bestätigung); Originale werden höchstens ausgeblendet.
  Die Variante „nur auf das Original verweisen“ ist nicht implementiert.
- ZIPs werden nie entpackt, sondern gestreamt. Schutz: Pfadvalidierung (Zip-Slip), max. 60 000 Dateien,
  2 GiB gesamt, 256 MiB je Datei, Kompressionsverhältnis ≤ 200:1, Byte-Zähler beim Lesen (gegen gefälschte
  Größenangaben), keine Symlinks.

## Kategorien und Abhängigkeiten

Jede Kategorie ist ein `Target` mit: Statusgruppen, Einzeldateien, Präfixen und Item-/Block-IDs. Für IDs werden
`items/<id>.json`, `models/item/<id>.json` und `blockstates/<id>.json` aufgelöst: erst die Verweise
(`model`, `base`, `parent`, `overrides`), dann die referenzierten Texturen samt `.mcmeta`, mit Namespaces.

- **Hotbar**: `gui/sprites/hud/hotbar*.png` (Hotbar, Auswahl, Offhand, Angriffsanzeige). In 1.21.x sind das
  einzelne Sprites, daher ist keine Extraktion aus `widgets.png` nötig; Hotbar und Inventar sind getrennt.
- **Inventar**: `gui/container/inventory.png`, Creative-Tabs, `sprites/container/inventory/`, leere Rüstungsslots.
  **Nicht** enthalten: Truhen, Öfen, Werkbank usw. (eigene Texturen, bewusst ausgelassen).
- **Crystals**: Entity-Textur `entity/end_crystal/` (inkl. Strahl) **und** Item-Icon/-Definition. **Trident**:
  `entity/trident.png`, `trident_riptide.png` und Item-Definition (Inventar, Hand, Wurf).
- **Werkzeuge/Schwert**: je Material (Holz, Stein, Kupfer, Eisen, Gold, Diamant, Netherit) einzeln; Auswahl für
  die ganze Kategorie plus Überschreibung je Material („Nicht überschreiben“ je Material unterbricht die Vererbung).
- **Mace, Anker (alle 5 Ladezustände), Glowstone**: Item-Definition, Blockstate, Modelle, Texturen.
- **Konfliktregel**: Brauchen zwei Targets dieselbe Zieldatei aus verschiedenen Packs, gewinnt das in
  `Categories.ALL` zuerst genannte; es gibt eine Warnung. **Gemeinsame Vorlagen-Modelle**
  (`item/generated`, `item/handheld`, `block/cube_all` …) werden nie übernommen, sonst würde ein Pack alle Items ändern.
- Fehlende Dateien: Fallback auf das, was darunter liegt (Vanilla/normale Packs), plus Hinweis im Log; nie Absturz.
- „Nicht überschreiben“ = die Mod liefert nichts. Ein separater „Vanilla erzwingen“-Modus ist **nicht** implementiert.

## Sky (Sonderfall)

Unterstützt werden nur normale Vanilla-Texturen (`environment/sun`, `moon_phases`, `end_sky`, `clouds`, ggf.
`environment/celestial/**`). Custom-Sky-Formate (OptiFine/MCPatcher `sky/`, `assets/*/sky/*.json`,
`fabricskyboxes/`) werden erkannt, **nicht übernommen** und im Menü als „nicht unterstützt“ markiert. Ich nenne
bewusst keine Zusatz-Mod, weil ich deren Kompatibilität mit 1.21.11 nicht geprüft habe.

## Build

```
# einmalig: Gradle-Wrapper erzeugen (oder gradlew*/gradle/wrapper aus dem Fabric Example Mod, Branch 1.21.11, kopieren)
gradle wrapper
./gradlew build            # Windows: gradlew.bat build
```
Ergebnis: `build/libs/resourcepackmixer-0.1.0.jar` (nicht die `-sources.jar`). Entwicklungsstart: `./gradlew runClient`.
Kernlogik-Test ohne Minecraft:
```
javac -d /tmp/out -cp gson.jar $(find src/main/java/dev/rpmixer/core -name '*.java')
java -cp /tmp/out:gson.jar tools/CoreSmokeTest.java
```

## Installation

Fabric Loader 0.18.x für 1.21.11 + Fabric API installieren, JAR nach `mods/`. Keine weiteren Abhängigkeiten.

## Bedienung

1. **F7** (änderbar unter Steuerung) öffnet das Menü.
2. Packs importieren: Pfad (ZIP/Ordner) ins Feld unten + „Importieren“, oder Datei ins Fenster ziehen. Packs aus
   `resourcepacks` erscheinen automatisch; „Neu einlesen“ aktualisiert.
3. Links Kategorie wählen, rechts Pack klicken (Suche oben). Bei Werkzeugen/Schwert mit `< >` ein Material wählen.
4. Grün = verfügbar, gelb = teilweise, grau = nicht vorhanden, rot = fehlt/ungültig. Tooltip zeigt Herkunft und erkannte Kategorien.
5. **Anwenden** erzeugt das Overlay und lädt einmal neu. **Zurücksetzen** setzt alle Kategorien auf „Nicht
   überschreiben“; mit **Anwenden** verschwindet das Overlay komplett.
6. Profile: `< >` wechseln, Namen ins Feld tippen für Neu/Umbenennen, Löschen mit Bestätigung. Export schreibt nur
   Einstellungen und Pack-Referenzen nach `resourcepackmixer/exports/`; Import: Pfad der `.json` ins Importfeld.
   „Neu“ kopiert die aktuelle Auswahl ins neue Profil.

## Menü

Seitenleiste mit Kategorien, Pack-Liste mit Miniaturvorschau und großes Vorschaufeld (Hover oder Auswahl). Packs ohne
die Textur der Kategorie sind ausgeblendet (Schalter „Alle“ zeigt sie, auch ungültige). Die Vorschau zeigt je Kategorie
eine typische Textur (Werkzeuge: Eisen zuerst, bei Materialwahl das gewählte Material); Animationsstreifen zeigen das
erste Bild. Vorschau = die Textur im Pack, keine 3D-Ansicht und nicht das Spiel-Rendering.

## Nicht umgesetzt (Etappe 2)

Pack-Icons (`pack.png`), 3D-/Ingame-Vorschau,
Verweis-Modus ohne Kopie, Vanilla-Erzwingen, Scrollrad-Liste (aktuell Seiten-Buttons), persistenter Index-Cache
(aktuell Cache im Speicher, ZIPs mit unveränderter Größe/Zeit werden nicht neu gelesen).

## Test-Checkliste (Ingame)

- [ ] Mod startet, F7 öffnet das Menü, Log enthält „Mapping-Selbsttest“ ohne (oder mit nachvollziehbaren) Fehlern.
- [ ] Hotbar aus Pack A + Crystals aus Pack B → Anwenden: Hotbar-Look von A, End Crystal (Welt + Item) von B, Rest unverändert.
- [ ] Inventar aus Pack C: Inventar (E) und Creative ändern sich, Hotbar bleibt wie gewählt.
- [ ] Axt/Spitzhacke/Schaufel/Hacke aus vier verschiedenen Packs; danach ein Material per Einzelwahl abweichend.
- [ ] Profile „Crystal PvP“/„Survival“ anlegen und wechseln → Anwenden → Look wechselt, ein Reload je Anwenden.
- [ ] Spiel neu starten: Auswahl und Overlay sind wieder da (`options.txt` enthält den Overlay-Eintrag).
- [ ] Pack aus `resourcepacks` löschen/umbenennen → Warnung „fehlt“, Kategorie fällt auf Normal zurück, kein Absturz.
- [ ] Kaputte ZIP und ZIP ohne `pack.mcmeta` importieren → verständliche Fehlermeldung.
- [ ] Pack ohne die gewählte Kategorie → Status „nicht vorhanden“; Pack mit nur Teilen → „teilweise“.
- [ ] Zwei Kategorien mit gleicher Zieldatei (Konflikt) → Warnung im Log, deterministisches Ergebnis.
- [ ] Sky-Pack mit OptiFine-Sky → Hinweis „nicht unterstützt“, Himmel bleibt normal.
- [ ] „Zurücksetzen“ + „Anwenden“ → Overlay-Ordner weg, Eintrag aus `options.txt` entfernt, Look wie ohne Mod.
- [ ] Zweimal schnell „Anwenden“ → zweiter Aufruf wird abgewiesen („Reload läuft bereits“).
- [ ] Originalpacks im `resourcepacks`-Ordner sind danach unverändert (Datum/Größe).
