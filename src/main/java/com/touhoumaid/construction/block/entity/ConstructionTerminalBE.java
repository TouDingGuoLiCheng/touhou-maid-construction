package com.touhoumaid.construction.block.entity;

import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.build.VaultItemAcceptPolicy;
import com.touhoumaid.construction.inventory.ConstructionTerminalMenu;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.item.ItemTerminalData;
import com.touhoumaid.construction.item.PortableVaultPolicy;
import com.touhoumaid.construction.item.VaultItemStorage;
import com.touhoumaid.construction.registry.ModBlockEntities;
import com.touhoumaid.construction.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Construction terminal: material vault + project list for the hub GUI.
 */
public class ConstructionTerminalBE extends BlockEntity implements Container, MenuProvider {
    public static final int COLS = 9;
    /** Visible rows in the material vault GUI; total {@link #ROWS} scroll off-screen. */
    public static final int VISIBLE_ROWS = 3;
    /** Scroll pages shown in the vault GUI title ({@code current / PAGES}). */
    public static final int PAGES = 100;
    /** Backing rows: each page shows {@link #VISIBLE_ROWS} non-overlapping rows. */
    public static final int ROWS = PAGES * VISIBLE_ROWS;
    public static final int SLOT_COUNT = COLS * ROWS;
    public static final int VISIBLE_SLOT_COUNT = COLS * VISIBLE_ROWS;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final List<BuildProject> projects = new ArrayList<>();
    private String selectedProjectId = "";
    private String vaultId = "";
    private boolean loading;

    public ConstructionTerminalBE(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSTRUCTION_TERMINAL.get(), pos, state);
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        persistPortableVault();
        if (level != null && !level.isClientSide) {
            BuildJobService.restoreTerminalJobs((net.minecraft.server.level.ServerLevel) level, this);
        }
    }

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    public String vaultId() {
        return vaultId;
    }

    public void setVaultId(String vaultId) {
        this.vaultId = vaultId == null ? "" : vaultId;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        persistPortableVault();
    }

    public List<BuildProject> getProjects() {
        return projects;
    }

    public String getSelectedProjectId() {
        return selectedProjectId;
    }

    public void setSelectedProjectId(String selectedProjectId) {
        this.selectedProjectId = selectedProjectId == null ? "" : selectedProjectId;
        setChanged();
        sync();
    }

    public Optional<BuildProject> getSelectedProject() {
        return BuildProject.find(projects, selectedProjectId);
    }

    public void addProject(BuildProject project) {
        projects.add(project);
        selectedProjectId = project.id();
        setChanged();
        sync();
    }

    public boolean removeProject(String id) {
        boolean removed = projects.removeIf(p -> p.id().equals(id));
        if (removed) {
            if (id.equals(selectedProjectId)) {
                selectedProjectId = projects.isEmpty() ? "" : projects.get(0).id();
            }
            setChanged();
            sync();
        }
        return removed;
    }

    public ItemStack createPickupStack() {
        ItemStack stack = new ItemStack(ModItems.CONSTRUCTION_TERMINAL.get());
        CompoundTag beTag = saveWithoutMetadata();
        if (level instanceof ServerLevel serverLevel) {
            ItemTerminalData.persistBlockTagToWorldStore(beTag, serverLevel.getServer());
            vaultId = beTag.getString(PortableVaultPolicy.VAULT_ID_KEY);
        } else if (!PortableVaultPolicy.clientCloneShouldKeepLegacyItems(
                beTag.getString(PortableVaultPolicy.VAULT_ID_KEY))) {
            beTag.remove(VaultItemStorage.ITEMS_KEY);
        }
        BlockItem.setBlockEntityData(stack, ModBlockEntities.CONSTRUCTION_TERMINAL.get(), beTag);
        return stack;
    }

    private void persistPortableVault() {
        if (loading || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ItemTerminalData.saveBlockVaultToWorldStore(this, serverLevel.getServer());
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        VaultItemStorage.saveAllItems(tag, items);
        tag.put("Projects", BuildProject.saveList(projects));
        tag.putString("SelectedProject", selectedProjectId);
        if (!vaultId.isEmpty()) {
            tag.putString(PortableVaultPolicy.VAULT_ID_KEY, vaultId);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        loading = true;
        super.load(tag);
        items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        VaultItemStorage.loadAllItems(tag, items);
        projects.clear();
        if (tag.contains("Projects", Tag.TAG_LIST)) {
            projects.addAll(BuildProject.loadList(tag.getList("Projects", Tag.TAG_COMPOUND)));
        }
        selectedProjectId = tag.getString("SelectedProject");
        vaultId = tag.getString(PortableVaultPolicy.VAULT_ID_KEY);
        loading = false;
        persistPortableVault();
        if (level != null && !level.isClientSide) {
            super.setChanged();
            sync();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return VaultItemAcceptPolicy.mayAcceptIntoVault(stack);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.touhou_maid_construction.construction_terminal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return ConstructionTerminalMenu.forBlock(id, playerInv, this);
    }
}
