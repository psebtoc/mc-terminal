package com.pyosechang.terminal.client;

import com.pyosechang.terminal.terminal.TerminalSession;
import com.pyosechang.terminal.terminal.TerminalSessionManager;
import com.pyosechang.terminal.util.KeyMapper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

public class TerminalScreen extends Screen {

    // Layout
    private static final int TAB_BAR_HEIGHT = 16;
    private static final int PADDING = 2;

    // Colors matching dark terminal theme
    private static final int SCREEN_BG       = 0xFF000000;
    private static final int TERM_BG          = 0xFF0C0C0C;
    private static final int TAB_BAR_BG       = 0xFF181818;
    private static final int TAB_ACTIVE_BG    = 0xFF0C0C0C;
    private static final int TAB_INACTIVE_BG  = 0xFF2D2D2D;
    private static final int TAB_ACTIVE_TEXT   = 0xFFFFFFFF;
    private static final int TAB_INACTIVE_TEXT = 0xFF888888;
    private static final int TAB_HOVER_BG     = 0xFF383838;
    private static final int TAB_INDICATOR     = 0xFF569CD6; // Blue accent for active tab
    private static final int TAB_CLOSE_NORMAL  = 0xFF666666;
    private static final int TAB_CLOSE_HOVER   = 0xFFFF5555;
    private static final int NEW_TAB_COLOR     = 0xFF666666;
    private static final int BORDER_COLOR      = 0xFF333333;

    private int termColumns, termRows;
    private int termX, termY;

    // Selection state
    private boolean selecting, hasSelection;
    private int selStartCol, selStartRow, selEndCol, selEndRow;

    // Tab interaction
    private int hoveredTab = -1;
    private boolean hoveredClose = false;
    private boolean hoveredNewTab = false;

    // Tab rename
    private boolean renaming = false;
    private int renamingTab = -1;
    private StringBuilder renameBuffer;

    private boolean calibrated;

    // Track tab layout for click handling
    private int[] tabXPositions;
    private int[] tabWidths;
    private int newTabX;

    public TerminalScreen() {
        super(Component.literal("Terminal"));
    }

    @Override
    protected void init() {
        super.init();

        if (!calibrated) {
            TerminalRenderer.calibrateCellSize(this.font);
            calibrated = true;
        }

        int availW = this.width - PADDING * 2;
        int availH = this.height - PADDING - TAB_BAR_HEIGHT;

        termColumns = availW / TerminalRenderer.CELL_WIDTH;
        termRows = availH / TerminalRenderer.CELL_HEIGHT;

        termX = PADDING;
        termY = TAB_BAR_HEIGHT;

        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null && session.isAlive()) {
            session.resize(termColumns, termRows);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int totalW = termColumns * TerminalRenderer.CELL_WIDTH;
        int totalH = termRows * TerminalRenderer.CELL_HEIGHT;

        // Full screen background
        graphics.fill(0, 0, this.width, this.height, SCREEN_BG);

        // Terminal background
        graphics.fill(termX, termY, termX + totalW, termY + totalH, TERM_BG);

        // Tab bar
        updateHover(mouseX, mouseY);
        renderTabBar(graphics, totalW, mouseX, mouseY);

        // Terminal content
        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null) {
            TerminalRenderer.render(graphics, this.font, session,
                    termX, termY, termColumns, termRows,
                    selStartCol, selStartRow, selEndCol, selEndRow, hasSelection);
        }

        // Rename overlay
        if (renaming) {
            renderRenameOverlay(graphics);
        }
    }

    private void renderTabBar(GuiGraphics graphics, int totalW, int mouseX, int mouseY) {
        // Tab bar background
        graphics.fill(0, 0, this.width, TAB_BAR_HEIGHT, TAB_BAR_BG);
        // Bottom border
        graphics.fill(0, TAB_BAR_HEIGHT - 1, this.width, TAB_BAR_HEIGHT, BORDER_COLOR);

        int tabCount = TerminalSessionManager.getTabCount();
        int activeIdx = TerminalSessionManager.getActiveIndex();
        tabXPositions = new int[tabCount];
        tabWidths = new int[tabCount];

        int tx = 2;
        for (int i = 0; i < tabCount; i++) {
            TerminalSession s = TerminalSessionManager.getSessions().get(i);
            String name = s.getName() != null ? s.getName() : "Terminal";
            boolean active = (i == activeIdx);
            boolean hovered = (i == hoveredTab);

            // Measure tab width
            int nameW = this.font.width(name);
            int closeW = this.font.width("x");
            int tabW = 8 + nameW + 10 + closeW + 6; // padding + name + gap + close + padding

            tabXPositions[i] = tx;
            tabWidths[i] = tabW;

            // Tab background
            int bg;
            if (active) bg = TAB_ACTIVE_BG;
            else if (hovered) bg = TAB_HOVER_BG;
            else bg = TAB_INACTIVE_BG;

            graphics.fill(tx, 1, tx + tabW, TAB_BAR_HEIGHT - 1, bg);

            // Active tab indicator (top border)
            if (active) {
                graphics.fill(tx, 0, tx + tabW, 2, TAB_INDICATOR);
            }

            // Tab name
            int textColor = active ? TAB_ACTIVE_TEXT : TAB_INACTIVE_TEXT;
            graphics.drawString(this.font, name, tx + 8, 4, textColor, false);

            // Close button "x"
            int closeX = tx + tabW - closeW - 6;
            int closeFg = (hovered && hoveredClose) ? TAB_CLOSE_HOVER : TAB_CLOSE_NORMAL;
            graphics.drawString(this.font, "x", closeX, 4, closeFg, false);

            // Tab separator
            if (!active && i < tabCount - 1) {
                boolean nextActive = (i + 1 == activeIdx);
                if (!nextActive) {
                    graphics.fill(tx + tabW, 4, tx + tabW + 1, TAB_BAR_HEIGHT - 5, 0xFF444444);
                }
            }

            tx += tabW + 1;
        }

        // New tab "+" button
        newTabX = tx + 4;
        int plusColor = hoveredNewTab ? 0xFFCCCCCC : NEW_TAB_COLOR;
        graphics.drawString(this.font, "+", newTabX, 4, plusColor, false);
    }

    private void renderRenameOverlay(GuiGraphics graphics) {
        if (renamingTab < 0 || renamingTab >= tabXPositions.length) return;
        int tx = tabXPositions[renamingTab];
        int tw = tabWidths[renamingTab];

        // Input background
        graphics.fill(tx, 1, tx + tw, TAB_BAR_HEIGHT - 1, 0xFF1A1A2E);
        graphics.fill(tx, 0, tx + tw, 2, 0xFF89B4FA);

        // Input text with cursor
        String text = renameBuffer.toString() + "_";
        graphics.drawString(this.font, text, tx + 4, 4, 0xFFFFFFFF, false);
    }

    private void updateHover(int mouseX, int mouseY) {
        hoveredTab = -1;
        hoveredClose = false;
        hoveredNewTab = false;

        if (mouseY < 0 || mouseY >= TAB_BAR_HEIGHT) return;

        if (tabXPositions == null) return;
        int tabCount = Math.min(TerminalSessionManager.getTabCount(), tabXPositions.length);

        for (int i = 0; i < tabCount; i++) {
            int tx = tabXPositions[i];
            int tw = tabWidths[i];
            if (mouseX >= tx && mouseX < tx + tw) {
                hoveredTab = i;
                // Check if hovering close button area (right 20px of tab)
                int closeArea = tx + tw - 20;
                hoveredClose = mouseX >= closeArea;
                return;
            }
        }

        // Check new tab button
        if (mouseX >= newTabX && mouseX < newTabX + 12) {
            hoveredNewTab = true;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        // Handle rename mode
        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                finishRename();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renaming = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && renameBuffer.length() > 0) {
                renameBuffer.deleteCharAt(renameBuffer.length() - 1);
                return true;
            }
            return true; // consume all keys during rename
        }

        // F12 closes terminal
        if (keyCode == GLFW.GLFW_KEY_F12) {
            this.onClose();
            return true;
        }

        // Ctrl+T = new tab
        if (ctrl && !shift && keyCode == GLFW.GLFW_KEY_T) {
            TerminalSessionManager.createNewTab(termColumns, termRows);
            clearSelection();
            return true;
        }

        // Ctrl+W = close tab
        if (ctrl && !shift && keyCode == GLFW.GLFW_KEY_W) {
            TerminalSessionManager.closeActiveTab();
            if (TerminalSessionManager.getTabCount() == 0) {
                this.onClose();
            }
            clearSelection();
            return true;
        }

        // Ctrl+Tab / Ctrl+Shift+Tab = switch tabs
        if (ctrl && keyCode == GLFW.GLFW_KEY_TAB) {
            if (shift) TerminalSessionManager.prevTab();
            else TerminalSessionManager.nextTab();
            clearSelection();
            TerminalSession s = TerminalSessionManager.getActive();
            if (s != null && s.isAlive()) s.resize(termColumns, termRows);
            return true;
        }

        // Ctrl+Shift+C = copy
        if (ctrl && shift && keyCode == GLFW.GLFW_KEY_C) {
            copySelection();
            return true;
        }

        // Ctrl+Shift+V or Ctrl+V = paste
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            pasteClipboard();
            return true;
        }

        // Forward to terminal
        TerminalSession session = TerminalSessionManager.getActive();
        if (session == null || !session.isAlive()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        byte[] bytes = KeyMapper.keyToBytes(keyCode, modifiers);
        if (bytes != null) {
            session.write(bytes);
            clearSelection();
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (renaming) {
            if (ch >= 32) {
                renameBuffer.append(ch);
            }
            return true;
        }

        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null && session.isAlive() && ch >= 32) {
            session.write(String.valueOf(ch).getBytes(StandardCharsets.UTF_8));
            clearSelection();
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Tab bar
        if (mouseY >= 0 && mouseY < TAB_BAR_HEIGHT) {
            if (button == 0) {
                return handleTabBarClick(mouseX);
            }
            // Right-click on tab = rename
            if (button == 1 && hoveredTab >= 0) {
                startRename(hoveredTab);
                return true;
            }
            // Middle-click = close tab
            if (button == 2 && hoveredTab >= 0) {
                TerminalSessionManager.closeTab(hoveredTab);
                if (TerminalSessionManager.getTabCount() == 0) this.onClose();
                return true;
            }
        }

        // Left click in terminal = start selection
        if (button == 0 && isInTerminal(mouseX, mouseY)) {
            int col = (int)((mouseX - termX) / TerminalRenderer.CELL_WIDTH);
            int row = (int)((mouseY - termY) / TerminalRenderer.CELL_HEIGHT);
            selStartCol = selEndCol = clamp(col, 0, termColumns - 1);
            selStartRow = selEndRow = clamp(row, 0, termRows - 1);
            selecting = true;
            hasSelection = false;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (selecting && button == 0) {
            int col = (int)((mouseX - termX) / TerminalRenderer.CELL_WIDTH);
            int row = (int)((mouseY - termY) / TerminalRenderer.CELL_HEIGHT);
            selEndCol = clamp(col, 0, termColumns - 1);
            selEndRow = clamp(row, 0, termRows - 1);
            hasSelection = (selStartCol != selEndCol || selStartRow != selEndRow);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selecting && button == 0) {
            selecting = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return true; // consume scroll
    }

    private boolean handleTabBarClick(double mouseX) {
        // Check existing tabs
        if (tabXPositions != null) {
            for (int i = 0; i < tabXPositions.length; i++) {
                int tx = tabXPositions[i];
                int tw = tabWidths[i];
                if (mouseX >= tx && mouseX < tx + tw) {
                    // Close button area
                    if (mouseX >= tx + tw - 20) {
                        TerminalSessionManager.closeTab(i);
                        if (TerminalSessionManager.getTabCount() == 0) this.onClose();
                    } else {
                        TerminalSessionManager.switchTo(i);
                        TerminalSession s = TerminalSessionManager.getActive();
                        if (s != null && s.isAlive()) s.resize(termColumns, termRows);
                    }
                    clearSelection();
                    return true;
                }
            }
        }

        // New tab button
        if (hoveredNewTab) {
            TerminalSessionManager.createNewTab(termColumns, termRows);
            clearSelection();
            return true;
        }

        return false;
    }

    private void startRename(int tabIndex) {
        renaming = true;
        renamingTab = tabIndex;
        TerminalSession s = TerminalSessionManager.getSessions().get(tabIndex);
        renameBuffer = new StringBuilder(s.getName() != null ? s.getName() : "");
    }

    private void finishRename() {
        if (renamingTab >= 0 && renamingTab < TerminalSessionManager.getTabCount()) {
            String newName = renameBuffer.toString().trim();
            if (!newName.isEmpty()) {
                TerminalSessionManager.getSessions().get(renamingTab).setName(newName);
            }
        }
        renaming = false;
        renamingTab = -1;
    }

    private void copySelection() {
        if (!hasSelection) return;
        TerminalSession session = TerminalSessionManager.getActive();
        if (session == null || session.getTextBuffer() == null) return;

        int r1, c1, r2, c2;
        if (selStartRow < selEndRow || (selStartRow == selEndRow && selStartCol <= selEndCol)) {
            r1 = selStartRow; c1 = selStartCol; r2 = selEndRow; c2 = selEndCol;
        } else {
            r1 = selEndRow; c1 = selEndCol; r2 = selStartRow; c2 = selStartCol;
        }

        StringBuilder sb = new StringBuilder();
        var buffer = session.getTextBuffer();
        buffer.lock();
        try {
            for (int row = r1; row <= r2; row++) {
                var line = buffer.getLine(row);
                if (line == null) continue;
                String text = line.getText();
                int start = (row == r1) ? c1 : 0;
                int end = (row == r2) ? c2 : text.length();
                start = clamp(start, 0, text.length());
                end = clamp(end, start, text.length());
                sb.append(text, start, end);
                if (row < r2) sb.append('\n');
            }
        } finally {
            buffer.unlock();
        }

        String selected = sb.toString();
        if (!selected.isEmpty()) {
            this.minecraft.keyboardHandler.setClipboard(selected);
        }
    }

    private void pasteClipboard() {
        String clipboard = this.minecraft.keyboardHandler.getClipboard();
        if (clipboard != null && !clipboard.isEmpty()) {
            TerminalSession session = TerminalSessionManager.getActive();
            if (session != null && session.isAlive()) {
                session.write(clipboard);
            }
        }
    }

    private void clearSelection() {
        hasSelection = false;
        selecting = false;
    }

    private boolean isInTerminal(double mx, double my) {
        return mx >= termX && my >= termY
                && mx < termX + termColumns * TerminalRenderer.CELL_WIDTH
                && my < termY + termRows * TerminalRenderer.CELL_HEIGHT;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(val, max));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { this.minecraft.setScreen(null); }
}
