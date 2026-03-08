package com.pyosechang.terminal.client;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pyosechang.terminal.terminal.TerminalSession;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public class TerminalRenderer {

    // JetBrains Mono TTF font (monospace, anti-aliased)
    public static final ResourceLocation TERMINAL_FONT = new ResourceLocation("terminal", "terminal");

    // Cell size calibrated from the TTF font
    public static int CELL_WIDTH = 6;
    public static int CELL_HEIGHT = 11;

    // Colors (Windows Terminal dark theme)
    public static final int DEFAULT_FG = 0xFFCCCCCC;
    public static final int DEFAULT_BG = 0xFF0C0C0C;
    private static final int CURSOR_COLOR = 0xAABBBBBB;
    private static final int CURSOR_TEXT = 0xFF0C0C0C;
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
        int cursorCol = session.getTerminal().getCursorX() - 1;
        int cursorRow = session.getTerminal().getCursorY() - 1;
        boolean cursorBlink = ((System.currentTimeMillis()) % 1000) < 600;

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
                    boolean wide = isWideChar(ch);
                    int cellSpan = wide ? 2 : 1;
                    int cellW = cellSpan * CELL_WIDTH;

                    // Background
                    if (isSelected) {
                        graphics.fill(cellX, cellY, cellX + cellW, cellY + CELL_HEIGHT, SELECTION_BG);
                    } else if (isCursor && cursorBlink) {
                        graphics.fill(cellX, cellY, cellX + cellW, cellY + CELL_HEIGHT, CURSOR_COLOR);
                    } else if (col < lineLen) {
                        int bg = getBackgroundColor(line.getStyleAt(col));
                        if (bg != DEFAULT_BG) {
                            graphics.fill(cellX, cellY, cellX + cellW, cellY + CELL_HEIGHT, bg);
                        }
                    }

                    // Character - draw at cell position using TTF monospace font
                    if (ch > 32) {
                        int fg = (isCursor && cursorBlink) ? CURSOR_TEXT
                                : getForegroundColor(col < lineLen ? line.getStyleAt(col) : null);
                        Component charComp = Component.literal(String.valueOf(ch))
                                .withStyle(Style.EMPTY.withFont(TERMINAL_FONT));
                        graphics.drawString(font, charComp, cellX, cellY + 1, fg, false);
                    }

                    col += cellSpan;
                }
            }
        } finally {
            buffer.unlock();
        }
    }

    private static boolean isWideChar(char ch) {
        return (ch >= 0x1100 && ch <= 0x115F) ||
               (ch >= 0x2E80 && ch <= 0x303E) ||
               (ch >= 0x3041 && ch <= 0x33BF) ||
               (ch >= 0x3400 && ch <= 0x4DBF) ||
               (ch >= 0x4E00 && ch <= 0x9FFF) ||
               (ch >= 0xAC00 && ch <= 0xD7AF) ||
               (ch >= 0xF900 && ch <= 0xFAFF) ||
               (ch >= 0xFE30 && ch <= 0xFE6F) ||
               (ch >= 0xFF01 && ch <= 0xFF60) ||
               (ch >= 0xFFE0 && ch <= 0xFFE6);
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
}
