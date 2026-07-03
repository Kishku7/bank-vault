package com.kishku7.bankvault.net;

import com.kishku7.bankvault.BankVault;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: bulk-deposit the player inventory into the vault.
 *  includeHotbar=false -> the 27 main slots only ("Deposit: Inventory");
 *  includeHotbar=true  -> 27 main + 9 hotbar ("Deposit: All").
 *  NEVER touches armor, offhand, trinket, backpack, or crafting slots (Kishku7, v1.1 spec). */
public record DepositAllPayload(boolean includeHotbar) implements CustomPacketPayload {

    public static final Type<DepositAllPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(BankVault.MOD_ID, "deposit_all"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DepositAllPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, DepositAllPayload::includeHotbar,
            DepositAllPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
