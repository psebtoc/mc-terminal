package com.pyosechang.terminal;

import com.pyosechang.terminal.client.ClientSetup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(TerminalMod.MOD_ID)
public class TerminalMod {
    public static final String MOD_ID = "terminal";

    public TerminalMod() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init(FMLJavaModLoadingContext.get());
        }
    }
}
