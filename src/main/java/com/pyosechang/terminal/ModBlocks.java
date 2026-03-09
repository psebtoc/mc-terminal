package com.pyosechang.terminal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TerminalMod.MOD_ID);

    public static final RegistryObject<Block> NOTEBOOK = BLOCKS.register("notebook",
            () -> new NotebookBlock(BlockBehaviour.Properties.of()
                    .strength(1.0f)
                    .sound(SoundType.METAL)
                    .noOcclusion()));
}
