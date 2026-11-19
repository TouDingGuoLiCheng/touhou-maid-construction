package com.touhoumaid.construction.build;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import net.minecraft.world.Container;

public final class BlockTerminalMaterialSource implements MaterialSource {
    private final ConstructionTerminalBE terminal;

    public BlockTerminalMaterialSource(ConstructionTerminalBE terminal) {
        this.terminal = terminal;
    }

    @Override
    public boolean hasMaterial(String blockId) {
        return MaterialVault.hasMaterial(terminal, blockId);
    }

    @Override
    public boolean tryConsume(String blockId) {
        boolean ok = MaterialVault.tryConsume(terminal, blockId);
        if (ok) {
            terminal.setChanged();
        }
        return ok;
    }

    @Override
    public void markDirty() {
        terminal.setChanged();
    }

    public Container container() {
        return terminal;
    }
}
