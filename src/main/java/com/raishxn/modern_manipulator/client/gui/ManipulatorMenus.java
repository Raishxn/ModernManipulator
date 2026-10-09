package com.raishxn.modern_manipulator.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;

import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.ManipulatorTier;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.BlockRemoveMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.BlockSelectMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PendingAction;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.Shape;
import com.raishxn.modern_manipulator.common.networking.Messages;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import static com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator.*;

/**
 * Builds the manipulator's radial menu (ported from ItemMatterManipulator).
 */
public class ManipulatorMenus {

    private ManipulatorMenus() {}

    public static void open(ItemMatterManipulator manipulator, ItemStack heldStack) {
        Minecraft.getInstance().setScreen(new RadialMenuScreen(getMenuOptions(manipulator, heldStack).build()));
    }

    // spotless:off
    public static RadialMenuBuilder getMenuOptions(ItemMatterManipulator manipulator, ItemStack heldStack) {
        ManipulatorTier tier = manipulator.tier;
        MMState initialState = ItemMatterManipulator.getState(heldStack);

        return new RadialMenuBuilder()
            .innerIcon(new ItemStack(manipulator))
            .pipe(builder -> {
                addCommonOptions(builder, tier, initialState);
            })
            .pipe(builder -> {
                switch (initialState.config.placeMode) {
                    case GEOMETRY -> addGeometryOptions(builder, heldStack, initialState);
                    case COPYING -> addCopyingOptions(builder, heldStack, initialState);
                    case MOVING -> addMovingOptions(builder, heldStack, initialState);
                    case EXCHANGING -> addExchangingOptions(builder, heldStack);
                    case CABLES -> addCableOptions(builder, heldStack);
                }
            });
    }

    private static void addCommonOptions(RadialMenuBuilder builder, ManipulatorTier tier, MMState state) {
        builder
            .branch()
                .label(I18n.get("mm.gui.set_mode"))
                .hidden(tier == ManipulatorTier.Tier0)
                .branch()
                    .label(I18n.get("mm.gui.set_remove_mode"))
                    .hidden(!state.hasCap(ALLOW_REMOVING))
                    .option()
                        .label(I18n.get("mm.gui.remove_none"))
                        .onClicked(() -> {
                            Messages.SetRemoveMode.sendToServer(BlockRemoveMode.NONE);
                        })
                    .done()
                    .option()
                        .label(I18n.get("mm.gui.remove_replaceable"))
                        .onClicked(() -> {
                            Messages.SetRemoveMode.sendToServer(BlockRemoveMode.REPLACEABLE);
                        })
                    .done()
                    .option()
                        .label(I18n.get("mm.gui.remove_all"))
                        .onClicked(() -> {
                            Messages.SetRemoveMode.sendToServer(BlockRemoveMode.ALL);
                        })
                    .done()
                .done()
                .option()
                    .label(I18n.get("mm.gui.geometry"))
                    .onClicked(() -> {
                        Messages.SetPlaceMode.sendToServer(PlaceMode.GEOMETRY);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.moving"))
                    .hidden(!state.hasCap(ALLOW_MOVING))
                    .onClicked(() -> {
                        Messages.SetPlaceMode.sendToServer(PlaceMode.MOVING);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.copying"))
                    .hidden(!state.hasCap(ALLOW_COPYING))
                    .onClicked(() -> {
                        Messages.SetPlaceMode.sendToServer(PlaceMode.COPYING);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.exchanging"))
                    .hidden(!state.hasCap(ALLOW_EXCHANGING))
                    .onClicked(() -> {
                        Messages.SetPlaceMode.sendToServer(PlaceMode.EXCHANGING);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.cables"))
                    .hidden(!state.hasCap(ALLOW_CABLES))
                    .onClicked(() -> {
                        Messages.SetPlaceMode.sendToServer(PlaceMode.CABLES);
                    })
                .done()
            .done()
            .branch()
                .label(I18n.get("mm.gui.set_remove_mode"))
                .hidden(tier != ManipulatorTier.Tier0 || !state.hasCap(ALLOW_REMOVING))
                .option()
                    .label(I18n.get("mm.gui.remove_none"))
                    .onClicked(() -> {
                        Messages.SetRemoveMode.sendToServer(BlockRemoveMode.NONE);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.remove_replaceable"))
                    .onClicked(() -> {
                        Messages.SetRemoveMode.sendToServer(BlockRemoveMode.REPLACEABLE);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.remove_all"))
                    .onClicked(() -> {
                        Messages.SetRemoveMode.sendToServer(BlockRemoveMode.ALL);
                    })
                .done()
            .done()
            .option()
                .label(I18n.get("mm.gui.edit_transform"))
                .onClicked((menu, option, mouseButton, doubleClicked) -> {
                    Minecraft.getInstance().setScreen(new TransformScreen(state.config.placeMode));
                })
            .done();
    }

    private static void addGeometryOptions(RadialMenuBuilder builder, ItemStack heldStack, MMState state) {
        builder
            .branch()
                .label(I18n.get("mm.gui.select_blocks"))
                .option()
                    .label(I18n.get("mm.gui.set_corners"))
                    .onClicked(() -> {
                        Messages.SetBlockSelectMode.sendToServer(BlockSelectMode.CORNERS);
                        Messages.SetPendingAction.sendToServer(PendingAction.GEOM_SELECTING_BLOCK);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.set_edges"))
                    .onClicked(() -> {
                        Messages.SetBlockSelectMode.sendToServer(BlockSelectMode.EDGES);
                        Messages.SetPendingAction.sendToServer(PendingAction.GEOM_SELECTING_BLOCK);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.set_faces"))
                    .onClicked(() -> {
                        Messages.SetBlockSelectMode.sendToServer(BlockSelectMode.FACES);
                        Messages.SetPendingAction.sendToServer(PendingAction.GEOM_SELECTING_BLOCK);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.set_volumes"))
                    .onClicked(() -> {
                        Messages.SetBlockSelectMode.sendToServer(BlockSelectMode.VOLUMES);
                        Messages.SetPendingAction.sendToServer(PendingAction.GEOM_SELECTING_BLOCK);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.set_all"))
                    .onClicked(() -> {
                        Messages.SetBlockSelectMode.sendToServer(BlockSelectMode.ALL);
                        Messages.SetPendingAction.sendToServer(PendingAction.GEOM_SELECTING_BLOCK);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.clear_all"))
                    .onClicked(() -> {
                        Messages.ClearBlocks.sendToServer();
                    })
                .done()
            .done()
            .branch()
                .label(I18n.get("mm.gui.set_shape"))
                .option()
                    .label(I18n.get("mm.gui.line"))
                    .onClicked(() -> {
                        Messages.SetShape.sendToServer(Shape.LINE);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.cube"))
                    .onClicked(() -> {
                        Messages.SetShape.sendToServer(Shape.CUBE);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.sphere"))
                    .onClicked(() -> {
                        Messages.SetShape.sendToServer(Shape.SPHERE);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.cylinder"))
                    .onClicked(() -> {
                        Messages.SetShape.sendToServer(Shape.CYLINDER);
                    })
                .done()
            .done()
            .branch()
                .label(I18n.get("mm.gui.move_coords"))
                .option()
                    .label(I18n.get("mm.gui.move_coord_a"))
                    .onClicked(() -> {
                        Messages.MoveA.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_all"))
                    .onClicked(() -> {
                        Messages.MoveAll.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_coord_b"))
                    .onClicked(() -> {
                        Messages.MoveB.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_here"))
                    .onClicked(() -> {
                        Messages.MoveHere.sendToServer();
                    })
                .done()
            .done();
    }

    private static void addCopyingOptions(RadialMenuBuilder builder, ItemStack heldStack, MMState initialState) {
        builder
            .option()
                .label(I18n.get("mm.gui.mark_copy"))
                .onClicked(() -> {
                    Messages.MarkCopy.sendToServer();
                })
            .done()
            .branch()
                .label(I18n.get("mm.gui.edit_stack"))
                .option()
                    .label(I18n.get("mm.gui.reset"))
                    .onClicked(() -> {
                        Messages.ResetArray.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.mark"))
                    .onClicked(() -> {
                        Messages.SetPendingAction.sendToServer(PendingAction.MARK_ARRAY);
                    })
                .done()
            .done()
            .branch()
                .label(I18n.get("mm.gui.planning"))
                .option()
                    .label(I18n.get("mm.gui.cancel_auto_plans"))
                    .onClicked(() -> {
                        Messages.CancelAutoPlans.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.plan_all_auto"))
                    .onClicked(() -> {
                        Messages.GetRequiredItems.sendToServer(MMUtils.PLAN_ALL | MMUtils.PLAN_AUTO_SUBMIT);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.plan_all_manual"))
                    .onClicked(() -> {
                        Messages.GetRequiredItems.sendToServer(MMUtils.PLAN_ALL);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.clear_manual_plans"))
                    .onClicked(() -> {
                        Messages.ClearManualPlans.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.plan_missing_manual"))
                    .onClicked(() -> {
                        Messages.GetRequiredItems.sendToServer(0);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.plan_missing_auto"))
                    .onClicked(() -> {
                        Messages.GetRequiredItems.sendToServer(MMUtils.PLAN_AUTO_SUBMIT);
                    })
                .done()
            .done()
            .option()
                .label(I18n.get("mm.gui.mark_paste"))
                .onClicked(() -> {
                    Messages.MarkPaste.sendToServer();
                })
            .done()
            .branch()
                .label(I18n.get("mm.gui.advanced_options"))
                .hidden(!initialState.hasCap(ALLOW_SMART_COPY))
                .option()
                    .hidden(!initialState.hasCap(ALLOW_SMART_COPY))
                    .label(() -> I18n.get(
                        "mm.gui.smart_copy.cribs_to_proxies",
                        I18n.get(initialState.config.replaceCribsWithProxies ? "mm.gui.smart_copy.on" : "mm.gui.smart_copy.off")))
                    .onClicked(() -> {
                        Messages.SetReplaceCribs.sendToServer();
                    })
                .done()
                .option()
                    // AE2 1.20 has no P2P tunnel that holds patterns, so this isn't ported (see PORTING_NOTES)
                    .hidden(true)
                    .label(() -> I18n.get(
                        "mm.gui.smart_copy.interfaces_to_p2p",
                        I18n.get(initialState.config.replaceInterfacesWithP2P ? "mm.gui.smart_copy.on" : "mm.gui.smart_copy.off")))
                    .onClicked(() -> {
                        Messages.SetReplaceInterfaces.sendToServer();
                    })
                .done()
            .done();
    }

    private static void addMovingOptions(RadialMenuBuilder builder, ItemStack heldStack, MMState initialState) {
        builder
            .option()
                .label(I18n.get("mm.gui.mark_cut"))
                .onClicked(() -> {
                    Messages.MarkCut.sendToServer();
                })
            .done()
            .option()
                .label(I18n.get("mm.gui.mark_paste"))
                .onClicked(() -> {
                    Messages.MarkPaste.sendToServer();
                })
            .done();
    }

    private static void addExchangingOptions(RadialMenuBuilder builder, ItemStack heldStack) {
        builder
            .branch()
                .label(I18n.get("mm.gui.edit_replace_whitelist"))
                .option()
                    .label(I18n.get("mm.gui.clear"))
                    .onClicked(() -> {
                        Messages.ClearWhitelist.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.add_block"))
                    .onClicked(() -> {
                        Messages.SetPendingAction.sendToServer(PendingAction.EXCH_ADD_REPLACE);
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.set_block"))
                    .onClicked(() -> {
                        Messages.SetPendingAction.sendToServer(PendingAction.EXCH_SET_REPLACE);
                    })
                .done()
            .done()
            .option()
                .label(I18n.get("mm.gui.set_block_to_replace_with"))
                .onClicked(() -> {
                    Messages.SetPendingAction.sendToServer(PendingAction.EXCH_SET_TARGET);
                })
            .done()
            .branch()
                .label(I18n.get("mm.gui.move_coords"))
                .option()
                    .label(I18n.get("mm.gui.move_coord_a"))
                    .onClicked(() -> {
                        Messages.MoveA.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_all"))
                    .onClicked(() -> {
                        Messages.MoveAll.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_coord_b"))
                    .onClicked(() -> {
                        Messages.MoveB.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_here"))
                    .onClicked(() -> {
                        Messages.MoveHere.sendToServer();
                    })
                .done()
            .done();
    }

    private static void addCableOptions(RadialMenuBuilder builder, ItemStack heldStack) {
        builder
            .option()
                .label(I18n.get("mm.gui.set_cable"))
                .onClicked(() -> {
                    Messages.SetPendingAction.sendToServer(PendingAction.PICK_CABLE);
                })
            .done()
            .branch()
                .label(I18n.get("mm.gui.move_coords"))
                .option()
                    .label(I18n.get("mm.gui.move_coord_a"))
                    .onClicked(() -> {
                        Messages.MoveA.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_all"))
                    .onClicked(() -> {
                        Messages.MoveAll.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_coord_b"))
                    .onClicked(() -> {
                        Messages.MoveB.sendToServer();
                    })
                .done()
                .option()
                    .label(I18n.get("mm.gui.move_here"))
                    .onClicked(() -> {
                        Messages.MoveHere.sendToServer();
                    })
                .done()
            .done();
    }
    // spotless:on
}
