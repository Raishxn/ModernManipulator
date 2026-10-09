package com.raishxn.modern_manipulator.client.rendering;

import com.raishxn.modern_manipulator.GlobalMMConfig;
import com.raishxn.modern_manipulator.ModernManipulator;
import com.raishxn.modern_manipulator.common.building.BlockSpec;
import com.raishxn.modern_manipulator.common.building.PendingBlock;
import com.raishxn.modern_manipulator.common.items.manipulator.ItemMatterManipulator;
import com.raishxn.modern_manipulator.common.items.manipulator.Location;
import com.raishxn.modern_manipulator.common.items.manipulator.MMConfig;
import com.raishxn.modern_manipulator.common.items.manipulator.MMConfig.VoxelAABB;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.PlaceMode;
import com.raishxn.modern_manipulator.common.items.manipulator.MMState.Shape;
import com.raishxn.modern_manipulator.common.utils.MMUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.joml.Vector3f;
import org.joml.Vector3i;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class MMRenderer {

    private static long lastAnalysisMS = 0;
    private static MMConfig lastAnalyzedConfig = null;
    private static Location lastPlayerPosition = null;
    private static List<PendingBlock> analysisCache = null;
    private static ItemMatterManipulator lastDrawer = null;
    private static boolean wasValid = false;
    private static final long ANALYSIS_INTERVAL_MS = 10_000;
    private static long lastExceptionPrint = 0;
    private static boolean needsHintDraw = false;
    private static boolean needsAnalysis = false;
    private static LongList errors, warnings;
    private static long statusExpiration = 0;
    private static boolean wasInUse = false;

    private static final Vector3f BLUE = new Vector3f(0.15f, 0.6f, 0.75f);
    private static final Vector3f ORANGE = new Vector3f(0.75f, 0.5f, 0.15f);

    private static final int WHITE = 0xFFE5F2FF;
    private static final int WARNING = 0xFFFFAA00;
    private static final int ERROR = 0xFFFF5555;

    private MMRenderer() {}

    public static void markNeedsRedraw() {
        needsHintDraw = true;
    }

    public static void markNeedsReanalysis() {
        needsAnalysis = true;
    }

    public static void setStatusHints(LongList errors, LongList warnings) {
        needsHintDraw = true;
        MMRenderer.errors = errors;
        MMRenderer.warnings = warnings;

        int exp = GlobalMMConfig.RenderingConfig.statusExpiration.get();
        statusExpiration = exp <= 0 ? 0 : System.currentTimeMillis() + exp * 1000L;
    }

    public static void renderSelection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        try {
            renderSelectionImpl(event);
        } catch (Throwable t) {
            ModernManipulator.LOG.error("Could not render matter manipulator preview", t);

            long now = System.currentTimeMillis();
            if ((now - lastExceptionPrint) > 10_000 && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal(
                    "Could not render preview due to a crash. Check the logs for more info. Building will not work - items may be voided if you try.")
                    .withStyle(ChatFormatting.RED));
                lastExceptionPrint = now;
            }
        }
    }

    private static void renderSelectionImpl(RenderLevelStageEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);

        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        if (held.getItem() instanceof ItemMatterManipulator manipulator) {
            MMState state = ItemMatterManipulator.getState(held);

            BoxRenderer.INSTANCE.start(pose, camera);

            try {
                switch (state.config.placeMode) {
                    case GEOMETRY, EXCHANGING, CABLES -> renderGeom(player, state, manipulator);
                    case COPYING, MOVING -> renderRegions(player, state, manipulator);
                }
            } finally {
                BoxRenderer.INSTANCE.finish();
            }

            RenderHints.INSTANCE.render(pose, event.getProjectionMatrix(), camera);
        } else {
            if (lastDrawer != null) {
                lastDrawer = null;
                clear(player);
            }
        }
    }

    private static void clear(Player player) {
        lastAnalysisMS = 0;
        lastAnalyzedConfig = null;
        lastPlayerPosition = null;
        analysisCache = null;
        needsHintDraw = false;
        needsAnalysis = false;

        RenderHints.INSTANCE.reset();
    }

    private static void hud(String text) {
        Minecraft.getInstance().gui.setOverlayMessage(Component.literal(text), false);
    }

    public static void checkPlayerStoppedBuilding(TickEvent.PlayerTickEvent event) {
        if (!event.player.level().isClientSide) return;
        if (event.phase != TickEvent.Phase.END) return;

        ItemStack inUse = event.player.getUseItem();

        if (!inUse.isEmpty()) {
            if (inUse.getItem() instanceof ItemMatterManipulator) wasInUse = true;
            return;
        }

        if (wasInUse) {
            markNeedsReanalysis();
            wasInUse = false;
        }
    }

    private static Location playerLocation(Player player) {
        return new Location(player.level(), Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()));
    }

    private static void rulers(Location l, float r, float g, float b) {
        BoxRenderer.INSTANCE.drawRulers(l.x, l.y, l.z, r, g, b, 0.75f);
    }

    private static void renderGeom(Player player, MMState state, ItemMatterManipulator manipulator) {
        Level world = player.level();
        Vector3i lookingAt = MMUtils.getLookingAtLocation(player);

        Location coordA = state.config.getCoordA(world, lookingAt);
        Location coordB = state.config.getCoordB(world, lookingAt);
        Location coordC = state.config.getCoordC(world, lookingAt);

        state.config.coordA = coordA;
        state.config.coordB = coordB;
        state.config.coordC = coordC;

        boolean isAValid = coordA != null && coordA.isInWorld(world);
        boolean isBValid = coordB != null && coordB.isInWorld(world);
        boolean isCValid = coordC != null && coordC.isInWorld(world);

        boolean isValid = isAValid && isBValid;

        // For cylinders, coord B must be pinned to one of the axis planes and coord C must be on the normal of that plane
        if (state.config.placeMode == PlaceMode.GEOMETRY && state.config.shape == Shape.CYLINDER) {
            isValid &= isCValid;

            if (isAValid && isBValid) {
                Vector3i b2 = MMState.pinToPlanes(coordA.toVec(), coordB.toVec());

                coordB.x = b2.x;
                coordB.y = b2.y;
                coordB.z = b2.z;

                if (isCValid) {
                    Vector3i height = MMState.pinToLine(coordA.toVec(), b2, coordC.toVec());

                    coordC.x = height.x;
                    coordC.y = height.y;
                    coordC.z = height.z;
                }
            }
        }

        if (!isValid && wasValid) {
            clear(player);
            wasValid = false;
            return;
        }

        wasValid = isValid;

        // For cables, coord B must be somewhere on one of the axes
        if (isAValid && isBValid && state.config.placeMode == PlaceMode.CABLES) {
            Vector3i b = MMState.pinToAxes(coordA.toVec(), coordB.toVec());

            coordB.x = b.x;
            coordB.y = b.y;
            coordB.z = b.z;
        }

        if (isAValid && state.config.coordAOffset != null) rulers(coordA, 0.15f, 0.6f, 0.75f);
        if (isBValid && state.config.coordBOffset != null) rulers(coordB, 0.15f, 0.6f, 0.75f);
        if (isCValid && state.config.coordCOffset != null) rulers(coordC, 0.15f, 0.6f, 0.75f);

        if (isAValid && isBValid) {
            Location playerLocation = playerLocation(player);

            VoxelAABB aabb = new VoxelAABB(coordA.toVec(), coordB.toVec());

            // expand the AABB if the shape uses coord C
            if ((state.config.placeMode != PlaceMode.GEOMETRY || state.config.shape.requiresC()) && isCValid) {
                aabb.union(coordC.toVec());
            }

            BoxRenderer.INSTANCE.drawAround(aabb.toBoundingBox(), BLUE);

            updateHints(player, state, manipulator, playerLocation, aabb.describe());
        }
    }

    private static void updateHints(Player player, MMState state, ItemMatterManipulator manipulator, Location playerLocation,
                                    String hudText) {
        long now = System.currentTimeMillis();

        if (statusExpiration > 0 && now > statusExpiration) {
            errors = null;
            warnings = null;
            statusExpiration = 0;
            needsHintDraw = true;
        }

        needsAnalysis = needsAnalysis || (now - lastAnalysisMS) >= ANALYSIS_INTERVAL_MS || lastDrawer != manipulator ||
            !Objects.equals(lastAnalyzedConfig, state.config);

        needsHintDraw = needsHintDraw || needsAnalysis || lastPlayerPosition == null ||
            (lastPlayerPosition.distanceTo(playerLocation) > 2 && manipulator.tier.maxRange != -1);

        if (needsAnalysis) {
            lastAnalysisMS = now;
            lastAnalyzedConfig = state.config;
            analysisCache = state.getPendingBlocks(manipulator.tier, player.level());
            analysisCache.removeIf(Objects::isNull);
            analysisCache.sort(Comparator.comparingInt((PendingBlock b) -> b.renderOrder));
            needsAnalysis = false;

            if (hudText != null) hud(hudText);
        }

        if (needsHintDraw) {
            lastPlayerPosition = playerLocation;
            lastDrawer = manipulator;
            needsHintDraw = false;

            drawHints(state, player, playerLocation, manipulator.tier.maxRange);
        }
    }

    private static void renderRegions(Player player, MMState state, ItemMatterManipulator manipulator) {
        Level world = player.level();

        Location sourceA = state.config.coordA;
        Location sourceB = state.config.coordB;
        Location paste = state.config.coordC;

        Vector3i lookingAt = MMUtils.getLookingAtLocation(player);

        if (state.config.action != null) {
            switch (state.config.action) {
                case MARK_COPY_A, MARK_CUT_A -> {
                    sourceA = new Location(world, lookingAt);
                    rulers(sourceA, 0.15f, 0.6f, 0.75f);
                }
                case MARK_COPY_B, MARK_CUT_B -> {
                    sourceB = new Location(world, lookingAt);
                    rulers(sourceB, 0.15f, 0.6f, 0.75f);
                }
                case MARK_PASTE -> {
                    paste = new Location(world, lookingAt);
                    rulers(paste, 0.75f, 0.5f, 0.15f);
                }
                case MARK_ARRAY -> {
                    rulers(new Location(world, lookingAt), 0.4f, 0.75f, 0.15f);

                    if (paste != null && paste.isInWorld(world)) {
                        state.config.arraySpan = state.config.getArrayMult(world, sourceA, sourceB, paste, lookingAt);
                    }
                }
                default -> {
                    return;
                }
            }
        }

        state.config.coordA = sourceA;
        state.config.coordB = sourceB;
        state.config.coordC = paste;

        boolean isSourceAValid = sourceA != null && sourceA.isInWorld(world);
        boolean isSourceBValid = sourceB != null && sourceB.isInWorld(world);
        boolean isPasteValid = paste != null && paste.isInWorld(world);

        boolean isValid = isSourceAValid && isSourceBValid && isPasteValid;

        if (!isValid && wasValid) {
            clear(player);
            wasValid = false;
            return;
        }

        wasValid = isValid;

        VoxelAABB copyDeltas = null;

        if (isSourceAValid && isSourceBValid) {
            copyDeltas = new VoxelAABB(sourceA.toVec(), sourceB.toVec());

            BoxRenderer.INSTANCE.drawAround(copyDeltas.toBoundingBox(), BLUE);
        }

        VoxelAABB pasteDeltas = null;

        if (isPasteValid) {
            pasteDeltas = state.config.getPasteVisualDeltas(world, state.config.placeMode == PlaceMode.COPYING);

            if (pasteDeltas == null) pasteDeltas = new VoxelAABB(paste.toVec(), paste.toVec());

            BoxRenderer.INSTANCE.drawAround(pasteDeltas.toBoundingBox(), ORANGE);

            updateHints(player, state, manipulator, playerLocation(player), null);
        }

        if (pasteDeltas != null) {
            String array = "";

            Vector3i span = state.config.arraySpan;
            if (span != null) {
                array = String.format(" stX=%d stY=%d stZ=%d", span.x >= 0 ? span.x + 1 : span.x, span.y >= 0 ? span.y + 1 : span.y,
                    span.z >= 0 ? span.z + 1 : span.z);
            }

            hudOnce(pasteDeltas.describe() + array);
        } else if (copyDeltas != null) {
            hudOnce(copyDeltas.describe());
        }
    }

    private static String lastHud;
    private static long lastHudMS;

    private static void hudOnce(String text) {
        long now = System.currentTimeMillis();

        if (!text.equals(lastHud) || now - lastHudMS > 2000) {
            lastHud = text;
            lastHudMS = now;
            hud(text);
        }
    }

    private static void drawHints(MMState state, Player player, Location playerLocation, int maxRange) {
        int buildable = maxRange * maxRange;

        int i = 0;

        BlockSpec pooled = new BlockSpec();

        LongOpenHashSet errors = MMRenderer.errors == null ? null : new LongOpenHashSet(MMRenderer.errors);
        LongOpenHashSet warnings = MMRenderer.warnings == null ? null : new LongOpenHashSet(MMRenderer.warnings);

        Level world = player.level();

        int maxHints = GlobalMMConfig.RenderingConfig.maxHints.get();

        RenderHints.INSTANCE.start();
        RenderHints.INSTANCE.setDepthTest(!GlobalMMConfig.RenderingConfig.hintsOnTop.get() && state.config.placeMode != PlaceMode.EXCHANGING);

        if (analysisCache != null) {
            for (PendingBlock pendingBlock : analysisCache) {
                if (!pendingBlock.isInWorld(world)) continue;

                if (maxRange != -1 && pendingBlock.distanceTo2(playerLocation) > buildable) continue;

                BlockPos pos = new BlockPos(pendingBlock.x, pendingBlock.y, pendingBlock.z);

                if (pendingBlock.spec.isAir() && world.isEmptyBlock(pos)) continue;

                BlockSpec.fromBlock(pooled, world, pos);

                if (pooled.isEquivalent(pendingBlock.spec) && pooled.getBlockState() == pendingBlock.getBlockState()) continue;

                if (++i > maxHints) break;

                long packed = pos.asLong();

                int tint = WHITE;

                if (warnings != null && warnings.remove(packed)) tint = WARNING;
                if (errors != null && errors.remove(packed)) tint = ERROR;

                if (pendingBlock.spec.isAir()) {
                    RenderHints.INSTANCE.addHint(pendingBlock.x, pendingBlock.y, pendingBlock.z, null, tint == WHITE ? ERROR : tint);
                } else {
                    RenderHints.INSTANCE.addHint(pendingBlock.x, pendingBlock.y, pendingBlock.z, pendingBlock.getPreviewState(), tint);
                }
            }
        }

        if (warnings != null) {
            for (long packed : warnings) {
                BlockPos p = BlockPos.of(packed);
                RenderHints.INSTANCE.addHint(p.getX(), p.getY(), p.getZ(), null, WARNING);
            }
        }

        if (errors != null) {
            for (long packed : errors) {
                BlockPos p = BlockPos.of(packed);
                RenderHints.INSTANCE.addHint(p.getX(), p.getY(), p.getZ(), null, ERROR);
            }
        }

        RenderHints.INSTANCE.finish();
    }
}
