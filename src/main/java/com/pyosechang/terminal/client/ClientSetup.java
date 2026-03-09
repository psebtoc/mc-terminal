package com.pyosechang.terminal.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.pyosechang.terminal.claude.ClaudeGuideCommand;
import com.pyosechang.terminal.terminal.TerminalSessionManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

public class ClientSetup {

    public static final KeyMapping TOGGLE_TERMINAL = new KeyMapping(
            "key.terminal.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F12,
            "key.categories.terminal"
    );

    public static void init(FMLJavaModLoadingContext context) {
        context.getModEventBus().addListener(ClientSetup::registerKeyMappings);
        MinecraftForge.EVENT_BUS.register(new ClientTickHandler());
        MinecraftForge.EVENT_BUS.addListener(ClaudeGuideCommand::register);

        // Cleanup on game shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(ClaudeGuideCommand::cleanup, "claude-guide-cleanup"));

        // Register config screen for Mods menu
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (mc, parentScreen) -> new TerminalConfigScreen(parentScreen)
                )
        );
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_TERMINAL);
    }

    public static class ClientTickHandler {
        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (TOGGLE_TERMINAL.consumeClick()) {
                if (mc.screen instanceof TerminalScreen) {
                    mc.setScreen(null);
                } else {
                    // Ensure at least one tab exists
                    TerminalSessionManager.getOrCreate();
                    mc.setScreen(new TerminalScreen());
                }
            }
        }
    }
}
