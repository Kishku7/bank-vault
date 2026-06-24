package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: a UI interaction worth remembering (v1.2 last-use memory). {@code lastTab}
 *  is the button key now selected; {@code tab}+{@code sort} record the sort method in effect on
 *  that tab ("family" | "type" | "alpha" | "count_asc" | "count_desc"); {@code sections} flips
 *  the section-titles checkbox ("on" | "off"). Empty strings skip that part of the update. */
public record UiStatePayload(String lastTab, String tab, String sort, String sections)
        implements CustomPacketPayload {

    public static final Type<UiStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "ui_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UiStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UiStatePayload::lastTab,
            ByteBufCodecs.STRING_UTF8, UiStatePayload::tab,
            ByteBufCodecs.STRING_UTF8, UiStatePayload::sort,
            ByteBufCodecs.STRING_UTF8, UiStatePayload::sections,
            UiStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
