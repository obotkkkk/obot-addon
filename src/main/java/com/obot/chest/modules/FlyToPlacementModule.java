package com.obot.chest.modules;

import com.obot.chest.ObotAddon;
import com.obot.chest.litematica.LitematicaCompat;
import com.obot.chest.util.FlightController;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Module 3: "Fly To Placement"
 *
 * Meant for servers that tolerate flying freely (like creative mode). While active, this module keeps
 * flying toward the CLOSEST position where Litematica's schematic overlay says a block should be but
 * isn't placed correctly yet (i.e. the same ghost/preview blocks you see for your placed schematic) -
 * pairs well with a separate Litematica-print / auto-build tool: this module gets you there, the print
 * tool (or you) places the block.
 *
 * - The target position is re-checked periodically (not every tick) and updated automatically, so once
 *   the current closest block gets placed, it moves on to the next closest one by itself.
 * - "Smart" flight: flies in a straight line toward the target; if it makes no real progress for about
 *   5 seconds (e.g. flying into a wall/roof), it automatically changes course (flies up and over) for a
 *   couple of seconds before going back to a direct line. See {@link FlightController}.
 *
 * Requirements: you've placed a schematic with Litematica (loaded and enabled).
 *
 * Implementation note: unlike Litematica's own "Verify" GUI (which uses an internal, non-public
 * SchematicVerifier), this finds the target the same way the community addon "litematica-printer" does -
 * by directly comparing Litematica's virtual schematic world against the real world block-by-block
 * (see github.com/aleksilassila/litematica-printer, Printer.java / SchematicBlockState.java).
 */
public class FlyToPlacementModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Flight speed, in blocks per tick.")
        .defaultValue(0.6)
        .min(0.05)
        .sliderMin(0.05)
        .sliderMax(3)
        .build()
    );

    private final Setting<Integer> searchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("search-radius")
        .description("How far (in blocks) around you to look for the nearest missing block. Larger values can search further but cost more performance.")
        .defaultValue(32)
        .min(4)
        .sliderMin(4)
        .sliderMax(128)
        .build()
    );

    private final Setting<Double> arriveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("arrive-distance")
        .description("How close (in blocks) counts as having arrived at the target position.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderMin(0.5)
        .sliderMax(5)
        .build()
    );

    private final FlightController flight;
    private BlockPos target = null;
    private int refreshCooldown = 0;

    public FlyToPlacementModule() {
        super(ObotAddon.CATEGORY, "fly-to-placement", "Flies to the nearest position missing a block, according to Litematica's schematic overlay.");
        flight = new FlightController(mc);
    }

    @Override
    public void onActivate() {
        target = null;
        refreshCooldown = 0;
        flight.start();

        if (!LitematicaCompat.isAvailable()) {
            warning("Litematica was not found in the mods folder - this module won't do anything.");
        }
    }

    @Override
    public void onDeactivate() {
        flight.stop();
        target = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (refreshCooldown > 0) {
            refreshCooldown--;
        } else {
            // Every ~0.5s: ask Litematica for the current closest missing-block position.
            refreshCooldown = 10;
            BlockPos found = LitematicaCompat.findNearestMissingBlockPos(mc, mc.player.getBlockPos(), searchRadius.get());
            target = found; // null is fine - means nothing missing within range right now
        }

        if (target == null) return;

        Vec3d dest = Vec3d.ofCenter(target);
        double distance = flight.tick(dest, speed.get());

        if (distance <= arriveDistance.get()) {
            // Arrived - hover here. The next refresh will move on once this position is no longer
            // the closest missing block (e.g. it got placed).
            mc.player.setVelocity(Vec3d.ZERO);
        }
    }

    @Override
    public String getInfoString() {
        if (target == null) return "no target";
        if (mc.player == null) return "target found";
        return String.format("%.1fm", mc.player.getPos().distanceTo(Vec3d.ofCenter(target)));
    }
}
