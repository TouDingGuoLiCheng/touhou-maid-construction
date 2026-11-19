package com.touhoumaid.construction.placement;

import com.touhoumaid.construction.blueprint.RawBlockState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Optional;

/** Resolve palette entries to live BlockStates (missing ids → air). */
public final class BlockStateResolve {
    private BlockStateResolve() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState resolve(RawBlockState raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.name());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockState state = block.defaultBlockState();
        for (var entry : raw.properties().entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                continue;
            }
            Optional<?> value = property.getValue(entry.getValue());
            if (value.isPresent()) {
                state = state.setValue((Property) property, (Comparable) value.get());
            }
        }
        return state;
    }

    public static BlockState resolveRotated(RawBlockState raw, int yaw) {
        BlockState state = resolve(raw);
        int steps = SchematicYaw.normalize(yaw);
        if (steps == 0 || state.isAir()) {
            return state;
        }
        try {
            return state.rotate(net.minecraft.world.level.block.Rotation.values()[steps]);
        } catch (RuntimeException ignored) {
            return state;
        }
    }
}
