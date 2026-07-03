package com.kishku7.bankvault.inventory;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls items out of container items recursively, depositing the contents and returning the emptied
 * shell as an item. Nested containers are flattened (depth-capped at 16).
 *
 * <p>Supported, in order of how their contents are stored:
 * <ul>
 *   <li>Shulker boxes — vanilla {@code DataComponents.CONTAINER}.</li>
 *   <li>Bundles — vanilla {@code DataComponents.BUNDLE_CONTENTS}.</li>
 *   <li>Traveler's Backpack — its {@code travelersbackpack:backpack_container} component, which is
 *       itself an {@link ItemContainerContents}. Resolved by registry ID at runtime so there is no
 *       compile-time dependency on TB; if TB is not installed the lookup yields nothing and TB
 *       backpacks are simply treated as ordinary (non-container) items. Only the main cargo is
 *       extracted — the backpack's tool slots, upgrade modules, and fluid tanks stay with the
 *       returned shell.</li>
 * </ul>
 */
public final class ContainerExtractor {

    public static final int MAX_DEPTH = 16;

    /** TB container components, resolved by ID once. Empty array when TB is absent. */
    private static final DataComponentType<?>[] SOFT_CONTAINERS = resolveSoftContainers();

    private ContainerExtractor() {}

    private static DataComponentType<?>[] resolveSoftContainers() {
        if (!ModList.get().isLoaded("travelersbackpack")) return new DataComponentType<?>[0];
        List<DataComponentType<?>> found = new ArrayList<>();
        // Only the main cargo container — leave tools/upgrades/fluids on the backpack.
        DataComponentType<?> t = BuiltInRegistries.DATA_COMPONENT_TYPE
                .getValue(Identifier.fromNamespaceAndPath("travelersbackpack", "backpack_container"));
        if (t != null) found.add(t);
        return found.toArray(new DataComponentType<?>[0]);
    }

    /** True if the stack carries extractable contents right now. */
    public static boolean isContainer(ItemStack stack) {
        return contentsOf(stack) != null;
    }

    /** True if the stack is structurally a container (shulker/bundle/TB), even when empty.
     *  Components only exist once something was stored, so empties are matched by item identity. */
    public static boolean isContainerType(ItemStack stack) {
        if (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS)) return true;
        for (DataComponentType<?> type : SOFT_CONTAINERS) if (stack.has(type)) return true;
        if (stack.getItem() instanceof BundleItem) return true;
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) return true;
        // TB backpacks are namespaced by variant (travelersbackpack:standard, :diamond, ...)
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals("travelersbackpack");
    }

    /** One level of contents, or null if the stack is not a (non-empty) container. */
    private static List<ItemStack> contentsOf(ItemStack stack) {
        List<ItemStack> out = new ArrayList<>();
        if (stack.has(DataComponents.CONTAINER))
            stack.get(DataComponents.CONTAINER).nonEmptyItemCopyStream().forEach(out::add);
        if (stack.has(DataComponents.BUNDLE_CONTENTS)) {
            BundleContents bc = stack.get(DataComponents.BUNDLE_CONTENTS);
            if (bc != null && !bc.isEmpty()) com.kishku7.bankvault.BvCompat.itemCopies(bc).forEach(out::add);
        }
        for (DataComponentType<?> type : SOFT_CONTAINERS) {
            if (stack.has(type) && stack.get(type) instanceof ItemContainerContents icc)
                icc.nonEmptyItemCopyStream().forEach(out::add);
        }
        return out.isEmpty() ? null : out;
    }

    /** Flattened contents (nested containers recursed; emptied containers returned as items). */
    public static List<ItemStack> extractAll(ItemStack container) {
        return extractAll(container, 0);
    }

    private static List<ItemStack> extractAll(ItemStack container, int depth) {
        List<ItemStack> inner = contentsOf(container);
        if (inner == null) return null;
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack item : inner) {
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
        ItemStack e = stack.copy();
        if (e.has(DataComponents.CONTAINER)) e.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (e.has(DataComponents.BUNDLE_CONTENTS)) e.set(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        for (DataComponentType<?> type : SOFT_CONTAINERS) if (e.has(type)) clear(e, type);
        return e;
    }

    @SuppressWarnings("unchecked")
    private static void clear(ItemStack stack, DataComponentType<?> type) {
        stack.set((DataComponentType<ItemContainerContents>) type, ItemContainerContents.EMPTY);
    }
}
