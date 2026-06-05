package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: a Sharing-corner action (v1.1). All permission checks are server-side.
 *  op: 0=INVITE (target = player name, level = requested grant level)
 *      1=ACCEPT pending invite          2=DECLINE pending invite
 *      3=KICK   (target = member uuid)  4=LEVEL+1 (target = member uuid)
 *      5=LEVEL-1 (target = member uuid) 6=LEAVE bank */
public record ShareActionPayload(int op, String target, int level) implements CustomPacketPayload {

    public static final int INVITE = 0, ACCEPT = 1, DECLINE = 2, KICK = 3,
            LEVEL_UP = 4, LEVEL_DOWN = 5, LEAVE = 6;

    public static final Type<ShareActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "share_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShareActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ShareActionPayload::op,
            ByteBufCodecs.STRING_UTF8, ShareActionPayload::target,
            ByteBufCodecs.VAR_INT, ShareActionPayload::level,
            ShareActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
