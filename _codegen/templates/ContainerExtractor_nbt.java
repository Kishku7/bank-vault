package com.kishku7.bankvault.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Pulls items out of container items recursively, depositing the contents and returning the emptied
 * shell as an item. Nested containers are flattened (depth-capped at 16).
 *
 * <p>1.20.x NBT rewrite of the 26.1.2 components version. Supported:
 * <ul>
 *   <li>Shulker boxes -- contents under {@code BlockEntityTag.Items}.</li>
 *   <li>Bundles -- contents under root-tag {@code Items} (not survival-obtainable in 1.20.x,
 *       but creative/command bundles still unload correctly).</li>
 * </ul>
 * Travelers' Backpack integration is DISABLED for the 1.20.x v1 (revisit on demand); TB packs
 * are treated as ordinary items.
 *
 * <p>Platform extensions (see {@link Extension}) let loader-specific code add support for items
 * whose contents live outside the stack NBT (e.g. Sophisticated Backpacks on Forge, which keeps
 * contents in world SavedData behind an item-handler capability). The extensions list is empty
 * unless a platform module registers one, so behavior is identical on loaders without extensions.
 */
public final class ContainerExtractor {

    public static final int MAX_DEPTH = 16;

    /**
     * Loader-specific container support, consulted IN ADDITION to the built-in shulker/bundle
     * logic. The contract mirrors the built-in path exactly as {@code BankVaultMenu.processUnload}
     * consumes it:
     * <ul>
     *   <li>{@link #peek} must be NON-mutating -- processUnload may still abort the unload after
     *       extractAll (vault-full room check) and expects the container left untouched.</li>
     *   <li>{@link #drain} is the mutating step, invoked only from {@link ContainerExtractor#emptied}
     *       after the deposit succeeded; it empties the stack in place so the same stack instance
     *       becomes the emptied shell parked in grab-me.</li>
     * </ul>
     * Implementations must never throw out of these methods (guard internally); the extractor
     * additionally shields itself against misbehaving extensions.
     */
    public interface Extension {
        /** Cheap structural test ("may go in the unload slot"). Must not mutate the stack. */
        boolean matches(ItemStack stack);

        /** True if the stack currently holds at least one item. Read-only. */
        boolean hasContents(ItemStack stack);

        /** Non-mutating snapshot of the contents (copies). Empty list / null when empty. */
        List<ItemStack> peek(ItemStack stack);

        /**
         * Extract everything, mutating the stack's storage so the stack itself becomes the
         * emptied shell. The returned list is informational (the extractor deposits the
         * {@link #peek} snapshot taken in the same server tick).
         */
        List<ItemStack> drain(ItemStack stack);
    }

    /** Registered platform extensions. Empty by default (e.g. on Fabric). */
    public static final List<Extension> EXTENSIONS = new CopyOnWriteArrayList<>();

    private ContainerExtractor() {}

    /** True if the stack carries extractable contents right now. */
    public static boolean isContainer(ItemStack stack) {
        Extension ext = extensionFor(stack);
        if (ext != null) {
            try {
                if (ext.hasContents(stack)) return true;
            } catch (Throwable t) {
                // a broken extension must never break the vault -- fall through to built-ins
            }
        }
        return contentsOf(stack) != null;
    }

    /** True if the stack is structurally a container (shulker/bundle/extension), even when empty. */
    public static boolean isContainerType(ItemStack stack) {
        if (extensionFor(stack) != null) return true;
        if (stack.getItem() instanceof BundleItem) return true;
        return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    /** First registered extension claiming this stack, or null. Never throws. */
    private static Extension extensionFor(ItemStack stack) {
        if (EXTENSIONS.isEmpty()) return null;
        for (Extension ext : EXTENSIONS) {
            try {
                if (ext.matches(stack)) return ext;
            } catch (Throwable t) {
                // ignore -- a broken extension must never break the vault
            }
        }
        return null;
    }

    /** Extension contents snapshot (copies, non-empty entries), or null if empty/none/failed. */
    private static List<ItemStack> peekExtension(Extension ext, ItemStack stack) {
        try {
            if (!ext.hasContents(stack)) return null;
            List<ItemStack> raw = ext.peek(stack);
            if (raw == null || raw.isEmpty()) return null;
            List<ItemStack> out = new ArrayList<>();
            for (ItemStack s : raw) {
                if (s != null && !s.isEmpty()) out.add(s);
            }
            return out.isEmpty() ? null : out;
        } catch (Throwable t) {
            return null;
        }
    }

    /** One level of contents, or null if the stack is not a (non-empty) container. */
    private static List<ItemStack> contentsOf(ItemStack stack) {
        ListTag items = itemListOf(stack);
        if (items == null || items.isEmpty()) return null;
        List<ItemStack> out = new ArrayList<>();
        for (Tag t : items) {
            if (!(t instanceof CompoundTag ct)) continue;
            ItemStack s = ItemStack.of(ct);
            if (!s.isEmpty()) out.add(s);
        }
        return out.isEmpty() ? null : out;
    }

    /** The NBT list holding this container's items, or null if not a container / no list. */
    private static ListTag itemListOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
            CompoundTag bet = BlockItem.getBlockEntityData(stack);
            if (bet != null && bet.contains("Items", Tag.TAG_LIST)) return bet.getList("Items", Tag.TAG_COMPOUND);
            return null;
        }
        if (stack.getItem() instanceof BundleItem) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("Items", Tag.TAG_LIST)) return tag.getList("Items", Tag.TAG_COMPOUND);
        }
        return null;
    }

    /** Flattened contents (nested containers recursed; emptied containers returned as items). */
    public static List<ItemStack> extractAll(ItemStack container) {
        return extractAll(container, 0);
    }

    private static List<ItemStack> extractAll(ItemStack container, int depth) {
        List<ItemStack> inner;
        Extension ext = extensionFor(container);
        if (ext != null) {
            // Non-mutating snapshot: processUnload may still abort (vault-full) and must be able
            // to leave the container untouched. The real drain happens later in emptied().
            inner = peekExtension(ext, container);
        } else {
            inner = contentsOf(container);
        }
        if (inner == null) return null;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : inner) {
            // Only built-in (NBT-carried) containers are recursed. Extension-matched items found
            // inside a container are deposited whole: their contents live outside the stack and
            // travel with it, and draining nested external storage during a peek would mutate.
            if (depth < MAX_DEPTH && contentsOf(item) != null) {
                List<ItemStack> sub = extractAll(item, depth + 1);
                if (sub != null) { out.addAll(sub); out.add(emptied(item)); }
                else out.add(item);
            } else {
                out.add(item);
            }
        }
        return out;
    }

    /** A copy of the container with its extractable contents cleared. */
    public static ItemStack emptied(ItemStack stack) {
        Extension ext = extensionFor(stack);
        if (ext != null) {
            // Mutating step: empties the external storage in place; the same stack instance is
            // the emptied shell (contents were already deposited from the extractAll snapshot).
            try {
                ext.drain(stack);
            } catch (Throwable t) {
                // never let a compat failure crash the vault; worst case the shell keeps items
            }
            return stack;
        }
        ItemStack e = stack.copy();
        if (e.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
            CompoundTag bet = BlockItem.getBlockEntityData(e);
            if (bet != null) {
                bet.remove("Items");
                // drop the whole BlockEntityTag when only the id remains (pristine empty shulker)
                if (bet.isEmpty() || (bet.size() == 1 && bet.contains("id"))) e.removeTagKey("BlockEntityTag");
            }
        } else if (e.getItem() instanceof BundleItem) {
            CompoundTag tag = e.getTag();
            if (tag != null) {
                tag.remove("Items");
                if (tag.isEmpty()) e.setTag(null);
            }
        }
        return e;
    }
}
