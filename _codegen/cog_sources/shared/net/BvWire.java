package com.kishku7.bankvault.net;

import com.kishku7.bankvault.vault.Bank;

import java.util.ArrayList;
import java.util.List;

/**
 * The SINGLE untrusted-input gate for everything Bank Vault reads off the network.
 *
 * <p>Doctrine D19/D20/D22. A joined player is AUTHENTICATED but not TRUSTED: Mojang auth proves who
 * they are, not that their client is honest, unmodified, or even a client. Every inbound field passes
 * two stages, in this order:
 *
 * <ol>
 *   <li><b>CLEAN</b> -- reject or clamp on count, length and range BEFORE anything is allocated,
 *       looped over, indexed or persisted. The ceilings below are THIS SERVER'S POLICY; they are
 *       deliberately not part of the wire format, and they are generous enough that an honest client
 *       never reaches one.</li>
 *   <li><b>VERIFY</b> -- re-derive the truth from real server state and check the cleaned value
 *       against it. In range is not the same as legitimate: a bounded slot index is not necessarily a
 *       slot this action may touch, and a well-formed bank key is not necessarily stock this bank
 *       holds. Handlers then act on the SERVER's number, never on the client's.</li>
 * </ol>
 *
 * <p>This class is the only place either stage is implemented. Every loader's {@code ModNetworking}
 * routes through it -- per-loader handler copies drift (D14/D20), so nothing here gets re-written per
 * cell. It is MC-API-free on purpose: it compiles unchanged on every cell from 1.20 to 26.x.
 */
public final class BvWire {

    private BvWire() {}

    // ---- stage 1: CLEAN -- server-policy ceilings -------------------------------------------

    /** Visible grid cells the client may describe at once. BankVaultMenu.VIEW_SIZE is far below this. */
    public static final int MAX_GRID_KEYS = 512;
    /** Distinct stacks one vault_sync may carry (bank uniques; the vanilla catalog is ~3.5k items). */
    public static final int MAX_SYNC_ENTRIES = 65536;
    /** Members one sharing_state may carry. */
    public static final int MAX_MEMBERS = 256;
    /** Pending invites one sharing_state may carry. */
    public static final int MAX_INVITES = 256;
    /** Distinct per-tab sort memories one player may accumulate (each one rewrites their bucket). */
    public static final int MAX_SORT_KEYS = 128;
    /** Pins per tab -- matches the cap UserSettings.togglePin already enforced. */
    public static final int MAX_PINS = 54;
    /** A bank key: "namespace:path" or "namespace:path#&lt;64 hex&gt;", with slack. */
    public static final int MAX_KEY_LEN = 320;
    /** Tab / sort / sections tokens. */
    public static final int MAX_TOKEN_LEN = 80;
    /** Items one withdraw request may move: 4096 full stacks. A 20M-item vault takes many requests. */
    public static final int MAX_WITHDRAW = 262144;
    /** Highest player-inventory index this mod may ever touch: 0-8 hotbar, 9-35 main rows. */
    public static final int PLAYER_SLOT_MAX = 35;

    /**
     * Decode-time count gate. A declared element count that is negative or over the server-policy
     * ceiling is a malformed packet: reject it BEFORE a single element is read or allocated, because
     * a collection presized from a hostile count is a one-packet OOM (and a negative one throws from
     * inside a tick task). Throwing DecoderException is how vanilla reports a bad payload; the
     * network layer drops the connection rather than the server.
     */
    public static int count(int declared, int cap) {
        if (declared < 0 || declared > cap) {
            throw new io.netty.handler.codec.DecoderException(
                    "Bank Vault rejected a packet declaring " + declared + " elements (limit " + cap + ")");
        }
        return declared;
    }

    /** Sanitize an identifier-shaped token (tab id, sort mode, flag). Anything odd becomes empty. */
    public static String token(String raw) {
        if (raw == null || raw.isEmpty() || raw.length() > MAX_TOKEN_LEN) return "";
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || c == ':';
            if (!ok) return "";
        }
        return raw;
    }

    // ---- stage 2: VERIFY -- check the cleaned value against real server state ----------------

    /**
     * Is this an inventory slot the vault is allowed to reach? Bounds alone are NOT enough: the
     * container is 41 slots, so a merely in-bounds index still reaches armor (36-39) and offhand
     * (40), which the v1.1 spec calls untouchable. The bulk path enforces this by construction with
     * a literal 0..35 loop; the single-slot path has to be told.
     */
    public static boolean isPlayerStorageSlot(int slot) {
        return slot >= 0 && slot <= PLAYER_SLOT_MAX;
    }

    /** How many of {@code key} this bank ACTUALLY holds -- plain bucket or NBT-exact special. */
    public static long stockOf(Bank bank, String key) {
        if (bank == null || key == null || key.isEmpty()) return 0L;
        Long plain = bank.items.get(key);
        if (plain != null) return plain;
        Bank.Special sp = bank.special.get(key);
        return sp == null ? 0L : sp.count;
    }

    /** Does this bank hold any of {@code key}? */
    public static boolean bankHasKey(Bank bank, String key) {
        return stockOf(bank, key) > 0L;
    }

    /**
     * Clean AND verify a withdraw quantity: at least 1, never more than one request may move, and
     * never more than the bank actually has. The availability term is the stage-2 check -- the
     * server decides how much exists, so "withdraw 2 billion" cannot turn into hundreds of thousands
     * of dropped entities on the tick thread.
     */
    public static int withdrawAmount(int raw, long available) {
        if (available <= 0L) return 0;
        long want = raw < 1 ? 1L : raw;
        if (want > MAX_WITHDRAW) want = MAX_WITHDRAW;
        if (want > available) want = available;
        return (int) want;
    }

    /**
     * Clean AND verify the grid map. The client sends "this bank key is in this cell" so a real-Slot
     * click maps back to stored stock -- but that makes it a withdraw handle, so the server must
     * never learn a key from it. Oversized lists are truncated, and every cell is checked against
     * stock the bank really holds; anything unknown, malformed or over-long becomes an EMPTY cell.
     */
    public static List<String> verifiedGridKeys(List<String> raw, Bank bank) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        int n = Math.min(raw.size(), MAX_GRID_KEYS);
        for (int i = 0; i < n; i++) {
            String k = raw.get(i);
            boolean good = k != null && !k.isEmpty() && k.length() <= MAX_KEY_LEN && bankHasKey(bank, k);
            out.add(good ? k : "");
        }
        return out;
    }

    /** Room left for another distinct per-tab sort memory (each write rewrites the player's bucket). */
    public static boolean sortMemoryHasRoom(int currentSize) {
        return currentSize < MAX_SORT_KEYS;
    }
}
