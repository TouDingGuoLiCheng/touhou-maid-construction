package com.touhoumaid.construction.registry;

import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ConstructionMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<ConstructionTerminalBE>> CONSTRUCTION_TERMINAL =
            BLOCK_ENTITIES.register("construction_terminal",
                    () -> BlockEntityType.Builder.of(
                            ConstructionTerminalBE::new,
                            ModBlocks.CONSTRUCTION_TERMINAL.get()).build(null));

    private ModBlockEntities() {
    }
}
