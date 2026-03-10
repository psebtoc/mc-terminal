package com.pyosechang.terminal.client;

import com.pyosechang.terminal.terminal.TerminalConfig;
import com.pyosechang.terminal.terminal.TerminalSession;
import com.pyosechang.terminal.terminal.TerminalSessionManager;
import com.pyosechang.terminal.util.KeyMapper;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class TerminalScreen extends Screen {

    // Layout
    private static final int TITLE_BAR_HEIGHT = 14;
    private static final int TAB_BAR_HEIGHT = 16;
    private static final int PADDING = 10;
    private static final float WINDOWED_SCALE = 0.70f;

    // Colors
    private static final int TITLE_BAR_BG    = 0xFF1E1E1E;
    private static final int TERM_BG          = 0xFF0C0C0C;
    private static final int OVERLAY_BG       = 0x88000000; // semi-transparent for windowed mode
    private static final int TAB_BAR_BG       = 0xFF181818;
    private static final int TAB_ACTIVE_BG    = 0xFF0C0C0C;
    private static final int TAB_INACTIVE_BG  = 0xFF2D2D2D;
    private static final int TAB_ACTIVE_TEXT   = 0xFFFFFFFF;
    private static final int TAB_INACTIVE_TEXT = 0xFF888888;
    private static final int TAB_HOVER_BG     = 0xFF383838;
    private static final int TAB_INDICATOR     = 0xFF569CD6;
    private static final int TAB_CLOSE_NORMAL  = 0xFF666666;
    private static final int TAB_CLOSE_HOVER   = 0xFFFF5555;
    private static final int NEW_TAB_COLOR     = 0xFF666666;
    private static final int BORDER_COLOR      = 0xFF333333;
    private static final int TITLE_BTN_HOVER   = 0xFF333333;
    private static final int CLOSE_BTN_HOVER   = 0xFFE81123;

    // Window state (static to persist across open/close)
    private static boolean maximized = true;

    // Window bounds (computed in init)
    private int winX, winY, winW, winH;

    private int termColumns, termRows;
    private int termX, termY;

    // Scale override: ratio of terminal scale to MC gui scale
    // e.g., MC=4x, terminal=3x → scaleFactor=0.75
    private float scaleFactor = 1.0f;
    // Logical size at terminal scale (larger than this.width/height when scaleFactor < 1)
    private int scaledWidth, scaledHeight;

    // Selection state
    private boolean selecting, hasSelection;
    private int selStartCol, selStartRow, selEndCol, selEndRow;

    // Tab interaction
    private int hoveredTab = -1;
    private boolean hoveredClose = false;
    private boolean hoveredNewTab = false;

    // Title bar button hover
    private int hoveredTitleBtn = -1; // 0=minimize, 1=maximize, 2=close

    // Scroll
    private int scrollOffset = 0;

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

    public TerminalScreen(boolean startMaximized) {
        super(Component.literal("Terminal"));
        maximized = startMaximized;
    }

    @Override
    protected void init() {
        super.init();

        if (!calibrated) {
            TerminalRenderer.calibrateCellSize(this.font);
            calibrated = true;
        }

        // Compute scale factor: terminal scale / MC gui scale
        int mcScale = (int) this.minecraft.getWindow().getGuiScale();
        int termScale = TerminalConfig.CLIENT_SPEC.isLoaded() ? TerminalConfig.GUI_SCALE.get() : 0;
        if (termScale > 0 && termScale != mcScale) {
            scaleFactor = (float) termScale / mcScale;
            scaledWidth = (int)(this.width / scaleFactor);
            scaledHeight = (int)(this.height / scaleFactor);
        } else {
            scaleFactor = 1.0f;
            scaledWidth = this.width;
            scaledHeight = this.height;
        }

        computeLayout();

        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null && session.isAlive()) {
            session.resize(termColumns, termRows);
        }
    }

    private void computeLayout() {
        int sw = scaledWidth;
        int sh = scaledHeight;
        if (maximized) {
            winX = 0;
            winY = 0;
            winW = sw;
            winH = sh;
        } else {
            winW = (int)(sw * WINDOWED_SCALE);
            winH = (int)(sh * WINDOWED_SCALE);
            winX = (sw - winW) / 2;
            winY = (sh - winH) / 2;
        }

        int contentTop = winY + TITLE_BAR_HEIGHT + TAB_BAR_HEIGHT + PADDING;
        int contentLeft = winX + PADDING;
        int availW = winW - PADDING * 2;
        int availH = winH - TITLE_BAR_HEIGHT - TAB_BAR_HEIGHT - PADDING * 2;

        termColumns = availW / TerminalRenderer.CELL_WIDTH;
        termRows = availH / TerminalRenderer.CELL_HEIGHT;
        termX = contentLeft;
        termY = contentTop;
    }

    private void toggleMaximize() {
        maximized = !maximized;
        computeLayout();
        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null && session.isAlive()) {
            session.resize(termColumns, termRows);
        }
    }

    // ---- Rendering ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Transform mouse coordinates to scaled space
        int smx = (int)(mouseX / scaleFactor);
        int smy = (int)(mouseY / scaleFactor);

        // Draw overlay BEFORE scaling (in screen space)
        if (!maximized) {
            graphics.fill(0, 0, this.width, this.height, OVERLAY_BG);
        }

        // Apply terminal scale
        graphics.pose().pushPose();
        graphics.pose().scale(scaleFactor, scaleFactor, 1.0f);

        // Background
        if (maximized) {
            graphics.fill(0, 0, scaledWidth, scaledHeight, TERM_BG);
        } else {
            // Window border
            graphics.fill(winX - 1, winY - 1, winX + winW + 1, winY + winH + 1, BORDER_COLOR);
        }

        // Window background
        graphics.fill(winX, winY, winX + winW, winY + winH, TERM_BG);

        // Title bar
        updateTitleBarHover(smx, smy);
        renderTitleBar(graphics, smx, smy);

        // Tab bar
        updateHover(smx, smy);
        renderTabBar(graphics, smx, smy);

        // Terminal content
        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null) {
            int hoverCol = (int)((smx - termX) / (float) TerminalRenderer.CELL_WIDTH);
            int hoverRow = (int)((smy - termY) / (float) TerminalRenderer.CELL_HEIGHT);
            TerminalRenderer.render(graphics, this.font, session,
                    termX, termY, termColumns, termRows,
                    selStartCol, selStartRow, selEndCol, selEndRow, hasSelection,
                    hoverRow, hoverCol, scrollOffset);

            // Scrollbar
            renderScrollbar(graphics, session);
        }

        // Rename overlay
        if (renaming) {
            renderRenameOverlay(graphics);
        }

        graphics.pose().popPose();
    }

    private void renderTitleBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tbY = winY;
        graphics.fill(winX, tbY, winX + winW, tbY + TITLE_BAR_HEIGHT, TITLE_BAR_BG);

        // Title text
        graphics.drawString(this.font, "Terminal", winX + 6, tbY + 3, 0xFF888888, false);

        // Window control buttons (right-aligned): _ □ X
        int btnW = 30;
        int btnH = TITLE_BAR_HEIGHT;
        int btnX = winX + winW - btnW * 3;

        int iconColor = 0xFFCCCCCC;
        int cy = tbY + btnH / 2; // vertical center

        // Minimize button: horizontal line ─
        if (hoveredTitleBtn == 0) {
            graphics.fill(btnX, tbY, btnX + btnW, tbY + btnH, TITLE_BTN_HOVER);
        }
        graphics.fill(btnX + 10, cy, btnX + 20, cy + 1, iconColor);
        btnX += btnW;

        // Maximize/Restore button: square □ or overlapping squares
        if (hoveredTitleBtn == 1) {
            graphics.fill(btnX, tbY, btnX + btnW, tbY + btnH, TITLE_BTN_HOVER);
        }
        if (maximized) {
            // Restore: two overlapping squares
            int sx = btnX + 11, sy = cy - 4;
            // Back square (offset +2, -2)
            graphics.fill(sx + 2, sy - 2, sx + 9, sy - 1, iconColor);
            graphics.fill(sx + 8, sy - 2, sx + 9, sy + 5, iconColor);
            // Front square
            graphics.fill(sx, sy, sx + 7, sy + 1, iconColor);
            graphics.fill(sx, sy + 6, sx + 7, sy + 7, iconColor);
            graphics.fill(sx, sy, sx + 1, sy + 7, iconColor);
            graphics.fill(sx + 6, sy, sx + 7, sy + 7, iconColor);
        } else {
            // Maximize: single square
            int sx = btnX + 10, sy = cy - 4;
            graphics.fill(sx, sy, sx + 9, sy + 1, iconColor);
            graphics.fill(sx, sy + 8, sx + 9, sy + 9, iconColor);
            graphics.fill(sx, sy, sx + 1, sy + 9, iconColor);
            graphics.fill(sx + 8, sy, sx + 9, sy + 9, iconColor);
        }
        btnX += btnW;

        // Close button: X shape (two diagonal lines)
        if (hoveredTitleBtn == 2) {
            graphics.fill(btnX, tbY, btnX + btnW, tbY + btnH, CLOSE_BTN_HOVER);
        }
        int cx = btnX + 15, xSize = 4;
        for (int i = -xSize; i <= xSize; i++) {
            graphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, iconColor);
            graphics.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, iconColor);
        }
    }

    private void renderTabBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int tabY = winY + TITLE_BAR_HEIGHT;
        graphics.fill(winX, tabY, winX + winW, tabY + TAB_BAR_HEIGHT, TAB_BAR_BG);
        graphics.fill(winX, tabY + TAB_BAR_HEIGHT - 1, winX + winW, tabY + TAB_BAR_HEIGHT, BORDER_COLOR);

        int tabCount = TerminalSessionManager.getTabCount();
        int activeIdx = TerminalSessionManager.getActiveIndex();
        tabXPositions = new int[tabCount];
        tabWidths = new int[tabCount];

        int tx = winX + 2;
        for (int i = 0; i < tabCount; i++) {
            TerminalSession s = TerminalSessionManager.getSessions().get(i);
            String name = s.getName() != null ? s.getName() : "Terminal";
            boolean active = (i == activeIdx);
            boolean hovered = (i == hoveredTab);

            int nameW = this.font.width(name);
            int closeW = this.font.width("x");
            int tabW = 8 + nameW + 10 + closeW + 6;

            tabXPositions[i] = tx;
            tabWidths[i] = tabW;

            int bg;
            if (active) bg = TAB_ACTIVE_BG;
            else if (hovered) bg = TAB_HOVER_BG;
            else bg = TAB_INACTIVE_BG;

            graphics.fill(tx, tabY + 1, tx + tabW, tabY + TAB_BAR_HEIGHT - 1, bg);

            if (active) {
                graphics.fill(tx, tabY, tx + tabW, tabY + 2, TAB_INDICATOR);
            }

            int textColor = active ? TAB_ACTIVE_TEXT : TAB_INACTIVE_TEXT;
            graphics.drawString(this.font, name, tx + 8, tabY + 4, textColor, false);

            int closeX = tx + tabW - closeW - 6;
            int closeFg = (hovered && hoveredClose) ? TAB_CLOSE_HOVER : TAB_CLOSE_NORMAL;
            graphics.drawString(this.font, "x", closeX, tabY + 4, closeFg, false);

            if (!active && i < tabCount - 1) {
                boolean nextActive = (i + 1 == activeIdx);
                if (!nextActive) {
                    graphics.fill(tx + tabW, tabY + 4, tx + tabW + 1, tabY + TAB_BAR_HEIGHT - 5, 0xFF444444);
                }
            }

            tx += tabW + 1;
        }

        newTabX = tx + 4;
        int plusColor = hoveredNewTab ? 0xFFCCCCCC : NEW_TAB_COLOR;
        graphics.drawString(this.font, "+", newTabX, tabY + 4, plusColor, false);
    }

    private void renderScrollbar(GuiGraphics graphics, TerminalSession session) {
        if (session.getTextBuffer() == null) return;
        int historyLines = session.getTextBuffer().getHistoryBuffer().getLineCount();
        if (historyLines <= 0) return;

        int totalLines = historyLines + termRows;
        int trackX = winX + winW - PADDING / 2 - 3;
        int trackY = termY;
        int trackH = termRows * TerminalRenderer.CELL_HEIGHT;
        int barW = 3;

        // Track background
        graphics.fill(trackX, trackY, trackX + barW, trackY + trackH, 0x33FFFFFF);

        // Thumb size and position
        float visibleRatio = (float) termRows / totalLines;
        int thumbH = Math.max((int)(trackH * visibleRatio), 8);
        float scrollRatio = (float) scrollOffset / historyLines;
        int thumbY = trackY + (int)((trackH - thumbH) * (1.0f - scrollRatio));

        int thumbColor = scrollOffset > 0 ? 0xAABBBBBB : 0x66888888;
        graphics.fill(trackX, thumbY, trackX + barW, thumbY + thumbH, thumbColor);
    }

    private void renderRenameOverlay(GuiGraphics graphics) {
        if (renamingTab < 0 || renamingTab >= tabXPositions.length) return;
        int tx = tabXPositions[renamingTab];
        int tw = tabWidths[renamingTab];
        int tabY = winY + TITLE_BAR_HEIGHT;

        graphics.fill(tx, tabY + 1, tx + tw, tabY + TAB_BAR_HEIGHT - 1, 0xFF1A1A2E);
        graphics.fill(tx, tabY, tx + tw, tabY + 2, 0xFF89B4FA);

        String text = renameBuffer.toString() + "_";
        graphics.drawString(this.font, text, tx + 4, tabY + 4, 0xFFFFFFFF, false);
    }

    // ---- Hover detection ----

    private void updateTitleBarHover(int mouseX, int mouseY) {
        hoveredTitleBtn = -1;
        int tbY = winY;
        if (mouseY < tbY || mouseY >= tbY + TITLE_BAR_HEIGHT) return;

        int btnW = 30;
        int btnX = winX + winW - btnW * 3;
        if (mouseX >= btnX && mouseX < btnX + btnW) hoveredTitleBtn = 0;
        else if (mouseX >= btnX + btnW && mouseX < btnX + btnW * 2) hoveredTitleBtn = 1;
        else if (mouseX >= btnX + btnW * 2 && mouseX < btnX + btnW * 3) hoveredTitleBtn = 2;
    }

    private void updateHover(int mouseX, int mouseY) {
        hoveredTab = -1;
        hoveredClose = false;
        hoveredNewTab = false;

        int tabY = winY + TITLE_BAR_HEIGHT;
        if (mouseY < tabY || mouseY >= tabY + TAB_BAR_HEIGHT) return;
        if (tabXPositions == null) return;

        int tabCount = Math.min(TerminalSessionManager.getTabCount(), tabXPositions.length);
        for (int i = 0; i < tabCount; i++) {
            int tx = tabXPositions[i];
            int tw = tabWidths[i];
            if (mouseX >= tx && mouseX < tx + tw) {
                hoveredTab = i;
                hoveredClose = mouseX >= tx + tw - 20;
                return;
            }
        }

        if (mouseX >= newTabX && mouseX < newTabX + 12) {
            hoveredNewTab = true;
        }
    }

    // ---- Input ----

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) { finishRename(); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { renaming = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && renameBuffer.length() > 0) {
                renameBuffer.deleteCharAt(renameBuffer.length() - 1);
                return true;
            }
            return true;
        }

        // Toggle key = close terminal (matches the KeyMapping used to open it)
        if (ClientSetup.TOGGLE_TERMINAL.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }

        // Ctrl+Shift+T = new tab
        if (ctrl && shift && keyCode == GLFW.GLFW_KEY_T) {
            TerminalSessionManager.createNewTab(termColumns, termRows);
            clearSelection();
            return true;
        }

        // Ctrl+Shift+W = close tab
        if (ctrl && shift && keyCode == GLFW.GLFW_KEY_W) {
            TerminalSessionManager.closeActiveTab();
            if (TerminalSessionManager.getTabCount() == 0) this.onClose();
            clearSelection();
            return true;
        }

        // Ctrl+Tab / Ctrl+Shift+Tab = switch tabs
        if (ctrl && keyCode == GLFW.GLFW_KEY_TAB) {
            if (shift) TerminalSessionManager.prevTab();
            else TerminalSessionManager.nextTab();
            clearSelection();
            scrollOffset = 0;
            TerminalSession s = TerminalSessionManager.getActive();
            if (s != null && s.isAlive()) s.resize(termColumns, termRows);
            return true;
        }

        // Ctrl+Alt+[1-9] = switch to Nth tab
        boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
        if (ctrl && alt && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_9) {
            int tabIndex = keyCode - GLFW.GLFW_KEY_1;
            if (tabIndex < TerminalSessionManager.getTabCount()) {
                TerminalSessionManager.switchTo(tabIndex);
                clearSelection();
                scrollOffset = 0;
                TerminalSession s = TerminalSessionManager.getActive();
                if (s != null && s.isAlive()) s.resize(termColumns, termRows);
            }
            return true;
        }

        // Shift+PageUp/PageDown = scroll history
        if (shift && keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scrollUp(termRows);
            return true;
        }
        if (shift && keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scrollDown(termRows);
            return true;
        }

        // Ctrl+C: copy if selection exists, otherwise send interrupt to terminal
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection) {
                copySelection();
                return true;
            }
            // No selection → fall through to send Ctrl+C (interrupt) to terminal
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
            scrollOffset = 0; // snap to bottom on input
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (renaming) {
            if (ch > 32) renameBuffer.append(ch); // no spaces in tab names
            return true;
        }

        // Ctrl combos are handled in keyPressed — don't double-send characters
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl) return false;

        TerminalSession session = TerminalSessionManager.getActive();
        if (session != null && session.isAlive() && ch >= 32) {
            session.write(String.valueOf(ch).getBytes(StandardCharsets.UTF_8));
            clearSelection();
            scrollOffset = 0; // snap to bottom on input
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    // Scale mouse coordinates from screen space to terminal space
    private double smx(double mouseX) { return mouseX / scaleFactor; }
    private double smy(double mouseY) { return mouseY / scaleFactor; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double mx = smx(mouseX), my = smy(mouseY);
        // Title bar buttons
        int tbY = winY;
        if (my >= tbY && my < tbY + TITLE_BAR_HEIGHT && button == 0) {
            if (hoveredTitleBtn == 0) { this.onClose(); return true; }           // minimize
            if (hoveredTitleBtn == 1) { toggleMaximize(); return true; }          // maximize/restore
            if (hoveredTitleBtn == 2) {                                           // close
                TerminalSessionManager.destroyAll();
                this.onClose();
                return true;
            }
        }

        // Tab bar
        int tabY = winY + TITLE_BAR_HEIGHT;
        if (my >= tabY && my < tabY + TAB_BAR_HEIGHT) {
            if (button == 0) return handleTabBarClick(mx);
            if (button == 1 && hoveredTab >= 0) { startRename(hoveredTab); return true; }
            if (button == 2 && hoveredTab >= 0) {
                TerminalSessionManager.closeTab(hoveredTab);
                if (TerminalSessionManager.getTabCount() == 0) this.onClose();
                return true;
            }
        }

        // Left click in terminal
        if (button == 0 && isInTerminal(mx, my)) {
            int col = (int)((mx - termX) / TerminalRenderer.CELL_WIDTH);
            int row = (int)((my - termY) / TerminalRenderer.CELL_HEIGHT);

            // Ctrl+click = open link
            if (hasControlDown()) {
                TerminalRenderer.LinkRegion link = TerminalRenderer.getLinkAt(row, col);
                if (link != null) {
                    openLink(link.url);
                    return true;
                }
            }

            // Start selection
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
            double mx = smx(mouseX), my = smy(mouseY);
            int col = (int)((mx - termX) / TerminalRenderer.CELL_WIDTH);
            int row = (int)((my - termY) / TerminalRenderer.CELL_HEIGHT);
            selEndCol = clamp(col, 0, termColumns - 1);
            selEndRow = clamp(row, 0, termRows - 1);
            hasSelection = (selStartCol != selEndCol || selStartRow != selEndRow);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selecting && button == 0) { selecting = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int lines = 3; // scroll 3 lines per tick
        if (delta > 0) {
            scrollUp(lines);
        } else if (delta < 0) {
            scrollDown(lines);
        }
        return true;
    }

    private void scrollUp(int lines) {
        TerminalSession session = TerminalSessionManager.getActive();
        if (session == null || session.getTextBuffer() == null) return;
        int maxScroll = session.getTextBuffer().getHistoryBuffer().getLineCount();
        scrollOffset = Math.min(scrollOffset + lines, maxScroll);
    }

    private void scrollDown(int lines) {
        scrollOffset = Math.max(scrollOffset - lines, 0);
    }

    // ---- Tab helpers ----

    private boolean handleTabBarClick(double mouseX) {
        if (tabXPositions != null) {
            for (int i = 0; i < tabXPositions.length; i++) {
                int tx = tabXPositions[i];
                int tw = tabWidths[i];
                if (mouseX >= tx && mouseX < tx + tw) {
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

    private void openLink(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                Util.getPlatform().openUri(new URI(url));
            } else {
                // Local file path — open containing folder in explorer
                File file = new File(url);
                if (file.isDirectory()) {
                    Util.getPlatform().openFile(file);
                } else if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
                    Util.getPlatform().openFile(file.getParentFile());
                }
            }
        } catch (Exception ignored) {}
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
