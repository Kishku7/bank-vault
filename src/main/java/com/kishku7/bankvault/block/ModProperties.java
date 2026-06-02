package com.kishku7.bankvault.block;

import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Shared blockstate properties so the form/unform manager can set them on the single vault block. */
public final class ModProperties {

    /** A vault block's role in a formed 3x3 (NONE = unformed plain steel). */
    public static final EnumProperty<VaultPart> PART = EnumProperty.create("part", VaultPart.class);

    private ModProperties() {}
}
