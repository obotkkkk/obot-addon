package com.obot.chest.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Simple "creative-style" flight controller: every tick, sets the player's velocity to move straight
 * toward a target position at a given speed. Meant for servers that tolerate flight-like movement
 * (the same way Meteor's own Flight module's "Velocity" mode works) - it does NOT bypass server-side
 * anti-cheat on servers that don't allow flying.
 *
 * "Smart" obstacle handling: if the player makes almost no progress for ~5 seconds in a row (e.g. flying
 * straight into a wall), it temporarily biases the direction upward for a couple of seconds to try to
 * fly up and over whatever is blocking the path, then goes back to a direct line toward the target.
 */
public class FlightController {
    private static final int STUCK_TICKS_THRESHOLD = 100; // 5 seconds at 20 TPS
    private static final int AVOID_DURATION_TICKS = 40;   // ~2 seconds of "fly up and over"
    private static final double STUCK_MOVE_EPSILON = 0.05;

    private final MinecraftClient mc;

    private boolean prevFlying;
    private boolean prevAllowFlying;
    private boolean started = false;

    private Vec3d lastPos = null;
    private int stuckTicks = 0;
    private int avoidTicks = 0;

    public FlightController(MinecraftClient mc) {
        this.mc = mc;
    }

    public void start() {
        PlayerEntity player = mc.player;
        if (player == null) return;

        prevFlying = player.getAbilities().flying;
        prevAllowFlying = player.getAbilities().allowFlying;

        lastPos = null;
        stuckTicks = 0;
        avoidTicks = 0;
        started = true;
    }

    public void stop() {
        PlayerEntity player = mc.player;
        if (player == null) {
            started = false;
            return;
        }

        if (started) {
            player.setVelocity(Vec3d.ZERO);
            player.getAbilities().flying = prevFlying;
            player.getAbilities().allowFlying = prevAllowFlying;
        }

        started = false;
    }

    /**
     * Advances the flight by one tick toward {@code target} at {@code speed} blocks/tick.
     *
     * @return the current distance to the target (before this tick's movement).
     */
    public double tick(Vec3d target, double speed) {
        PlayerEntity player = mc.player;
        if (player == null) return Double.MAX_VALUE;
        if (!started) start();

        Vec3d pos = player.getPos();
        Vec3d toTarget = target.subtract(pos);
        double distance = toTarget.length();

        if (lastPos != null) {
            double moved = pos.distanceTo(lastPos);
            if (moved < STUCK_MOVE_EPSILON) stuckTicks++;
            else stuckTicks = 0;
        }
        lastPos = pos;

        if (stuckTicks >= STUCK_TICKS_THRESHOLD) {
            avoidTicks = AVOID_DURATION_TICKS;
            stuckTicks = 0;
        }

        Vec3d direction;
        if (distance < 0.0001) {
            direction = Vec3d.ZERO;
        } else if (avoidTicks > 0) {
            avoidTicks--;
            // Keep heading toward the target horizontally, but force a strong upward component so we
            // climb above whatever is blocking a direct line (walls, roofs, terrain, ...).
            direction = new Vec3d(toTarget.x, Math.max(toTarget.y, distance * 0.6 + 1.0), toTarget.z).normalize();
        } else {
            direction = toTarget.normalize();
        }

        player.getAbilities().flying = true;
        if (!player.getAbilities().creativeMode) player.getAbilities().allowFlying = true;

        player.setVelocity(direction.multiply(speed));
        player.fallDistance = 0;
        player.setOnGround(false);

        return distance;
    }

    public boolean isAvoiding() {
        return avoidTicks > 0;
    }
}
