package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: toggle a per-tab user pin (v1.2 Pin hot area). Dropping a carried item on
 *  the Pin box pins it for that user on that tab; dropping it again unpins. The cursor stack is
 *  never touched -- this only flips the persisted preference. */
public record PinPayload(String tab, String itemId) implements CustomPacketPayload {

    public static final Type<PinPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "pin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PinPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PinPayload::tab,
            ByteBufCodecs.STRING_UTF8, PinPayload::itemId,
            PinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
