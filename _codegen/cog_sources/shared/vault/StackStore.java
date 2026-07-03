package com.kishku7.bankvault.vault;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        /* [[[cog
        import compat_core
        compat_core.emit_stackstore_ops(cog, ver)
        ]]] */
        return ra.createSerializationContext(JsonOps.INSTANCE);
        /* [[[end]]] */
    }

    /** True if the stack has no custom components (a plain item that can be keyed by id alone). */
    public static boolean isPlain(ItemStack stack) {
        /* [[[cog
        import compat_core
        compat_core.emit_stackstore_isplain(cog, ver)
        ]]] */
        return stack.getComponentsPatch().isEmpty();
        /* [[[end]]] */
    }

    public static String idOf(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Serialize a single-item prototype to canonical JSON (null on failure). */
    public static JsonElement encode(ItemStack stack, RegistryAccess ra) {
        JsonElement el = ItemStack.CODEC.encodeStart(ops(ra), stack.copyWithCount(1)).result().orElse(null);
        return el == null ? null : canonical(el);
    }

    /** Recursively key-sorted copy so identical stacks always serialize identically.
     *  Object keys are sorted; array ORDER is preserved (it is semantic). */
    public static JsonElement canonical(JsonElement el) {
        if (el == null || el.isJsonNull()) return el;
        if (el.isJsonObject()) {
            JsonObject in = el.getAsJsonObject();
            java.util.List<String> keys = new java.util.ArrayList<>(in.keySet());
            java.util.Collections.sort(keys);
            JsonObject out = new JsonObject();
            for (String k : keys) out.add(k, canonical(in.get(k)));
            return out;
        }
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : el.getAsJsonArray()) out.add(canonical(e));
            return out;
        }
        return el;
    }

    public static ItemStack decode(JsonElement json, RegistryAccess ra) {
        if (json == null) return ItemStack.EMPTY;
        return ItemStack.CODEC.parse(ops(ra), json).result().orElse(ItemStack.EMPTY);
    }

    /** Stable key for an NBT stack: "id#hash" of its canonical serialized form (SHA-256/64-bit).
     *  Callers must STILL verify stored JSON equals the new JSON before merging counts
     *  (BankManager.depositStack probes "key~n" on mismatch) -- the key alone is not proof. */
    public static String specialKey(ItemStack stack, JsonElement json) {
        return idOf(stack) + "#" + sha16(json.toString());
    }

    private static String sha16(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(s.hashCode()); // unreachable: SHA-256 is mandatory in the JRE
        }
    }

    public static Identifier parseId(String key) {
        String id = key.contains("#") ? key.substring(0, key.indexOf('#')) : key;
        String ns = "minecraft", path = id;
        int i = id.indexOf(':');
        if (i >= 0) { ns = id.substring(0, i); path = id.substring(i + 1); }
        try { return Identifier.fromNamespaceAndPath(ns, path); } catch (Exception e) { return null; }
    }
}
