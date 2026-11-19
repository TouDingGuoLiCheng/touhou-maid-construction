package com.touhoumaid.construction.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.ai.ConstructionPriority;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerVehicleTask",
        remap = false)
public abstract class MaidFollowOwnerVehicleTaskMixin {
    @Inject(
            method = "checkExtraStartConditions(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void touhouMaidConstruction$yieldToConstruction(ServerLevel level, EntityMaid maid,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (ConstructionPriority.followShouldYield(maid)) {
            cir.setReturnValue(false);
        }
    }
}
