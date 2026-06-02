package com.kishku7.bankvault.vault;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Helpers for keying and serializing item stacks (so NBT-bearing items keep their identity). */
public final class StackStore {

    private StackStore() {}

    private static DynamicOps<JsonElement> ops(RegistryAccess ra) {
        return ra.createSerializationContext(JsonOps.INSTANCE);
    }

    /** True if the stack has no custom components (a plain item that can be keyed by id alone). */
    public static boolean isPlain(ItemStack stack) {
        return stack.getComponentsPatch().isEmpty();
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Serialize a single-item prototype to JSON (null on failure). */
    public static JsonElement encode(ItemStack stack, RegistryAccess ra) {
        return ItemStack.CODEC.encodeStart(ops(ra), stack.copyWithCount(1)).result().orElse(null);
    }

    public static ItemStack decode(JsonElement json, RegistryAccess ra) {
        if (json == null) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(ops(ra), json).result().orElse(ItemStack.EMPTY);
    }

    /** Stable key for an NBT stack: "id#hash" of its serialized form. */
    public static String specialKey(ItemStack stack, JsonElement json) {
        return idOf(stack) + "#" + Integer.toHexString(json.toString().hashCode());
    }

    public static Identifier parseId(String key) {
        String id = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
        String ns = "minecraft", path = id;
        int i = id.indexOf(':');
        if (i >= 0) { ns = id.substring(0, i); path = id.substring(i + 1); }
        try { return Identifier.fromNamespaceAndPath(ns, path); } catch (Exception e) { return null; }
    }
}
