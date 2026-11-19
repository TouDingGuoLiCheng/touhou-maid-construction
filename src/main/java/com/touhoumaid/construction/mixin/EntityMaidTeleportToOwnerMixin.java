package com.touhoumaid.construction.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.ai.ConstructionPriority;
import com.touhoumaid.construction.build.BuildMaterialWaitStayPolicy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid", remap = false)
public abstract class EntityMaidTeleportToOwnerMixin {
    @Inject(
            method = "teleportToOwner(Lnet/minecraft/world/entity/LivingEntity;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void touhouMaidConstruction$blockOwnerTeleport(
            LivingEntity owner, CallbackInfoReturnable<Boolean> cir) {
        EntityMaid maid = (EntityMaid) (Object) this;
        if (BuildMaterialWaitStayPolicy.shouldBlockOwnerTeleport(
                ConstructionPriority.followShouldYield(maid))) {
            cir.setReturnValue(false);
        }
    }
}
