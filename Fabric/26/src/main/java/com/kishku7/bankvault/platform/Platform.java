package com.kishku7.bankvault.platform;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/** Loader seam: same package+class on every loader; shared code compiles against whichever copy is present. */
public final class Platform {
    private Platform() {}

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static boolean isLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
