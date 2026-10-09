package com.raishxn.modern_manipulator;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * Port of the original GlobalMMConfig. Values are read lazily because forge configs load after class init.
 */
public final class GlobalMMConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final ForgeConfigSpec CLIENT_SPEC;

    public static final class InteractionConfig {

        /** Clear the paste region when the copy or cut regions are marked */
        public static ForgeConfigSpec.BooleanValue pasteAutoClear;
        /** Clear the transform and the stacking amount when the coordinates are cleared */
        public static ForgeConfigSpec.BooleanValue resetTransform;
    }

    public static final class RenderingConfig {

        public static ForgeConfigSpec.IntValue maxHints;
        public static ForgeConfigSpec.IntValue statusExpiration;
        public static ForgeConfigSpec.BooleanValue hintsOnTop;
    }

    public static final class DebugConfig {

        public static ForgeConfigSpec.BooleanValue debug;

        public static boolean debug() {
            return debug != null && COMMON_SPEC.isLoaded() && debug.get();
        }
    }

    public static final class BuildingConfig {

        public static ForgeConfigSpec.BooleanValue meEmptying;
        public static ForgeConfigSpec.IntValue mk3BlocksPerPlace;

        public static int mk3BlocksPerPlace() {
            return COMMON_SPEC.isLoaded() ? mk3BlocksPerPlace.get() : 256;
        }
    }

    static {
        ForgeConfigSpec.Builder common = new ForgeConfigSpec.Builder();

        common.push("interaction");
        InteractionConfig.pasteAutoClear = common
                .comment("Clear the paste region when the copy or cut regions are marked")
                .define("pasteAutoClear", true);
        InteractionConfig.resetTransform = common
                .comment("Clear the transform and the stacking amount when the coordinates are cleared")
                .define("resetTransform", true);
        common.pop();

        common.push("debug");
        DebugConfig.debug = common.comment("Enable Debug Logging").define("debug", false);
        common.pop();

        common.push("building");
        BuildingConfig.meEmptying = common
                .comment("Empty ME Output Hatches/Busses when they're removed. Server only.")
                .define("meEmptying", true);
        BuildingConfig.mk3BlocksPerPlace = common
                .comment("High values may cause world desync and lag. Server only. Requires restart.")
                .defineInRange("mk3BlocksPerPlace", 256, 1, Integer.MAX_VALUE);
        common.pop();

        COMMON_SPEC = common.build();

        ForgeConfigSpec.Builder client = new ForgeConfigSpec.Builder();

        client.push("rendering");
        RenderingConfig.maxHints = client
                .comment("Controls how many blocks are shown in the preview. Client only.")
                .defineInRange("maxHints", 1_000_000, 0, Integer.MAX_VALUE);
        RenderingConfig.statusExpiration = client
                .comment(
                        "Controls the duration of the build status warning/error hints (seconds). Client only. Set to 0 to never clear hints.")
                .defineInRange("statusExpiration", 60, 0, Integer.MAX_VALUE);
        RenderingConfig.hintsOnTop = client
                .comment("When true, hints will always be drawn on top of the terrain. Client only.")
                .define("hintsOnTop", true);
        client.pop();

        CLIENT_SPEC = client.build();
    }

    private GlobalMMConfig() {}

    @SuppressWarnings("removal")
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }
}
