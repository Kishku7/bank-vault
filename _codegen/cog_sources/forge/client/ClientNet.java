package com.kishku7.bankvault.client;

import com.kishku7.bankvault.net.ModNetworking;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client networking seam: shared client code sends to the server through this one name. */
public final class ClientNet {
    private ClientNet() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ModNetworking.sendToServer(payload);
    }
}
