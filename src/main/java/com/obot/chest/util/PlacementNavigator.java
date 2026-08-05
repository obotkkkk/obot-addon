package com.obot.chest.util;

import com.obot.chest.litematica.LitematicaCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Flies toward the nearest Litematica "missing block" position using a small state machine, instead of
 * a naive straight line. This design (phases, skip-list, watchdogs) is based on the same approach found
 * in a reference addon the user shared (a decompiled "autoflyer" jar's LitematicaNavigator class) - it
 * fixes two problems a naive straight-line-only approach has:
 *
 * 1. Getting permanently stuck hovering at a position that never actually gets marked "done" (e.g. the
 *    block never gets placed there for whatever reason) - handled by a per-target watchdog and a
 *    "hovered too long" timeout, both of which temporarily add the position to a skip-list and force a
 *    search for a different target.
 * 2. Flying straight into walls/terrain - handled by checking whether a direct line is actually clear
 *    first; if not, it climbs to a cruise altitude, flies over at that altitude, then descends onto the
 *    target, instead of just reactively noticing it's stuck.
 *
 * Phases: DIRECT (straight line, used when clear) or ASCEND -> TRAVEL -> DESCEND (climb over, then down
 * onto the target) -> HOVER (arrived, waiting).
 */
public class PlacementNavigator {
    private enum Phase { ASCEND, TRAVEL, DESCEND, HOVER, DIRECT }

    private static final int HOVER_TIMEOUT_TICKS = 100;   // 5s hovering with nothing happening -> skip
    private static final int STUCK_TIMEOUT_TICKS = 24;    // ~1.2s of near-zero movement -> retry higher
    private static final int MAX_RETRIES_BEFORE_SKIP = 3; // give up on a target after this many stuck-retries
    private static final int TARGET_WATCHDOG_TICKS = 600; // 30s total on one target, no matter what -> skip
    private static final long SKIP_DURATION_TICKS = 200;  // ~10s before a skipped position can be picked again
    private static final double STUCK_MOVE_EPSILON_SQ = 4.0E-4;
    private static final double ARRIVE_DISTANCE = 0.4;
    private static final double CRUISE_MARGIN = 3.0;
    private static final double CRUISE_STEP_UP = 6.0;

    private final MinecraftClient mc;

    private BlockPos currentTarget;
    private Phase phase;
    private double cruiseY;
    private Vec3d lastPos;
    private int stuckTicks;
    private int hoverTicks;
    private int retries;
    private int targetTotalTicks;
    private final Map<BlockPos, Long> skipUntilTick = new HashMap<>();
    private long tickCounter = 0;

    private boolean prevFlying;
    private boolean prevAllowFlying;

    public PlacementNavigator(MinecraftClient mc) {
        this.mc = mc;
    }

    public void start() {
        if (mc.player != null) {
            prevFlying = mc.player.getAbilities().flying;
            prevAllowFlying = mc.player.getAbilities().allowFlying;
        }
        reset();
    }

    public void stop() {
        if (mc.player != null) {
            mc.player.setVelocity(Vec3d.ZERO);
            mc.player.getAbilities().flying = prevFlying;
            mc.player.getAbilities().allowFlying = prevAllowFlying;
        }
        reset();
    }

    private void reset() {
        currentTarget = null;
        phase = null;
        lastPos = null;
        stuckTicks = 0;
        hoverTicks = 0;
        retries = 0;
        targetTotalTicks = 0;
        skipUntilTick.clear();
        tickCounter = 0;
    }

    /**
     * Advances the navigator by one tick.
     *
     * @return true once there is nothing left to do within {@code searchRadius} (caller should stop).
     */
    public boolean tick(double hoverHeight, double speed, int searchRadius) {
        ClientPlayerEntity player = mc.player;
        World world = mc.world;
        if (player == null || world == null) return false;

        tickCounter++;

        if (currentTarget != null && LitematicaCompat.isPlacedCorrectly(mc, currentTarget)) {
            skipUntilTick.remove(currentTarget);
            currentTarget = null;
        }

        cleanupExpiredSkips();

        if (currentTarget == null) {
            currentTarget = LitematicaCompat.findNearestMissingBlockPos(mc, player.getBlockPos(), searchRadius, skipUntilTick.keySet());

            if (currentTarget == null) return true; // nothing left to do within range

            phase = null;
            hoverTicks = 0;
            retries = 0;
            targetTotalTicks = 0;
        }

        targetTotalTicks++;
        if (targetTotalTicks > TARGET_WATCHDOG_TICKS) {
            skip(currentTarget);
            currentTarget = null;
            phase = null;
            return false;
        }

        Vec3d hoverTarget = new Vec3d(
            currentTarget.getX() + 0.5,
            currentTarget.getY() + hoverHeight,
            currentTarget.getZ() + 0.5
        );

        Vec3d pos = player.getPos();

        if (phase == null) phase = choosePath(world, pos, hoverTarget);

        switch (phase) {
            case DIRECT -> {
                if (flyToward(player, hoverTarget, speed)) phase = Phase.HOVER;
            }
            case ASCEND -> {
                Vec3d wp = new Vec3d(pos.x, cruiseY, pos.z);
                if (flyToward(player, wp, speed)) phase = Phase.TRAVEL;
            }
            case TRAVEL -> {
                Vec3d wp = new Vec3d(hoverTarget.x, cruiseY, hoverTarget.z);
                if (flyToward(player, wp, speed)) phase = Phase.DESCEND;
            }
            case DESCEND -> {
                if (flyToward(player, hoverTarget, speed)) phase = Phase.HOVER;
            }
            case HOVER -> {
                player.setVelocity(Vec3d.ZERO);
                hoverTicks++;

                if (hoverTicks > HOVER_TIMEOUT_TICKS) {
                    skip(currentTarget);
                    currentTarget = null;
                    phase = null;
                }
            }
        }

        // Stuck detection doesn't apply while deliberately holding still in HOVER.
        if (phase != Phase.HOVER) {
            if (lastPos != null && pos.squaredDistanceTo(lastPos) < STUCK_MOVE_EPSILON_SQ) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
            }

            if (stuckTicks > STUCK_TIMEOUT_TICKS) {
                stuckTicks = 0;
                retries++;

                if (retries > MAX_RETRIES_BEFORE_SKIP) {
                    skip(currentTarget);
                    currentTarget = null;
                    phase = null;
                } else {
                    // Small hop, then try again with a higher cruise altitude.
                    player.setVelocity(0, 0.4, 0);
                    cruiseY = Math.max(cruiseY, pos.y) + CRUISE_STEP_UP;
                    phase = Phase.ASCEND;
                }
            }
        }

        lastPos = pos;
        return false;
    }

    public BlockPos getCurrentTarget() {
        return currentTarget;
    }

    private void skip(BlockPos pos) {
        if (pos != null) skipUntilTick.put(pos, tickCounter + SKIP_DURATION_TICKS);
    }

    private void cleanupExpiredSkips() {
        skipUntilTick.entrySet().removeIf(e -> e.getValue() <= tickCounter);
    }

    /** Straight line if clear; otherwise climb to whichever cruise altitude gives a fully clear path. */
    private Phase choosePath(World world, Vec3d from, Vec3d to) {
        if (isPathClear(world, from, to)) return Phase.DIRECT;

        double baseY = Math.max(from.y, to.y);
        double hardCeiling = baseY + CRUISE_MARGIN + CRUISE_STEP_UP * MAX_RETRIES_BEFORE_SKIP;

        for (double y = baseY + CRUISE_MARGIN; y <= hardCeiling; y += CRUISE_STEP_UP) {
            Vec3d up1 = new Vec3d(from.x, y, from.z);
            Vec3d up2 = new Vec3d(to.x, y, to.z);

            if (isPathClear(world, from, up1) && isPathClear(world, up1, up2) && isPathClear(world, up2, to)) {
                cruiseY = y;
                return Phase.ASCEND;
            }
        }

        // Nothing fully clear found - use the highest altitude tried anyway as a best-effort attempt;
        // the stuck-detection retry loop above will keep climbing further if this still isn't enough.
        cruiseY = hardCeiling;
        return Phase.ASCEND;
    }

    private boolean isPathClear(World world, Vec3d from, Vec3d to) {
        double dist = from.distanceTo(to);
        if (dist < 0.1) return true;

        int steps = (int) Math.ceil(dist / 0.5);
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double x = from.x + (to.x - from.x) * t;
            double y = from.y + (to.y - from.y) * t;
            double z = from.z + (to.z - from.z) * t;

            if (isSolidAt(world, x, y, z) || isSolidAt(world, x, y + 1.5, z)) return false;
        }

        return true;
    }

    private boolean isSolidAt(World world, double x, double y, double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    /** @return true once within {@link #ARRIVE_DISTANCE} of {@code target}. */
    private boolean flyToward(ClientPlayerEntity player, Vec3d target, double speed) {
        Vec3d pos = player.getPos();
        Vec3d diff = target.subtract(pos);
        double dist = diff.length();

        if (dist < ARRIVE_DISTANCE) {
            player.setVelocity(Vec3d.ZERO);
            return true;
        }

        PlayerAbilities abilities = player.getAbilities();
        abilities.flying = true;
        if (!abilities.creativeMode) abilities.allowFlying = true;

        double moveSpeed = Math.min(speed, dist);
        player.setVelocity(diff.normalize().multiply(moveSpeed));
        player.fallDistance = 0;
        player.setOnGround(false);

        return false;
    }
}
