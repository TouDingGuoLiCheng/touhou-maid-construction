package com.touhoumaid.construction.task;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.ai.ConstructionPriority;
import com.touhoumaid.construction.ai.MaidConstructionBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public class TaskConstruction implements IMaidTask {
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(ConstructionMod.MOD_ID, "construction");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public ItemStack getIcon() {
        return Items.BRICKS.getDefaultInstance();
    }

    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return InitSounds.MAID_FARM.get();
    }

    @Override
    public List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidConstructionBehavior()));
    }

    @Override
    public boolean enableLookAndRandomWalk(EntityMaid maid) {
        return ConstructionPriority.allowAmbientAi(maid);
    }
}
