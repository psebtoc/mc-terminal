package com.pyosechang.terminal.client;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.TerminalMode;
import com.jediterm.terminal.model.JediTerminal;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pyosechang.terminal.terminal.TerminalSession;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.util.EnumSet;

public class TerminalRenderer {

    // JetBrains Mono TTF font (monospace, anti-aliased)
    public static final ResourceLocation TERMINAL_FONT = new ResourceLocation("terminal", "terminal");

    // Cell size calibrated from the TTF font
    public static int CELL_WIDTH = 6;
    public static int CELL_HEIGHT = 11;

    // Colors (Windows Terminal dark theme)
    public static final int DEFAULT_FG = 0xFFCCCCCC;
    public static final int DEFAULT_BG = 0xFF0C0C0C;
    private static final int CURSOR_COLOR = 0xFFCCCCCC;
    private static final int SELECTION_BG = 0x55FFFFFF;

    // ANSI 16-color palette
    private static final int[] ANSI_PALETTE = {
        0xFF0C0C0C, 0xFFC50F1F, 0xFF13A10E, 0xFFC19C00,
        0xFF0037DA, 0xFF881798, 0xFF3A96DD, 0xFFCCCCCC,
        0xFF767676, 0xFFE74856, 0xFF16C60C, 0xFFF9F1A5,
        0xFF3B78FF, 0xFFB4009E, 0xFF61D6D6, 0xFFF2F2F2,
    };

    public static void calibrateCellSize(Font font) {
        Component test = Component.literal("M").withStyle(Style.EMPTY.withFont(TERMINAL_FONT));
        CELL_WIDTH = font.width(test);
        if (CELL_WIDTH < 4) CELL_WIDTH = 6;
        CELL_HEIGHT = font.lineHeight + 1;
    }

    public static void render(GuiGraphics graphics, Font font, TerminalSession session,
                              int x, int y, int cols, int rows,
                              int selStartCol, int selStartRow, int selEndCol, int selEndRow,
                              boolean hasSelection) {
        if (session == null || session.getTextBuffer() == null) return;

        TerminalTextBuffer buffer = session.getTextBuffer();

        // Cursor: only render when terminal reports cursor as visible
        // TUI apps (Claude Code etc.) send \e[?25l to hide cursor
        int cursorCol = -1, cursorRow = -1;
        boolean cursorBlink = false;
        if (isCursorVisible(session.getTerminal())) {
            cursorCol = session.getTerminal().getCursorX() - 1;
            cursorRow = session.getTerminal().getCursorY() - 1;
            cursorBlink = ((System.currentTimeMillis()) % 1000) < 600;
        }

        int sR1, sC1, sR2, sC2;
        if (hasSelection) {
            if (selStartRow < selEndRow || (selStartRow == selEndRow && selStartCol <= selEndCol)) {
                sR1 = selStartRow; sC1 = selStartCol; sR2 = selEndRow; sC2 = selEndCol;
            } else {
                sR1 = selEndRow; sC1 = selEndCol; sR2 = selStartRow; sC2 = selStartCol;
            }
        } else {
            sR1 = sC1 = sR2 = sC2 = -1;
        }

        buffer.lock();
        try {
            for (int row = 0; row < rows; row++) {
                TerminalLine line = buffer.getLine(row);
                if (line == null) continue;

                String text = line.getText();
                int lineLen = line.length();
                int cellY = y + row * CELL_HEIGHT;

                int col = 0;
                while (col < cols) {
                    int cellX = x + col * CELL_WIDTH;
                    boolean isCursor = (row == cursorRow && col == cursorCol);
                    boolean isSelected = hasSelection && isInSelection(row, col, sR1, sC1, sR2, sC2);

                    char ch = (col < text.length()) ? text.charAt(col) : 0;

                    // Skip DWC padding (U+E000) - jediterm inserts this for the
                    // second cell of double-width characters
                    if (ch == 0xE000) {
                        col++;
                        continue;
                    }

                    boolean wide = isWideChar(ch);
                    int cellSpan = wide ? 2 : 1;
                    int cellW = cellSpan * CELL_WIDTH;

                    // Background
                    if (isSelected) {
                        graphics.fill(cellX, cellY, cellX + cellW, cellY + CELL_HEIGHT, SELECTION_BG);
                    } else if (col < lineLen) {
                        int bg = getBackgroundColor(line.getStyleAt(col));
                        if (bg != DEFAULT_BG) {
                            graphics.fill(cellX, cellY, cellX + cellW, cellY + CELL_HEIGHT, bg);
                        }
                    }

                    // Character - draw at cell position using TTF monospace font
                    // Skip HIDDEN text (SGR 8) - used by Claude Code to conceal markers
                    if (ch > 32) {
                        TextStyle style = col < lineLen ? line.getStyleAt(col) : null;
                        boolean hidden = style != null && style.hasOption(TextStyle.Option.HIDDEN);
                        if (!hidden) {
                            char renderCh = getFallbackChar(ch);
                            if (renderCh != 0) {
                                int fg = getForegroundColor(style);
                                Component charComp = Component.literal(String.valueOf(renderCh))
                                        .withStyle(Style.EMPTY.withFont(TERMINAL_FONT));
                                graphics.drawString(font, charComp, cellX, cellY + 1, fg, false);
                            }
                        }
                    }

                    // Thin line cursor (only when terminal reports cursor visible)
                    if (isCursor && cursorBlink) {
                        graphics.fill(cellX, cellY, cellX + 2, cellY + CELL_HEIGHT, CURSOR_COLOR);
                    }

                    col += cellSpan;
                }
            }
        } finally {
            buffer.unlock();
        }
    }

    private static boolean isWideChar(char ch) {
        // CJK ranges
        if ((ch >= 0x1100 && ch <= 0x115F) ||
               (ch >= 0x2E80 && ch <= 0x303E) ||
               (ch >= 0x3041 && ch <= 0x33BF) ||
               (ch >= 0x3400 && ch <= 0x4DBF) ||
               (ch >= 0x4E00 && ch <= 0x9FFF) ||
               (ch >= 0xAC00 && ch <= 0xD7AF) ||
               (ch >= 0xF900 && ch <= 0xFAFF) ||
               (ch >= 0xFE30 && ch <= 0xFE6F) ||
               (ch >= 0xFF01 && ch <= 0xFF60) ||
               (ch >= 0xFFE0 && ch <= 0xFFE6)) {
            return true;
        }
        // U+25CF (●) - Black Circle used by Claude Code as response marker.
        // CJK terminals treat this as 2-wide (ambiguous width), which hides
        // the single-character artifact that follows it in the stream.
        if (ch == 0x25CF) {
            return true;
        }
        return false;
    }

    /**
     * Returns the character to render. Only filters characters that
     * definitely cannot render (surrogates). Everything else is passed through -
     * the font + minecraft:default fallback handles rendering.
     */
    private static char getFallbackChar(char ch) {
        // Surrogate pairs (incomplete single chars): skip
        if (ch >= 0xD800 && ch <= 0xDFFF) return 0;
        return ch;
    }

    private static boolean isInSelection(int row, int col, int sR1, int sC1, int sR2, int sC2) {
        if (row < sR1 || row > sR2) return false;
        if (row == sR1 && row == sR2) return col >= sC1 && col < sC2;
        if (row == sR1) return col >= sC1;
        if (row == sR2) return col < sC2;
        return true;
    }

    private static int getForegroundColor(TextStyle style) {
        if (style == null) return DEFAULT_FG;
        TerminalColor fg = style.getForeground();
        if (fg == null) return DEFAULT_FG;
        return terminalColorToArgb(fg, DEFAULT_FG);
    }

    private static int getBackgroundColor(TextStyle style) {
        if (style == null) return DEFAULT_BG;
        TerminalColor bg = style.getBackground();
        if (bg == null) return DEFAULT_BG;
        return terminalColorToArgb(bg, DEFAULT_BG);
    }

    private static int terminalColorToArgb(TerminalColor termColor, int fallback) {
        if (termColor == null) return fallback;
        try {
            if (termColor.isIndexed()) {
                int idx = termColor.getColorIndex();
                if (idx >= 0 && idx < 16) return ANSI_PALETTE[idx];
                if (idx >= 16 && idx < 232) {
                    int ci = idx - 16;
                    return 0xFF000000 | ((ci/36)*51 << 16) | (((ci/6)%6)*51 << 8) | ((ci%6)*51);
                }
                if (idx >= 232 && idx < 256) {
                    int g = 8 + (idx - 232) * 10;
                    return 0xFF000000 | (g << 16) | (g << 8) | g;
                }
            }
        } catch (Exception ignored) {}
        try {
            Color c = termColor.toColor();
            if (c != null) return 0xFF000000 | (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
        } catch (Exception ignored) {}
        return fallback;
    }

    private static Field modesField;
    private static boolean modesFieldResolved;

    @SuppressWarnings("unchecked")
    private static boolean isCursorVisible(JediTerminal terminal) {
        if (terminal == null) return true;
        if (!modesFieldResolved) {
            modesFieldResolved = true;
            try {
                modesField = JediTerminal.class.getDeclaredField("myModes");
                modesField.setAccessible(true);
            } catch (Exception ignored) {}
        }
        if (modesField != null) {
            try {
                EnumSet<TerminalMode> modes = (EnumSet<TerminalMode>) modesField.get(terminal);
                return modes.contains(TerminalMode.CursorVisible);
            } catch (Exception ignored) {}
        }
        return true;
    }
}
