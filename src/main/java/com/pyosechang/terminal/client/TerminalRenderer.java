package com.pyosechang.terminal.client;

import com.jediterm.core.Color;
import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.model.TerminalLine;
import com.jediterm.terminal.model.TerminalTextBuffer;
import com.pyosechang.terminal.terminal.TerminalSession;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public class TerminalRenderer {

    public static final int CHAR_WIDTH = 6;
    public static final int CHAR_HEIGHT = 10;

    private static final int DEFAULT_FG = 0xFFE5E5E5;
    private static final int DEFAULT_BG = 0xF01E1E1E;

    public static void render(GuiGraphics graphics, Font font, TerminalSession session,
                              int x, int y, int visibleColumns, int visibleRows) {
        if (session == null || session.getTextBuffer() == null) return;

        TerminalTextBuffer buffer = session.getTextBuffer();

        int totalWidth = visibleColumns * CHAR_WIDTH;
        int totalHeight = visibleRows * CHAR_HEIGHT;

        // Background
        graphics.fill(x - 4, y - 4, x + totalWidth + 4, y + totalHeight + 4, DEFAULT_BG);

        // Border
        int borderColor = 0xFF555555;
        graphics.fill(x - 4, y - 4, x + totalWidth + 4, y - 3, borderColor);
        graphics.fill(x - 4, y + totalHeight + 3, x + totalWidth + 4, y + totalHeight + 4, borderColor);
        graphics.fill(x - 4, y - 4, x - 3, y + totalHeight + 4, borderColor);
        graphics.fill(x + totalWidth + 3, y - 4, x + totalWidth + 4, y + totalHeight + 4, borderColor);

        // Cursor
        int cursorCol = session.getTerminal().getCursorX() - 1;
        int cursorRow = session.getTerminal().getCursorY() - 1;
        boolean cursorVisible = ((System.currentTimeMillis()) % 1000) < 600;

        buffer.lock();
        try {
            for (int row = 0; row < visibleRows; row++) {
                TerminalLine line = buffer.getLine(row);
                if (line == null) continue;

                int lineLen = line.length();
                int cellY = y + row * CHAR_HEIGHT;

                // Pass 1: Draw cell backgrounds and cursor using font.width() positions
                String fullText = lineLen > 0 ? line.getText() : "";
                if (fullText.length() > visibleColumns) {
                    fullText = fullText.substring(0, visibleColumns);
                }

                for (int col = 0; col < visibleColumns; col++) {
                    // Calculate pixel X from actual text width
                    int cellX = x + (col < fullText.length()
                            ? font.width(fullText.substring(0, col))
                            : font.width(fullText) + (col - fullText.length()) * CHAR_WIDTH);
                    int nextCellX = x + (col + 1 <= fullText.length()
                            ? font.width(fullText.substring(0, col + 1))
                            : font.width(fullText) + (col + 1 - fullText.length()) * CHAR_WIDTH);
                    int cellW = nextCellX - cellX;

                    // Cell background
                    if (col < lineLen) {
                        TextStyle style = line.getStyleAt(col);
                        int bgColor = getBackgroundColor(style);
                        if (bgColor != DEFAULT_BG) {
                            graphics.fill(cellX, cellY, cellX + cellW, cellY + CHAR_HEIGHT, bgColor);
                        }
                    }

                    // Cursor block
                    if (row == cursorRow && col == cursorCol && cursorVisible) {
                        graphics.fill(cellX, cellY, cellX + cellW, cellY + CHAR_HEIGHT, 0xCCFFFFFF);
                    }
                }

                // Pass 2: Draw text per style run
                String lineText = fullText;

                if (!lineText.isEmpty()) {
                    // Find style runs and render each run
                    int runStart = 0;
                    TextStyle currentStyle = lineLen > 0 ? line.getStyleAt(0) : null;

                    for (int col = 1; col <= lineText.length(); col++) {
                        TextStyle style = col < lineLen ? line.getStyleAt(col) : null;
                        boolean styleChanged = !stylesEqual(currentStyle, style);

                        if (styleChanged || col == lineText.length()) {
                            int runEnd = col;
                            String runText = lineText.substring(runStart, runEnd);
                            int fgColor = getForegroundColor(currentStyle);

                            // Calculate x position based on text width of preceding chars
                            String preceding = lineText.substring(0, runStart);
                            int textX = x + font.width(preceding);

                            // Check if cursor is in this run - draw those chars in black
                            if (row == cursorRow && cursorVisible
                                    && cursorCol >= runStart && cursorCol < runEnd) {
                                // Split: before cursor, cursor char, after cursor
                                String before = lineText.substring(runStart, cursorCol);
                                String cursorChar = lineText.substring(cursorCol, cursorCol + 1);
                                String after = cursorCol + 1 < runEnd
                                        ? lineText.substring(cursorCol + 1, runEnd) : "";

                                int bx = textX;
                                if (!before.isEmpty()) {
                                    graphics.drawString(font, before, bx, cellY + 1, fgColor, false);
                                    bx += font.width(before);
                                }
                                graphics.drawString(font, cursorChar, bx, cellY + 1, 0xFF000000, false);
                                bx += font.width(cursorChar);
                                if (!after.isEmpty()) {
                                    graphics.drawString(font, after, bx, cellY + 1, fgColor, false);
                                }
                            } else {
                                graphics.drawString(font, runText, textX, cellY + 1, fgColor, false);
                            }

                            runStart = col;
                            currentStyle = style;
                        }
                    }
                }
            }
        } finally {
            buffer.unlock();
        }
    }

    private static boolean stylesEqual(TextStyle a, TextStyle b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        TerminalColor fgA = a.getForeground();
        TerminalColor fgB = b.getForeground();
        TerminalColor bgA = a.getBackground();
        TerminalColor bgB = b.getBackground();
        return java.util.Objects.equals(fgA, fgB) && java.util.Objects.equals(bgA, bgB);
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
        try {
            Color color = termColor.toColor();
            if (color != null) {
                return 0xFF000000 | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
            }
        } catch (Exception e) {
            // fallback
        }
        return fallback;
    }
}
