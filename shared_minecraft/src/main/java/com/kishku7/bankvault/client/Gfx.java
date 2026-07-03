package com.kishku7.bankvault.client;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Era-stable drawing facade: shared GUI code draws through this one surface; the per-era
 *  graphics object and its renamed methods live behind it (cog-emitted per MC version). */
public final class Gfx {

    private final GuiGraphicsExtractor g;

    private Gfx(GuiGraphicsExtractor g) { this.g = g; }

    public static Gfx of(GuiGraphicsExtractor g) { return new Gfx(g); }

    public GuiGraphicsExtractor raw() { return g; }

    public void fill(int x0, int y0, int x1, int y1, int col) { g.fill(x0, y0, x1, y1, col); }

    public void text(Font font, String s, int x, int y, int color) { g.text(font, s, x, y, color); }

    public void item(ItemStack stack, int x, int y) { g.item(stack, x, y); }

    public void itemDecorations(Font font, ItemStack stack, int x, int y, String s) { g.itemDecorations(font, stack, x, y, s); }

    public void pushMatrix() { g.pose().pushMatrix(); }

    public void translate(float x, float y) { g.pose().translate(x, y); }

    public void scale(float sx, float sy) { g.pose().scale(sx, sy); }

    public void popMatrix() { g.pose().popMatrix(); }

    public void enableScissor(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }

    public void disableScissor() { g.disableScissor(); }

    public void blit(RenderPipeline pipeline, Identifier tex, int x, int y, float u, float v,
                     int w, int h, int tw, int th) { g.blit(pipeline, tex, x, y, u, v, w, h, tw, th); }

    public void setTooltipForNextFrame(Font font, List<Component> lines, Optional<TooltipComponent> comp,
                                       int mx, int my) { g.setTooltipForNextFrame(font, lines, comp, mx, my); }

    public void setTooltipForNextFrame(Font font, ItemStack stack, int mx, int my) { g.setTooltipForNextFrame(font, stack, mx, my); }

    public void entityInInventoryFollowsMouse(int x1, int y1, int x2, int y2, int scale, float yOff,
                                              float mouseX, float mouseY, LivingEntity entity) {
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, yOff, mouseX, mouseY, entity);
    }

    public void setTooltipForNextFrame(Font font, Component c, int mx, int my) { g.setTooltipForNextFrame(font, c, mx, my); }
}
