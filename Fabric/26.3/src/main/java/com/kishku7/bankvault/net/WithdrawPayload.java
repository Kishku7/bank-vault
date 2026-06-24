package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: withdraw {amount} of {itemId}. */
public record WithdrawPayload(String itemId, int amount) implements CustomPacketPayload {

    public static final Type<WithdrawPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "withdraw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WithdrawPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WithdrawPayload::itemId,
            ByteBufCodecs.VAR_INT, WithdrawPayload::amount,
            WithdrawPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
