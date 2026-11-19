package com.touhoumaid.construction.registry;

import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ConstructionMod.MOD_ID);

    public static final RegistryObject<Item> CONSTRUCTION_TERMINAL = ITEMS.register(
            "construction_terminal",
            () -> new ConstructionTerminalItem(
                    ModBlocks.CONSTRUCTION_TERMINAL.get(),
                    new Item.Properties().stacksTo(1).fireResistant()));

    private ModItems() {
    }
}
