package com.kishku7.bankvault.client;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Input shim: same accessor surface every era; wraps the 1.21.9+ input records here. */
public final class Ev {

    private final Object raw;

    private Ev(Object raw) { this.raw = raw; }

    public static Ev key(KeyEvent e) { return new Ev(e); }

    public static Ev chr(CharacterEvent e) { return new Ev(e); }

    public static Ev mouse(MouseButtonEvent e) { return new Ev(e); }

    public Object raw() { return raw; }

    public int input() { return ((KeyEvent) raw).input(); }

    public boolean isEscape() { return ((KeyEvent) raw).isEscape(); }

    public boolean isConfirmation() { return ((KeyEvent) raw).isConfirmation(); }

    public boolean isAllowedChatCharacter() { return ((CharacterEvent) raw).isAllowedChatCharacter(); }

    public String codepointAsString() { return ((CharacterEvent) raw).codepointAsString(); }

    public double x() { return ((MouseButtonEvent) raw).x(); }

    public double y() { return ((MouseButtonEvent) raw).y(); }

    public int button() { return ((MouseButtonEvent) raw).button(); }

    public boolean hasShiftDown() { return ((MouseButtonEvent) raw).hasShiftDown(); }
}
