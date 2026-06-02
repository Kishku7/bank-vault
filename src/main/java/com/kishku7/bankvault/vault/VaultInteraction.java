package com.kishku7.bankvault.vault;

import com.kishku7.bankvault.block.ModProperties;
import com.kishku7.bankvault.block.VaultPart;
import com.kishku7.bankvault.entity.BankVaultBlockEntity;
import com.kishku7.bankvault.inventory.BankVaultMenu;
import com.kishku7.bankvault.net.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.UUID;

/** Opens the vault screen for members; refuses if the structure is incomplete or the player isn't a member. */
public final class VaultInteraction {

    private VaultInteraction() {}

    public static void onUse(ServerPlayer player, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BankVaultBlockEntity bv)
                || level.getBlockState(pos).getValue(ModProperties.PART) == VaultPart.NONE) {
            player.sendSystemMessage(Component.literal(
                    "§6[Bank Vault]§r §cVault incomplete§r — complete the 3×3 to use it."));
            return;
        }
        UUID builder = bv.getBuilderUUID();
        Bank bank = builder == null ? null : BankManager.lookup(builder);
        if (bank == null) {
            player.sendSystemMessage(Component.literal("§6[Bank Vault]§r §cThis vault isn't initialized.§r"));
            return;
        }
        if (bank.levelOf(player.getUUID()) <= 0) {
            player.sendSystemMessage(Component.literal("§6[Bank Vault]§r §cYou're not a member of this vault.§r"));
            return;
        }
        // Open the container menu (real inventory slots), then push the vault grid data.
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new BankVaultMenu(id, inv),
                Component.literal("Bank Vault")));
        ModNetworking.sendSync(player, bank);
    }
}
