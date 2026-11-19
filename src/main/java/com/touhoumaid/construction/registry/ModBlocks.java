package com.touhoumaid.construction.registry;

import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.block.ConstructionScaffoldBlock;
import com.touhoumaid.construction.block.ConstructionTerminalBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ConstructionMod.MOD_ID);

    public static final RegistryObject<Block> CONSTRUCTION_TERMINAL = BLOCKS.register(
            "construction_terminal",
            () -> new ConstructionTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F, 1200.0F)
                    .sound(SoundType.WOOD)
                    .pushReaction(PushReaction.BLOCK)));

    /** Maid staging towers; not obtainable in survival — reuses vanilla scaffolding appearance. */
    public static final RegistryObject<Block> CONSTRUCTION_SCAFFOLD = BLOCKS.register(
            "construction_scaffold",
            () -> new ConstructionScaffoldBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .noCollission()
                    .strength(0.0F)
                    .sound(SoundType.BAMBOO)
                    .dynamicShape()
                    .pushReaction(PushReaction.DESTROY)));

    private ModBlocks() {
    }
}
