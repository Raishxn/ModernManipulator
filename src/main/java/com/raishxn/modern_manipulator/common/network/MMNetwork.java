package com.raishxn.modern_manipulator.common.network;

import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import com.raishxn.modern_manipulator.ModernManipulator;

public final class MMNetwork {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ModernManipulator.id("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int packetId;

    private MMNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(packetId++, ManipulatorConfigPacket.class, ManipulatorConfigPacket::encode,
                ManipulatorConfigPacket::decode, ManipulatorConfigPacket::handle);
    }
}
