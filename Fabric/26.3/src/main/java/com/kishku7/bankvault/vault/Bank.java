package com.kishku7.bankvault.vault;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A bank belonging to a group. Plain items are stored by id+count; NBT-bearing stacks separately. */
public class Bank {

    public String bankId;
    public long createdAt;
    public List<Member> members = new ArrayList<>();
    /** Plain items (no custom components): key = item id, value = count. */
    public Map<String, Long> items = new LinkedHashMap<>();
    /** NBT/component-bearing stacks: key = "id#hash", value = serialized prototype + count. */
    public Map<String, Special> special = new LinkedHashMap<>();
    public int upgradeCount = 0;

    public static class Member {
        public String uuid; public String name; public int level; public long joinedAt;
        public Member() {}
        public Member(String uuid, String name, int level, long joinedAt) {
            this.uuid = uuid; this.name = name; this.level = level; this.joinedAt = joinedAt;
        }
    }

    /** A unique NBT stack prototype (count 1, serialized via ItemStack codec) + how many are stored. */
    public static class Special {
        public JsonElement stack;
        public long count;
        public Special() {}
        public Special(JsonElement stack, long count) { this.stack = stack; this.count = count; }
    }

    public long totalItems() {
        long t = 0;
        for (long v : items.values()) t += v;
        for (Special s : special.values()) t += s.count;
        return t;
    }

    public int uniqueItems() { return items.size() + special.size(); }

    public Member member(UUID u) {
        String s = u.toString();
        for (Member m : members) if (s.equals(m.uuid)) return m;
        return null;
    }

    public int levelOf(UUID u) { Member m = member(u); return m == null ? 0 : m.level; }
}
