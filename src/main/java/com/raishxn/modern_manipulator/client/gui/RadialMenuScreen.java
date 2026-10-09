package com.raishxn.modern_manipulator.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Hosts a radial menu.
 */
public class RadialMenuScreen extends Screen {

    private final RadialMenu menu;

    public RadialMenuScreen(RadialMenu menu) {
        super(Component.translatable("mm.gui.radial_menu"));
        this.menu = menu;
        this.menu.screen = this;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        menu.draw(graphics, width, height, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.onClick(width, height, mouseX, mouseY, button)) return true;

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
