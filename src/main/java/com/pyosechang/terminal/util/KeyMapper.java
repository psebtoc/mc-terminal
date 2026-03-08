package com.pyosechang.terminal.util;

import org.lwjgl.glfw.GLFW;

/**
 * Maps GLFW key codes to terminal byte sequences.
 */
public class KeyMapper {

    /**
     * Convert a GLFW key press to the byte sequence a terminal expects.
     * Returns null if the key should be ignored (handled via charTyped instead).
     */
    public static byte[] keyToBytes(int keyCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        // Ctrl+letter combinations
        if (ctrl && keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return new byte[]{(byte) (keyCode - GLFW.GLFW_KEY_A + 1)};
        }

        // Special keys to VT100 escape sequences
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER:
            case GLFW.GLFW_KEY_KP_ENTER:
                return new byte[]{'\r'};
            case GLFW.GLFW_KEY_BACKSPACE:
                return new byte[]{0x7f};
            case GLFW.GLFW_KEY_TAB:
                if (shift) return new byte[]{0x1b, '[', 'Z'}; // reverse tab
                return new byte[]{'\t'};
            case GLFW.GLFW_KEY_ESCAPE:
                return new byte[]{0x1b};
            case GLFW.GLFW_KEY_UP:
                return new byte[]{0x1b, '[', 'A'};
            case GLFW.GLFW_KEY_DOWN:
                return new byte[]{0x1b, '[', 'B'};
            case GLFW.GLFW_KEY_RIGHT:
                return new byte[]{0x1b, '[', 'C'};
            case GLFW.GLFW_KEY_LEFT:
                return new byte[]{0x1b, '[', 'D'};
            case GLFW.GLFW_KEY_HOME:
                return new byte[]{0x1b, '[', 'H'};
            case GLFW.GLFW_KEY_END:
                return new byte[]{0x1b, '[', 'F'};
            case GLFW.GLFW_KEY_INSERT:
                return new byte[]{0x1b, '[', '2', '~'};
            case GLFW.GLFW_KEY_DELETE:
                return new byte[]{0x1b, '[', '3', '~'};
            case GLFW.GLFW_KEY_PAGE_UP:
                return new byte[]{0x1b, '[', '5', '~'};
            case GLFW.GLFW_KEY_PAGE_DOWN:
                return new byte[]{0x1b, '[', '6', '~'};
            case GLFW.GLFW_KEY_F1:
                return new byte[]{0x1b, 'O', 'P'};
            case GLFW.GLFW_KEY_F2:
                return new byte[]{0x1b, 'O', 'Q'};
            case GLFW.GLFW_KEY_F3:
                return new byte[]{0x1b, 'O', 'R'};
            case GLFW.GLFW_KEY_F4:
                return new byte[]{0x1b, 'O', 'S'};
            case GLFW.GLFW_KEY_F5:
                return new byte[]{0x1b, '[', '1', '5', '~'};
            case GLFW.GLFW_KEY_F6:
                return new byte[]{0x1b, '[', '1', '7', '~'};
            case GLFW.GLFW_KEY_F7:
                return new byte[]{0x1b, '[', '1', '8', '~'};
            case GLFW.GLFW_KEY_F8:
                return new byte[]{0x1b, '[', '1', '9', '~'};
            case GLFW.GLFW_KEY_F9:
                return new byte[]{0x1b, '[', '2', '0', '~'};
            case GLFW.GLFW_KEY_F10:
                return new byte[]{0x1b, '[', '2', '1', '~'};
            case GLFW.GLFW_KEY_F11:
                return new byte[]{0x1b, '[', '2', '3', '~'};
            // F12 is our toggle key, don't send to terminal
            default:
                return null;
        }
    }
}
