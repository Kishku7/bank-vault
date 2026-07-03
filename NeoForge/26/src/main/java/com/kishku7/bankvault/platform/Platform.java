package com.kishku7.bankvault.platform;

import java.nio.file.Path;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Loader seam: same package+class on every loader; shared code compiles against whichever copy is present. */
public final class Platform {
    private Platform() {}

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }
}
