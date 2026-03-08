package com.pyosechang.terminal.client;

import com.pyosechang.terminal.terminal.TerminalSession;
import com.pyosechang.terminal.util.KeyMapper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

public class TerminalScreen extends Screen {

    private final TerminalSession session;

    // Dynamically calculated based on screen size
    private int termColumns;
    private int termRows;
    private int termX;
    private int termY;

    public TerminalScreen(TerminalSession session) {
        super(Component.literal("Terminal"));
        this.session = session;
    }

    @Override
    protected void init() {
        super.init();

        int padding = 10;
        int titleBarHeight = 14;

        // Calculate how many columns/rows fit in the screen with padding
        termColumns = Math.min(120, (this.width - padding * 2) / TerminalRenderer.CHAR_WIDTH);
        termRows = Math.min(30, (this.height - padding * 2 - titleBarHeight) / TerminalRenderer.CHAR_HEIGHT);

        // Center the terminal
        int termWidth = termColumns * TerminalRenderer.CHAR_WIDTH;
        int termHeight = termRows * TerminalRenderer.CHAR_HEIGHT;
        termX = (this.width - termWidth) / 2;
        termY = (this.height - termHeight) / 2 + titleBarHeight / 2;

        // Resize PTY to match
        if (session != null && session.isAlive()) {
            session.resize(termColumns, termRows);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Full screen dark overlay
        graphics.fill(0, 0, this.width, this.height, 0xDD000000);

        // Render terminal content
        TerminalRenderer.render(graphics, this.font, session, termX, termY, termColumns, termRows);

        // Title bar
        int termWidth = termColumns * TerminalRenderer.CHAR_WIDTH;
        graphics.drawString(this.font, "Terminal [F12 to close]", termX, termY - 12, 0xFF888888, false);

        // Status indicator
        String status = session != null && session.isAlive() ? "CONNECTED" : "DISCONNECTED";
        int statusColor = session != null && session.isAlive() ? 0xFF00FF00 : 0xFFFF0000;
        graphics.drawString(this.font, status, termX + termWidth - this.font.width(status), termY - 12, statusColor, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // F12 closes terminal
        if (keyCode == GLFW.GLFW_KEY_F12) {
            this.onClose();
            return true;
        }

        if (session == null || !session.isAlive()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        // Ctrl+Shift+C = copy
        if (keyCode == GLFW.GLFW_KEY_C
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            // TODO: copy selection
            return true;
        }

        // Ctrl+Shift+V = paste
        if (keyCode == GLFW.GLFW_KEY_V
                && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0
                && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
            String clipboard = this.minecraft.keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                session.write(clipboard);
            }
            return true;
        }

        // Map key to terminal bytes
        byte[] bytes = KeyMapper.keyToBytes(keyCode, modifiers);
        if (bytes != null) {
            session.write(bytes);
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char ch, int modifiers) {
        if (session != null && session.isAlive() && ch >= 32) {
            session.write(String.valueOf(ch).getBytes(StandardCharsets.UTF_8));
            return true;
        }
        return super.charTyped(ch, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // TODO: scroll history buffer
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        // Keep session running in background
        this.minecraft.setScreen(null);
    }
}
