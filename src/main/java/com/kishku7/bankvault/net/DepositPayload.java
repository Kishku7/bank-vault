package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: deposit the stack in the given player-inventory slot (0–35) into the vault. */
public record DepositPayload(int slot) implements CustomPacketPayload {

    public static final Type<DepositPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DepositPayload::slot,
            DepositPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
