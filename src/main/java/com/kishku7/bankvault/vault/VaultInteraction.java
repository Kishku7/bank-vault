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

/**
 * Opens the vault screen. The structure is a shared access point (Ender-Chest model): any formed
 * vault opens the clicking player's own bank (or their group bank via the index) -- there is no
 * per-structure ownership, so a whole server can share one 3x3. Refuses only if incomplete.
 */
public final class VaultInteraction {

    private VaultInteraction() {}

    public static void onUse(ServerPlayer player, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BankVaultBlockEntity)
                || level.getBlockState(pos).getValue(ModProperties.PART) == VaultPart.NONE) {
            player.sendSystemMessage(Component.literal(
                    "\u00A76[Bank Vault]\u00A7r \u00A7cVault incomplete\u00A7r \u2014 complete the 3\u00D73 to use it."));
            return;
        }
        Bank bank = BankManager.getOrCreate(player);
        // Open the container menu (real inventory slots), then push the vault grid data.
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new BankVaultMenu(id, inv),
                Component.literal("Bank Vault")));
        ModNetworking.sendSync(player, bank);
    }
}
