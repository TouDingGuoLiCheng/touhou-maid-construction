package com.touhoumaid.construction.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintLoadReport;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import com.touhoumaid.construction.blueprint.BlueprintRegistryCheck;
import com.touhoumaid.construction.blueprint.LitematicReader;
import com.touhoumaid.construction.build.ConstructionDebug;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Construction crew commands.
 * <pre>/tmc debug [on|off]</pre>
 * <pre>/tmc_blueprint dump &lt;relative-or-absolute-path&gt;</pre>
 * Relative paths resolve against the game run directory, then the server folder.
 */
public final class ConstructionCommands {
    private ConstructionCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmc")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .executes(ConstructionCommands::debugStatus)
                                .then(Commands.literal("on")
                                        .executes(ctx -> setDebug(ctx, true)))
                                .then(Commands.literal("off")
                                        .executes(ctx -> setDebug(ctx, false)))));
        dispatcher.register(
                Commands.literal("tmc_blueprint")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("dump")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(ConstructionCommands::dump))));
    }

    private static int debugStatus(CommandContext<CommandSourceStack> context) {
        boolean on = ConstructionDebug.enabled();
        context.getSource().sendSuccess(
                () -> Component.literal("Construction debug logging is " + (on ? "ON" : "OFF")),
                false);
        return on ? 1 : 0;
    }

    private static int setDebug(CommandContext<CommandSourceStack> context, boolean enabled) {
        ConstructionDebug.setEnabled(enabled);
        context.getSource().sendSuccess(
                () -> Component.literal("Construction debug logging " + (enabled ? "enabled" : "disabled")),
                true);
        return 1;
    }

    private static int dump(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String rawPath = StringArgumentType.getString(context, "path");
        Path path = resolvePath(source.getServer(), rawPath);
        if (!Files.isRegularFile(path)) {
            source.sendFailure(Component.literal("File not found: " + path.toAbsolutePath()));
            return 0;
        }

        try {
            Blueprint blueprint = LitematicReader.read(path);
            BlueprintLoadReport report = BlueprintRegistryCheck.check(blueprint);

            source.sendSuccess(() -> Component.literal("=== Blueprint dump ==="), false);
            source.sendSuccess(() -> Component.literal("File: " + path.getFileName()), false);
            source.sendSuccess(() -> Component.literal("Name: " + blueprint.name()
                    + " | Author: " + blueprint.author()), false);
            source.sendSuccess(() -> Component.literal("SchematicVersion: " + blueprint.schematicVersion()
                    + " | DataVersion: " + blueprint.minecraftDataVersion()), false);
            source.sendSuccess(() -> Component.literal("Meta TotalBlocks: " + blueprint.totalBlocksMeta()
                    + " | Counted non-air: " + blueprint.countedNonAirBlocks()
                    + " | Meta Volume: " + blueprint.totalVolumeMeta()), false);
            source.sendSuccess(() -> Component.literal("EnclosingSize: " + formatVec(blueprint.enclosingSize())
                    + " | Regions: " + blueprint.regions().size()), false);

            for (BlueprintRegion region : blueprint.regions().values()) {
                source.sendSuccess(() -> Component.literal("Region '" + region.name() + "': size="
                        + formatVec(region.size())
                        + " signed=" + formatVec(region.signedSize())
                        + " pos=" + formatVec(region.position())
                        + " palette=" + region.palette().size()
                        + " TE=" + region.tileEntities().size()
                        + " Entities=" + region.entities().size()
                        + " nonAir=" + region.countNonAir()), false);
            }

            if (report.hasMissing()) {
                source.sendSuccess(() -> Component.literal("Missing blocks: " + report.missingBlocks()), false);
                source.sendSuccess(() -> Component.literal("Missing entities: " + report.missingEntityTypes()), false);
            } else {
                source.sendSuccess(() -> Component.literal("Registry check: no missing block/entity ids."), false);
            }
            for (String note : report.notes()) {
                source.sendSuccess(() -> Component.literal("Note: " + note), false);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to read litematic: " + e.getMessage()));
            return 0;
        }
    }

    private static Path resolvePath(MinecraftServer server, String rawPath) {
        Path given = Path.of(rawPath);
        if (given.isAbsolute()) {
            return given.normalize();
        }
        Path fromRun = Path.of("").toAbsolutePath().resolve(given).normalize();
        if (Files.isRegularFile(fromRun)) {
            return fromRun;
        }
        return server.getServerDirectory().toPath().toAbsolutePath().resolve(given).normalize();
    }

    private static String formatVec(net.minecraft.core.Vec3i v) {
        return v.getX() + "x" + v.getY() + "x" + v.getZ();
    }
}
