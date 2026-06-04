package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Client -> server: the bank key shown in each visible grid cell, in cell order, so a real-Slot
 *  click on the vault view maps to the correct stored item. An empty string marks an empty cell. */
public record GridViewPayload(List<String> keys) implements CustomPacketPayload {

    public static final Type<GridViewPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "grid_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GridViewPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), GridViewPayload::keys,
            GridViewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
