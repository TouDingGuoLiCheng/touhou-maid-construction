package com.touhoumaid.construction.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.ai.ConstructionPriority;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidFollowOwnerTask",
        remap = false)
public abstract class MaidFollowOwnerTaskMixin {
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

    @Inject(
            method = "start(Lnet/minecraft/server/level/ServerLevel;"
                    + "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void touhouMaidConstruction$skipFollowStart(
            ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        if (ConstructionPriority.followShouldYield(maid)) {
            ci.cancel();
        }
    }
}
