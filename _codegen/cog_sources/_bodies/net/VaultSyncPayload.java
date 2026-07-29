package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Server -> client: the bank snapshot the screen renders (each entry carries the real ItemStack). */
public record VaultSyncPayload(List<Entry> entries, int upgradeCount, long capacity, int permLevel)
        implements CustomPacketPayload {

    /** key = plain item id, or "id#hash" for an NBT stack; stack is the display prototype. */
    public record Entry(String key, ItemStack stack, long count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::key,
                ItemStack.OPTIONAL_STREAM_CODEC, Entry::stack,
                ByteBufCodecs.VAR_LONG, Entry::count,
                Entry::new);
    }

    public static final Type<VaultSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "vault_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultSyncPayload> CODEC = StreamCodec.composite(
            Entry.CODEC.apply(ByteBufCodecs.list(BvWire.MAX_SYNC_ENTRIES)), VaultSyncPayload::entries,
            ByteBufCodecs.VAR_INT, VaultSyncPayload::upgradeCount,
            ByteBufCodecs.VAR_LONG, VaultSyncPayload::capacity,
            ByteBufCodecs.VAR_INT, VaultSyncPayload::permLevel,
            VaultSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
