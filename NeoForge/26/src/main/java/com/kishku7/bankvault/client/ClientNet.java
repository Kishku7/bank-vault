package com.kishku7.bankvault.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client networking seam: shared client code sends to the server through this one name. */
public final class ClientNet {
    private ClientNet() {}

    public static void sendToServer(CustomPacketPayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }
}
