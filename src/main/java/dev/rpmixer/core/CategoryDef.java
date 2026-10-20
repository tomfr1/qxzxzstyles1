package dev.rpmixer.core;

import java.util.ArrayList;
import java.util.List;

public final class CategoryDef {
    public final String id;
    public final boolean perMaterial;
    public final List<Target> targets;

    public CategoryDef(String id, boolean perMaterial, List<Target> targets) {
        this.id = id;
        this.perMaterial = perMaterial;
        this.targets = targets;
    }

    /** materialIndex 0 = ganze Kategorie, 1..n = Material aus {@link Categories#MATERIALS}. */
    public List<Target> targetsFor(int materialIndex) {
        if (!perMaterial || materialIndex <= 0) return targets;
        String suffix = ":" + Categories.MATERIALS.get(materialIndex - 1);
        List<Target> out = new ArrayList<>();
        for (Target t : targets) if (t.key.endsWith(suffix)) out.add(t);
        return out;
    }

    public String keyFor(int materialIndex) {
        if (!perMaterial || materialIndex <= 0) return id;
        return id + ":" + Categories.MATERIALS.get(materialIndex - 1);
    }
}
