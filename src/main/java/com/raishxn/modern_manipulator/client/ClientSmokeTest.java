package com.raishxn.modern_manipulator.client;

import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.client.gui.ManipulatorMenus;
import com.raishxn.modern_manipulator.client.gui.RadialMenu;
import com.raishxn.modern_manipulator.client.gui.RadialMenuScreen;
import com.raishxn.modern_manipulator.client.rendering.RenderHints;
import com.raishxn.modern_manipulator.common.items.MMItems;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only client smoke test, enabled with -Dmm.clientSmokeTest=true.
 * Renders every page of the radial menu of every tier and builds the hint vertex buffer, then exits.
 */
@Mod.EventBusSubscriber(modid = ModernManipulator.MOD_ID, value = Dist.CLIENT)
public class ClientSmokeTest {

    private static boolean done = false;

    @SubscribeEvent
    public static void onTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (done || !Boolean.getBoolean("mm.clientSmokeTest") || event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();

        if (!(mc.screen instanceof TitleScreen title)) return;
        done = true;

        ModernManipulator.LOG.info("MM client smoke test starting");

        var graphics = new net.minecraft.client.gui.GuiGraphics(mc, mc.renderBuffers().bufferSource());
        int pages = 0;

        try {
            for (var item : new net.minecraft.world.item.Item[] { MMItems.MK0.get(), MMItems.MK1.get(), MMItems.MK2.get(), MMItems.MK3.get() }) {
                ItemMatterManipulator manipulator = (ItemMatterManipulator) item;

                for (var mode : com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode.values()) {
                    ItemStack stack = manipulator.getDefaultInstance();
                    var state = ItemMatterManipulator.getState(stack);
                    state.config.placeMode = mode;
                    ItemMatterManipulator.setState(stack, state);

                    RadialMenu menu = ManipulatorMenus.getMenuOptions(manipulator, stack).build();
                    RadialMenuScreen screen = new RadialMenuScreen(menu);
                    screen.init(mc, title.width, title.height);
                    screen.render(graphics, 0, 0, 0);
                    pages++;
                }
            }

            RenderHints.INSTANCE.start();
            RenderHints.INSTANCE.addHint(0, 0, 0, Blocks.STONE.defaultBlockState(), 0xFFFFFFFF);
            RenderHints.INSTANCE.addHint(1, 0, 0, null, 0xFFFF5555);
            RenderHints.INSTANCE.finish();
            RenderHints.INSTANCE.forceRebuild();

            ModernManipulator.LOG.info("MM client smoke test passed ({} radial menus rendered)", pages);
        } catch (Throwable t) {
            ModernManipulator.LOG.error("MM client smoke test FAILED", t);
        }

        mc.stop();
    }
}
