package com.pyosechang.terminal.client;

import com.pyosechang.terminal.terminal.TerminalConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * In-game config screen accessible from Mods menu.
 */
public class TerminalConfigScreen extends Screen {

    private final Screen parentScreen;
    private EditBox defaultDirBox;
    private EditBox worldDirBox;

    public TerminalConfigScreen(Screen parentScreen) {
        super(Component.literal("Terminal Settings"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 50;

        // Global default directory
        defaultDirBox = new EditBox(this.font, centerX - 150, y + 12, 300, 20, Component.literal("Default Directory"));
        defaultDirBox.setMaxLength(512);
        if (TerminalConfig.CLIENT_SPEC.isLoaded()) {
            defaultDirBox.setValue(TerminalConfig.DEFAULT_DIR.get());
        }
        this.addRenderableWidget(defaultDirBox);

        // Per-world directory
        y += 60;
        worldDirBox = new EditBox(this.font, centerX - 150, y + 12, 300, 20, Component.literal("World Directory"));
        worldDirBox.setMaxLength(512);
        if (TerminalConfig.SERVER_SPEC.isLoaded()) {
            String val = TerminalConfig.START_DIR.get();
            worldDirBox.setValue(val != null ? val : "");
        } else {
            worldDirBox.setEditable(false);
        }
        this.addRenderableWidget(worldDirBox);

        // Save button
        y += 60;
        this.addRenderableWidget(Button.builder(Component.literal("Save"), btn -> save())
                .bounds(centerX - 75, y, 150, 20)
                .build());

        // Done button
        y += 26;
        this.addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(centerX - 75, y, 150, 20)
                .build());
    }

    private void save() {
        if (TerminalConfig.CLIENT_SPEC.isLoaded()) {
            TerminalConfig.DEFAULT_DIR.set(defaultDirBox.getValue());
        }
        if (TerminalConfig.SERVER_SPEC.isLoaded()) {
            TerminalConfig.START_DIR.set(worldDirBox.getValue());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);

        int centerX = this.width / 2;
        int y = 50;

        graphics.drawString(this.font, "Default Start Directory", centerX - 150, y, 0xAAAAAA);
        y += 60;

        String worldLabel = TerminalConfig.SERVER_SPEC.isLoaded()
                ? "World Start Directory (this world only)"
                : "World Start Directory (join a world to edit)";
        graphics.drawString(this.font, worldLabel, centerX - 150, y, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parentScreen);
    }
}
