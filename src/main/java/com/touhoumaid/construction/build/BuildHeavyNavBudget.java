package com.touhoumaid.construction.build;

import com.touhoumaid.construction.ConstructionMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-tick slot for {@link BuildHeavyNavBudgetPolicy}. */
@Mod.EventBusSubscriber(modid = ConstructionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildHeavyNavBudget {
    private static int usedThisTick;

    private BuildHeavyNavBudget() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            usedThisTick = 0;
        }
    }

    public static boolean tryAcquire(int ticksSinceLastHeavy) {
        if (!BuildHeavyNavBudgetPolicy.mayRunHeavy(ticksSinceLastHeavy, usedThisTick)) {
            return false;
        }
        usedThisTick++;
        return true;
    }
}
