package com.pyosechang.terminal;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TerminalMod.MOD_ID);

    public static final RegistryObject<Item> NOTEBOOK = ITEMS.register("notebook",
            () -> new BlockItem(ModBlocks.NOTEBOOK.get(), new Item.Properties().stacksTo(1)));
}
