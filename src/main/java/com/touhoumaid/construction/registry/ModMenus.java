package com.touhoumaid.construction.registry;

import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.inventory.ConstructionTerminalMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ConstructionMod.MOD_ID);

    public static final RegistryObject<MenuType<ConstructionTerminalMenu>> CONSTRUCTION_TERMINAL =
            MENUS.register("construction_terminal",
                    () -> IForgeMenuType.create(ConstructionTerminalMenu::fromNetwork));

    private ModMenus() {
    }
}
