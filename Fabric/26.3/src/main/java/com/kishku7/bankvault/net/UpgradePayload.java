package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: add (true) one upgrade from a held chest, or remove (false) one (Master+). */
public record UpgradePayload(boolean add) implements CustomPacketPayload {

    public static final Type<UpgradePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "upgrade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, UpgradePayload::add,
            UpgradePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
