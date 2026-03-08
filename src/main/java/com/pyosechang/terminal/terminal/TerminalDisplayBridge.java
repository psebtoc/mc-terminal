package com.pyosechang.terminal.terminal;

import com.jediterm.terminal.CursorShape;
import com.jediterm.terminal.TerminalDisplay;
import com.jediterm.terminal.emulator.mouse.MouseFormat;
import com.jediterm.terminal.emulator.mouse.MouseMode;
import com.jediterm.terminal.model.TerminalSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal TerminalDisplay bridge. Actual rendering is done by TerminalScreen
 * reading TerminalTextBuffer directly.
 */
public class TerminalDisplayBridge implements TerminalDisplay {

    @Override
    public void setCursor(int x, int y) {
    }

    @Override
    public void setCursorShape(@NotNull CursorShape cursorShape) {
    }

    @Override
    public void beep() {
    }

    @Override
    public void scrollArea(int scrollRegionTop, int scrollRegionSize, int dy) {
    }

    @Override
    public void setCursorVisible(boolean visible) {
    }

    @Override
    public void useAlternateScreenBuffer(boolean enabled) {
    }

    @NotNull
    @Override
    public String getWindowTitle() {
        return "Terminal";
    }

    @Override
    public void setWindowTitle(@NotNull String title) {
    }

    @Nullable
    @Override
    public TerminalSelection getSelection() {
        return null;
    }

    @Override
    public void terminalMouseModeSet(@NotNull MouseMode mouseMode) {
    }

    @Override
    public void setMouseFormat(@NotNull MouseFormat mouseFormat) {
    }

    @Override
    public boolean ambiguousCharsAreDoubleWidth() {
        return false;
    }
}
