package com.touhoumaid.construction;

import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.build.ConstructionLoginRecovery;
import com.touhoumaid.construction.build.ConstructionMaidRecovery;
import com.touhoumaid.construction.command.ConstructionCommands;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import com.touhoumaid.construction.registry.ModBlockEntities;
import com.touhoumaid.construction.registry.ModBlocks;
import com.touhoumaid.construction.registry.ModCreativeTabs;
import com.touhoumaid.construction.registry.ModItems;
import com.touhoumaid.construction.registry.ModMenus;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(ConstructionMod.MOD_ID)
public class ConstructionMod {
    public static final String MOD_ID = "touhou_maid_construction";
    public static final String TLM_MOD_ID = "touhou_little_maid";
    public static final String HOVER_RECOVER_TICKS_KEY = "tmc_hover_recover_ticks";
    public static final int HOVER_RECOVER_TICKS = 40;

    private static final Logger LOGGER = LogUtils.getLogger();

    public ConstructionMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> com.touhoumaid.construction.network.ModNetwork.register());
        LOGGER.info("Touhou Maid: Construction Crew loaded.");
        LOGGER.info("Touhou Little Maid present: {}", ModList.get().isLoaded(TLM_MOD_ID));
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ConstructionCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Touhou Maid: Construction Crew server starting.");
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BuildJobService.restoreActiveBuildingJobs(player);
        ServerLevel level = player.serverLevel();
        for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
            if (entity instanceof EntityMaid maid) {
                ConstructionMaidRecovery.recoverOnJoin(level, maid, "player_login");
            }
        }
        ConstructionLoginRecovery.schedule(player);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (event.getEntity() instanceof EntityMaid maid && event.getLevel() instanceof ServerLevel level) {
            ConstructionMaidRecovery.recoverOnJoin(level, maid, "entity_join");
        }
    }
}
