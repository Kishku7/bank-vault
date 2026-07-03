package com.kishku7.bankvault;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.Stream;

/** Cross-26.x fault-line bridges so one source compiles + runs on 26.1 -> 26.3. */
public final class BvCompat {
    private BvCompat() {}

    // 26.1: Minecraft.setScreen(Screen). 26.2+: Minecraft.setScreenAndShow(Screen).
    public static void setScreen(Minecraft mc, Screen screen) {
        /* [[[cog
        import compat_core
        compat_core.emit_bvcompat_setscreen(cog, ver)
        ]]] */
        if (invoke1(mc, "setScreenAndShow", Screen.class, screen)) return;
        invoke1(mc, "setScreen", Screen.class, screen);
        /* [[[end]]] */
    }

    // 26.1: Minecraft.screen (field). 26.2+: Minecraft.gui.screen().
    public static Screen currentScreen(Minecraft mc) {
        /* [[[cog
        import compat_core
        compat_core.emit_bvcompat_currentscreen(cog, ver)
        ]]] */
        try {
            Field guiF = Minecraft.class.getField("gui");
            Object gui = guiF.get(mc);
            Method m = gui.getClass().getMethod("screen");
            return (Screen) m.invoke(gui);
        } catch (Exception ignored) {}
        try {
            Field f = Minecraft.class.getField("screen");
            return (Screen) f.get(mc);
        } catch (Exception e) {
            return null;
        }
        /* [[[end]]] */
    }

    // 26.1/26.2: bundle.itemCopyStream(). 26.3: bundle.itemCopies(). Both return Stream<ItemStack>.
    @SuppressWarnings("unchecked")
    public static Stream<net.minecraft.world.item.ItemStack> itemCopies(Object bundleContents) {
        /* [[[cog
        import compat_core
        compat_core.emit_bvcompat_itemcopies(cog, ver)
        ]]] */
        for (String name : new String[]{"itemCopies", "itemCopyStream"}) {
            try {
                Method m = bundleContents.getClass().getMethod(name);
                return (Stream<net.minecraft.world.item.ItemStack>) m.invoke(bundleContents);
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("BvCompat.itemCopies: no itemCopies/itemCopyStream on " + bundleContents.getClass());
        /* [[[end]]] */
    }
    /* [[[cog
    import compat_core
    compat_core.emit_bvcompat_invoke1(cog, ver)
    ]]] */

    private static boolean invoke1(Object target, String method, Class<?> paramType, Object arg) {
        try {
            Method m = target.getClass().getMethod(method, paramType);
            m.invoke(target, arg);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException("BvCompat." + method + " failed", e);
        }
    }
    /* [[[end]]] */
}
