package dev.rpmixer.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Flacher, eigener Button-Look. Erbt von ButtonWidget und überschreibt nur das Zeichnen; Klick-, Fokus- und
 * Tooltip-Verhalten bleiben vom Spiel (robust gegenüber Änderungen der Eingabe-API).
 */
final class UiButton extends ButtonWidget {
    enum Style { NORMAL, PRIMARY, DANGER, CATEGORY, ROW }

    static final int TEXT = 0xFFE6E9F5;
    static final int MUTED = 0xFF8A92B2;
    static final int GREEN = 0xFF4ADE80;
    static final int YELLOW = 0xFFFACC15;
    static final int RED = 0xFFF87171;
    static final int ACCENT = 0xFF34D399;

    private final Style style;
    boolean selected;
    boolean marked;
    String subtitle = "";
    int subtitleColor = MUTED;
    PreviewCache.Preview thumb;
    boolean showThumbBox;

    UiButton(int x, int y, int w, int h, Text label, Style style, PressAction action) {
        super(x, y, w, h, label, action, DEFAULT_NARRATION_SUPPLIER);
        this.style = style;
    }

    UiButton selected(boolean v) {
        this.selected = v;
        return this;
    }

    UiButton marked(boolean v) {
        this.marked = v;
        return this;
    }

    UiButton sub(String text, int color) {
        this.subtitle = text;
        this.subtitleColor = color;
        return this;
    }

    UiButton thumb(PreviewCache.Preview t, boolean box) {
        this.thumb = t;
        this.showThumbBox = box;
        return this;
    }

    @Override
    public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = isHovered() && active;

        int bg;
        int border;
        switch (style) {
            case PRIMARY -> {
                bg = hover ? 0xFF2FA87A : 0xFF248F68;
                border = 0xFF3CCB96;
            }
            case DANGER -> {
                bg = hover ? 0xFF7A2E3A : 0xFF5E2430;
                border = 0xFF9B3B4B;
            }
            case CATEGORY -> {
                bg = selected ? 0xFF2A3350 : hover ? 0xFF232A42 : 0xFF1A1F32;
                border = selected ? 0xFF4C5C99 : 0xFF1A1F32;
            }
            case ROW -> {
                bg = selected ? 0xFF1F3D3A : hover ? 0xFF252C46 : 0xFF1A1F32;
                border = selected ? ACCENT : hover ? 0xFF4C5C99 : 0xFF2A3050;
            }
            default -> {
                bg = hover ? 0xFF2D3556 : 0xFF222842;
                border = hover ? 0xFF5568A8 : 0xFF38406A;
            }
        }
        if (!active) {
            bg = 0xFF171A2A;
            border = 0xFF171A2A;
        }
        ctx.fill(x, y, x + w, y + h, border);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        String title = getMessage().getString();
        int textColor = active ? TEXT : MUTED;

        if (style == Style.ROW) {
            int ts = h - 4;
            int tx = x + 3;
            int ty = y + 2;
            int textX = tx;
            if (showThumbBox) {
                ctx.fill(tx, ty, tx + ts, ty + ts, 0xFF0B0D17);
                if (thumb != null) {
                    if (thumb.state == PreviewCache.State.READY) {
                        PreviewCache.draw(ctx, thumb, tx + 1, ty + 1, ts - 2, ts - 2);
                    } else {
                        String m = thumb.state == PreviewCache.State.LOADING ? "…" : "–";
                        ctx.drawTextWithShadow(tr, m, tx + (ts - tr.getWidth(m)) / 2, ty + (ts - 8) / 2, MUTED);
                    }
                } else {
                    String m = "–";
                    ctx.drawTextWithShadow(tr, m, tx + (ts - tr.getWidth(m)) / 2, ty + (ts - 8) / 2, 0xFF4A5070);
                }
                textX = tx + ts + 5;
            } else {
                textX = x + 6;
            }
            int maxW = x + w - textX - 4;
            String t = tr.trimToWidth(title, maxW);
            if (subtitle.isEmpty()) {
                ctx.drawTextWithShadow(tr, t, textX, y + (h - 8) / 2, textColor);
            } else {
                ctx.drawTextWithShadow(tr, t, textX, y + 4, textColor);
                ctx.drawTextWithShadow(tr, tr.trimToWidth(subtitle, maxW), textX, y + h - 12, subtitleColor);
            }
            if (selected) ctx.fill(x + w - 5, y + 4, x + w - 3, y + h - 4, ACCENT);
            return;
        }

        if (style == Style.CATEGORY) {
            if (marked) ctx.fill(x, y + 1, x + 2, y + h - 1, GREEN);
            else if (selected) ctx.fill(x, y + 1, x + 2, y + h - 1, 0xFF7C8DD6);
            String t = tr.trimToWidth(title, w - 10);
            ctx.drawTextWithShadow(tr, t, x + 6, y + (h - 8) / 2, selected ? 0xFFFFFFFF : textColor);
            return;
        }

        String t = tr.trimToWidth(title, w - 4);
        ctx.drawTextWithShadow(tr, t, x + (w - tr.getWidth(t)) / 2, y + (h - 8) / 2, textColor);
    }
}
