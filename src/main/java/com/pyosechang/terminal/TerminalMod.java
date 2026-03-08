package com.pyosechang.terminal;

import com.pyosechang.terminal.client.ClientSetup;
import com.pyosechang.terminal.terminal.TerminalConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(TerminalMod.MOD_ID)
public class TerminalMod {
    public static final String MOD_ID = "terminal";

    public TerminalMod() {
        // Register configs
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TerminalConfig.CLIENT_SPEC, "terminal-client.toml");
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, TerminalConfig.SERVER_SPEC, "terminal-server.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(FMLJavaModLoadingContext.get());
        }
    }
}
