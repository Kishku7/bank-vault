package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/** Server -> client, sent just before the vault menu opens: the player's remembered UI state
 *  (v1.2 last-use memory) -- last tab examined, last sort method per tab, the section-titles
 *  checkbox, and their per-tab pins. The screen restores all of it on open. */
public record UiStateSyncPayload(String lastTab, List<TabSort> sorts, boolean showSections,
                                 List<TabPins> pins) implements CustomPacketPayload {

    public record TabSort(String tab, String sort) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TabSort> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TabSort::tab,
                ByteBufCodecs.STRING_UTF8, TabSort::sort,
                TabSort::new);
    }

    public record TabPins(String tab, List<String> ids) {
        public static final StreamCodec<RegistryFriendlyByteBuf, TabPins> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, TabPins::tab,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TabPins::ids,
                TabPins::new);
    }

    public static final Type<UiStateSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "ui_state_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UiStateSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, UiStateSyncPayload::lastTab,
            TabSort.CODEC.apply(ByteBufCodecs.list()), UiStateSyncPayload::sorts,
            ByteBufCodecs.BOOL, UiStateSyncPayload::showSections,
            TabPins.CODEC.apply(ByteBufCodecs.list()), UiStateSyncPayload::pins,
            UiStateSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
