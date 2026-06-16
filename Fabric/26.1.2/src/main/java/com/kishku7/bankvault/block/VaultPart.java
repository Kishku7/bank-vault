package com.kishku7.bankvault.block;

import net.minecraft.util.StringRepresentable;

/** A vault block's role + front-face UV rotation within a formed 3x3. NONE = unformed (plain steel). */
public enum VaultPart implements StringRepresentable {
    NONE("none"),
    CENTER("center"),
    CORNER0("corner0"), CORNER90("corner90"), CORNER180("corner180"), CORNER270("corner270"),
    EDGE0("edge0"), EDGE90("edge90"), EDGE180("edge180"), EDGE270("edge270");

    private final String name;
    VaultPart(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
