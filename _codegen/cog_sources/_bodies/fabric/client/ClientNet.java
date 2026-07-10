package com.kishku7.bankvault.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client networking seam: shared client code sends to the server through this one name. */
public final class ClientNet {
    private ClientNet() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }
}
