package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.ConstructionMod;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Slow-path timing for construction after job start. Grep {@code [TMC-Perf]}.
 * <ul>
 *   <li>Always WARN when a measured step ≥ {@link #SECTION_WARN_MS} ms</li>
 *   <li>End-of-tick WARN when total maid construction ticks ≥ {@link #TICK_WARN_MS} ms</li>
 *   <li>With {@code /tmc debug on}, also INFO for steps ≥ {@link #SECTION_DEBUG_MS} ms</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ConstructionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BuildPerfLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final double SECTION_WARN_MS = 25.0D;
    public static final double SECTION_DEBUG_MS = 5.0D;
    public static final double TICK_WARN_MS = 50.0D;

    private static final Acc ACC = new Acc();

    private BuildPerfLog() {
    }

    public static long start() {
        return System.nanoTime();
    }

    /** Whole {@code MaidConstructionBehavior.tick} wall time (drives tickTotal). */
    public static void maidTick(EntityMaid maid, String phase, long startNs) {
        long dt = System.nanoTime() - startNs;
        ACC.addTotal(dt);
        ACC.addBreakdown("maidTick", dt);
        emit("maidTick", dt, "maid=" + ConstructionDebugIds.maid(maid) + " phase=" + phase);
    }

    public static void phase(EntityMaid maid, String phase, long startNs) {
        long dt = System.nanoTime() - startNs;
        ACC.addBreakdown("phase:" + phase, dt);
        emit("phase:" + phase, dt, "maid=" + ConstructionDebugIds.maid(maid));
    }

    public static void claimNearest(
            String projectId,
            int layer,
            int candidatesChecked,
            int hasMaterialCalls,
            long startNs) {
        long dt = System.nanoTime() - startNs;
        ACC.addBreakdown("claimNearest", dt);
        ACC.hasMaterialCalls += Math.max(0, hasMaterialCalls);
        emit(
                "claimNearest",
                dt,
                "project=" + projectId
                        + " layer=" + layer
                        + " checked=" + candidatesChecked
                        + " hasMaterialCalls=" + hasMaterialCalls);
    }

    public static void hasPlaceableScan(String projectId, int layer, int scanned, long startNs) {
        long dt = System.nanoTime() - startNs;
        ACC.addBreakdown("hasPlaceableScan", dt);
        emit(
                "hasPlaceableScan",
                dt,
                "project=" + projectId + " layer=" + layer + " scanned=" + scanned);
    }

    public static void autoCraft(
            String blockId,
            boolean cacheHit,
            boolean ensured,
            boolean crafted,
            long startNs) {
        autoCraft(blockId, cacheHit, ensured, crafted, "", startNs);
    }

    public static void autoCraft(
            String blockId,
            boolean cacheHit,
            boolean ensured,
            boolean crafted,
            String reason,
            long startNs) {
        long dt = System.nanoTime() - startNs;
        ACC.addBreakdown("autoCraft", dt);
        if (cacheHit) {
            ACC.autoCraftCacheHits++;
        } else {
            ACC.autoCraftAttempts++;
        }
        if (crafted) {
            ACC.autoCraftSuccesses++;
        }
        String detail = "blockId=" + blockId
                + " cacheHit=" + cacheHit
                + " ensured=" + ensured
                + " crafted=" + crafted;
        if (reason != null && !reason.isEmpty()) {
            detail += " reason=" + reason;
        }
        // Always surface failed real attempts so vault-full / no-plan shows in latest.log.
        if (!cacheHit && !crafted) {
            double ms = dt / 1_000_000.0D;
            if (ms >= SECTION_WARN_MS) {
                LOGGER.warn("[TMC-Perf] step=autoCraft ms={} {}", String.format("%.1f", ms), detail);
            } else {
                LOGGER.info("[TMC-Perf] step=autoCraft ms={} {}", String.format("%.1f", ms), detail);
            }
            return;
        }
        emit("autoCraft", dt, detail);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Acc snap = ACC.snapshotAndReset();
        if (snap.totalNs <= 0L) {
            return;
        }
        double totalMs = snap.totalNs / 1_000_000.0D;
        boolean warn = totalMs >= TICK_WARN_MS;
        if (!warn && !ConstructionDebug.enabled()) {
            return;
        }
        String msg = String.format(
                "[TMC-Perf] tickTotal=%.1fms claimNearest=%.1fms autoCraft=%.1fms hasPlaceable=%.1fms "
                        + "maidTick=%.1fms idle=%.1fms moving=%.1fms placing=%.1fms "
                        + "hasMaterialCalls=%d autoCraftAttempts=%d autoCraftCacheHits=%d autoCraftOk=%d",
                totalMs,
                snap.ms("claimNearest"),
                snap.ms("autoCraft"),
                snap.ms("hasPlaceableScan"),
                snap.ms("maidTick"),
                snap.ms("phase:IDLE"),
                snap.ms("phase:MOVING"),
                snap.ms("phase:PLACING"),
                snap.hasMaterialCalls,
                snap.autoCraftAttempts,
                snap.autoCraftCacheHits,
                snap.autoCraftSuccesses);
        if (warn) {
            LOGGER.warn(msg);
        } else {
            LOGGER.info(msg);
        }
    }

    private static void emit(String step, long dtNs, String detail) {
        double ms = dtNs / 1_000_000.0D;
        boolean warn = ms >= SECTION_WARN_MS;
        boolean debug = ConstructionDebug.enabled() && ms >= SECTION_DEBUG_MS;
        if (!warn && !debug) {
            return;
        }
        String line = String.format("[TMC-Perf] step=%s ms=%.1f %s", step, ms, detail);
        if (warn) {
            LOGGER.warn(line);
        } else {
            LOGGER.info(line);
        }
    }

    private static final class Acc {
        private final java.util.HashMap<String, Long> breakdown = new java.util.HashMap<>();
        private int hasMaterialCalls;
        private int autoCraftAttempts;
        private int autoCraftCacheHits;
        private int autoCraftSuccesses;
        private long totalNs;

        synchronized void addTotal(long ns) {
            if (ns > 0L) {
                totalNs += ns;
            }
        }

        synchronized void addBreakdown(String step, long ns) {
            if (ns > 0L) {
                breakdown.merge(step, ns, Long::sum);
            }
        }

        synchronized Acc snapshotAndReset() {
            Acc snap = new Acc();
            snap.breakdown.putAll(breakdown);
            snap.hasMaterialCalls = hasMaterialCalls;
            snap.autoCraftAttempts = autoCraftAttempts;
            snap.autoCraftCacheHits = autoCraftCacheHits;
            snap.autoCraftSuccesses = autoCraftSuccesses;
            snap.totalNs = totalNs;
            breakdown.clear();
            hasMaterialCalls = 0;
            autoCraftAttempts = 0;
            autoCraftCacheHits = 0;
            autoCraftSuccesses = 0;
            totalNs = 0L;
            return snap;
        }

        double ms(String step) {
            return breakdown.getOrDefault(step, 0L) / 1_000_000.0D;
        }
    }
}
