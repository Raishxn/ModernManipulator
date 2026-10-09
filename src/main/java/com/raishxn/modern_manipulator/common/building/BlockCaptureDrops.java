package com.raishxn.modern_manipulator.common.building;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Captures item drops spawned in a world while capturing is active.
 * Replaces the old 1.7.10 mixin based drop capturing.
 */
public class BlockCaptureDrops {

    private static final Map<Level, Deque<List<ItemStack>>> CAPTURES = new IdentityHashMap<>();

    public static void captureDrops(Level world) {
        CAPTURES.computeIfAbsent(world, w -> new ArrayDeque<>()).push(new ArrayList<>());
    }

    public static List<ItemStack> stopCapturingDrops(Level world) {
        Deque<List<ItemStack>> stack = CAPTURES.get(world);

        if (stack == null || stack.isEmpty()) return new ArrayList<>();

        List<ItemStack> out = stack.pop();

        if (stack.isEmpty()) CAPTURES.remove(world);

        return out;
    }

    public static boolean isCapturing(Level world) {
        Deque<List<ItemStack>> stack = CAPTURES.get(world);
        return stack != null && !stack.isEmpty();
    }

    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity item)) return;

        Deque<List<ItemStack>> stack = CAPTURES.get(event.getLevel());

        if (stack == null || stack.isEmpty()) return;

        stack.peek().add(item.getItem().copy());
        event.setCanceled(true);
    }
}
