package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persists active BUILDING sessions on the player so login can force-restore jobs. */
public final class ConstructionPlayerBuildIndex {
    private static final String ROOT = "TouhouMaidConstruction";
    private static final String ACTIVE_JOBS = "ActiveBuildJobs";

    private ConstructionPlayerBuildIndex() {
    }

    public static void remember(ServerPlayer player, BuildJobKey key) {
        if (player == null || key == null) {
            return;
        }
        List<BuildJobKey> keys = new ArrayList<>(activeJobs(player));
        keys.removeIf(existing -> sameSession(existing, key));
        keys.add(key);
        write(player, keys);
    }

    public static void forget(ServerPlayer player, BuildJobKey key) {
        if (player == null || key == null) {
            return;
        }
        List<BuildJobKey> keys = new ArrayList<>(activeJobs(player));
        if (keys.removeIf(existing -> sameSession(existing, key))) {
            write(player, keys);
        }
    }

    public static List<BuildJobKey> activeJobs(ServerPlayer player) {
        if (player == null) {
            return List.of();
        }
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        if (!root.contains(ACTIVE_JOBS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = root.getList(ACTIVE_JOBS, Tag.TAG_COMPOUND);
        List<BuildJobKey> keys = new ArrayList<>(list.size());
        for (Tag tag : list) {
            decode((CompoundTag) tag, player.getUUID()).ifPresent(keys::add);
        }
        return List.copyOf(dedupe(keys));
    }

    private static void write(ServerPlayer player, List<BuildJobKey> keys) {
        CompoundTag root = player.getPersistentData().contains(ROOT)
                ? player.getPersistentData().getCompound(ROOT)
                : new CompoundTag();
        ListTag list = new ListTag();
        for (BuildJobKey key : dedupe(keys)) {
            list.add(encode(key));
        }
        if (list.isEmpty()) {
            root.remove(ACTIVE_JOBS);
        } else {
            root.put(ACTIVE_JOBS, list);
        }
        if (root.isEmpty()) {
            player.getPersistentData().remove(ROOT);
        } else {
            player.getPersistentData().put(ROOT, root);
        }
    }

    private static CompoundTag encode(BuildJobKey key) {
        CompoundTag tag = new CompoundTag();
        tag.putString("ProjectId", key.projectId());
        tag.putLong("TerminalPos", key.terminalPos().asLong());
        tag.putBoolean("ItemMode", key.itemMode());
        tag.putString("Hand", key.hand().name());
        tag.putUUID("OwnerId", key.ownerId());
        return tag;
    }

    private static java.util.Optional<BuildJobKey> decode(CompoundTag tag, UUID fallbackOwner) {
        if (!tag.contains("ProjectId")) {
            return java.util.Optional.empty();
        }
        UUID ownerId = tag.contains("OwnerId") ? tag.getUUID("OwnerId") : fallbackOwner;
        BlockPos terminalPos = tag.contains("TerminalPos")
                ? BlockPos.of(tag.getLong("TerminalPos"))
                : BlockPos.ZERO;
        InteractionHand hand;
        try {
            hand = InteractionHand.valueOf(tag.getString("Hand"));
        } catch (IllegalArgumentException e) {
            hand = InteractionHand.MAIN_HAND;
        }
        return java.util.Optional.of(new BuildJobKey(
                tag.getString("ProjectId"),
                terminalPos,
                tag.getBoolean("ItemMode"),
                hand,
                ownerId));
    }

    private static boolean sameSession(BuildJobKey left, BuildJobKey right) {
        return left.projectId().equals(right.projectId())
                && left.terminalPos().equals(right.terminalPos())
                && left.itemMode() == right.itemMode()
                && left.hand() == right.hand()
                && left.ownerId().equals(right.ownerId());
    }

    private static List<BuildJobKey> dedupe(List<BuildJobKey> keys) {
        Map<String, BuildJobKey> unique = new LinkedHashMap<>();
        for (BuildJobKey key : keys) {
            unique.put(sessionKey(key), key);
        }
        return List.copyOf(unique.values());
    }

    private static String sessionKey(BuildJobKey key) {
        return key.ownerId()
                + "|"
                + key.projectId()
                + "|"
                + key.terminalPos().asLong()
                + "|"
                + key.itemMode()
                + "|"
                + key.hand();
    }
}
