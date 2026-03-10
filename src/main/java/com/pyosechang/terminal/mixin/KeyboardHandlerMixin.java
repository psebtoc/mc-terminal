package com.pyosechang.terminal.mixin;

import com.pyosechang.terminal.client.TerminalScreen;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts all keyboard input at the GLFW level.
 * When TerminalScreen is open, suppresses vanilla key handling
 * (narrator, fullscreen, debug keys, etc.) so keys only reach the terminal.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void terminal$onKeyPress(long windowPointer, int key, int scanCode, int action, int modifiers, CallbackInfo ci) {
        if (this.minecraft.screen instanceof TerminalScreen) {
            // Let the screen handle it directly via GLFW, bypass all vanilla processing
            // The Screen's keyPressed/keyReleased will still be called by Forge's event system
            // We need to manually forward to the screen and then cancel
            if (windowPointer == this.minecraft.getWindow().getWindow()) {
                if (action == 1) { // GLFW_PRESS
                    this.minecraft.screen.keyPressed(key, scanCode, modifiers);
                } else if (action == 0) { // GLFW_RELEASE
                    this.minecraft.screen.keyReleased(key, scanCode, modifiers);
                }
                ci.cancel();
            }
        }
    }
}
