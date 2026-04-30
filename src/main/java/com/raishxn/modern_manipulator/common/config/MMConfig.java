package com.raishxn.modern_manipulator.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class MMConfig {

    public static final ForgeConfigSpec SERVER_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ForgeConfigSpec.LongValue MAX_SELECTION_SCAN_VOLUME;
    public static final ForgeConfigSpec.BooleanValue COPY_SAFE_BLOCK_ENTITY_NBT;
    public static final ForgeConfigSpec.BooleanValue COPY_GTCEU_CONFIGS;
    public static final ForgeConfigSpec.BooleanValue CLEAR_PASTE_TARGET_AFTER_PASTE;
    public static final ForgeConfigSpec.BooleanValue RESET_TRANSFORM_AFTER_PASTE;
    public static final ForgeConfigSpec.BooleanValue DROP_REPLACED_BLOCKS_ON_PASTE;
    public static final ForgeConfigSpec.LongValue ME_DOWNLINK_RANGE_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue RENDER_FILLED_BOXES;
    public static final ForgeConfigSpec.BooleanValue RENDER_SELECTION_RULERS;
    public static final ForgeConfigSpec.BooleanValue RENDER_BLUEPRINT_HINTS;
    public static final ForgeConfigSpec.LongValue MAX_RENDER_HINT_BLOCKS;

    static {
        ForgeConfigSpec.Builder serverBuilder = new ForgeConfigSpec.Builder();
        serverBuilder.push("building");
        MAX_SELECTION_SCAN_VOLUME = serverBuilder
                .comment("Maximum bounding-box scan volume for manipulator selections before an action is rejected.")
                .defineInRange("maxSelectionScanVolume", 262_144L, 1L, Long.MAX_VALUE);
        COPY_SAFE_BLOCK_ENTITY_NBT = serverBuilder
                .comment(
                        "Copy vanilla block entity NBT only when it has no item, fluid, or energy capabilities and no unsafe payload keys.")
                .define("copySafeBlockEntityNbt", true);
        COPY_GTCEU_CONFIGS = serverBuilder
                .comment(
                        "Copy and paste GTCEu machine and pipe configuration data such as facing, covers, auto-output, circuits, and pipe connections.")
                .define("copyGtceuConfigs", true);
        CLEAR_PASTE_TARGET_AFTER_PASTE = serverBuilder
                .comment("Clear Coord C after a paste finishes.")
                .define("clearPasteTargetAfterPaste", false);
        RESET_TRANSFORM_AFTER_PASTE = serverBuilder
                .comment("Reset rotation and mirror transform after a paste finishes.")
                .define("resetTransformAfterPaste", false);
        DROP_REPLACED_BLOCKS_ON_PASTE = serverBuilder
                .comment("Drop blocks overwritten by paste operations. Disabled by default to avoid cheap mining.")
                .define("dropReplacedBlocksOnPaste", false);
        serverBuilder.pop();
        serverBuilder.push("ae2");
        ME_DOWNLINK_RANGE_BLOCKS = serverBuilder
                .comment("Maximum distance from the player to a linked ME downlink. Set to 0 to allow any loaded same-dimension downlink.")
                .defineInRange("meDownlinkRangeBlocks", 1024L, 0L, Long.MAX_VALUE);
        serverBuilder.pop();
        SERVER_SPEC = serverBuilder.build();

        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        clientBuilder.push("render");
        RENDER_FILLED_BOXES = clientBuilder
                .comment("Render translucent filled selection and preview boxes like the original manipulator overlay.")
                .define("renderFilledBoxes", true);
        RENDER_SELECTION_RULERS = clientBuilder
                .comment("Render long guide/ruler lines from selected coordinates.")
                .define("renderSelectionRulers", true);
        RENDER_BLUEPRINT_HINTS = clientBuilder
                .comment("Render per-block paste hints for small blueprints, including blocked and replacement colors.")
                .define("renderBlueprintHints", true);
        MAX_RENDER_HINT_BLOCKS = clientBuilder
                .comment("Maximum number of paste preview blocks to render individually.")
                .defineInRange("maxRenderHintBlocks", 768L, 0L, Long.MAX_VALUE);
        clientBuilder.pop();
        CLIENT_SPEC = clientBuilder.build();
    }

    private MMConfig() {}

    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
