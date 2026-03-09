package com.pyosechang.terminal;

import com.pyosechang.terminal.client.ClientSetup;
import com.pyosechang.terminal.terminal.TerminalConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(TerminalMod.MOD_ID)
public class TerminalMod {
    public static final String MOD_ID = "terminal";

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final RegistryObject<CreativeModeTab> TERMINAL_TAB = CREATIVE_TABS.register("terminal_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Terminal"))
                    .icon(() -> ModItems.NOTEBOOK.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(ModItems.NOTEBOOK.get()))
                    .build());

    public TerminalMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register blocks, items, and creative tabs
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        CREATIVE_TABS.register(modBus);

        // Register configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TerminalConfig.CLIENT_SPEC, "terminal-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TerminalConfig.SERVER_SPEC, "terminal-server.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(FMLJavaModLoadingContext.get());
        }
    }
}
