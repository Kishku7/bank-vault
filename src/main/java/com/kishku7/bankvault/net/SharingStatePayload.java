package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server -> client: the player's bank membership + any pending invite, for the in-screen
 *  Sharing corner (v1.1). members has exactly one entry (the player) when solo; more = group.
 *  inviteFrom is empty when no invite is pending. */
public record SharingStatePayload(List<Member> members, String inviteFrom, int inviteLevel)
        implements CustomPacketPayload {

    public record Member(String uuid, String name, int level) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Member> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Member::uuid,
                ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.VAR_INT, Member::level,
                Member::new);
    }

    public static final Type<SharingStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "sharing_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SharingStatePayload> CODEC = StreamCodec.composite(
            Member.CODEC.apply(ByteBufCodecs.list()), SharingStatePayload::members,
            ByteBufCodecs.STRING_UTF8, SharingStatePayload::inviteFrom,
            ByteBufCodecs.VAR_INT, SharingStatePayload::inviteLevel,
            SharingStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
