package com.obot.chest.modules;

import com.obot.chest.ObotAddon;
import com.obot.chest.litematica.LitematicaCompat;
import com.obot.chest.util.PlacementNavigator;
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
 * Navigation is handled by {@link PlacementNavigator}: a small state machine (climb over obstacles
 * instead of flying straight into them, watchdog + skip-list so it can never get stuck forever sitting
 * on one position) based on the design found in a reference "autoflyer" addon the user provided.
 *
 * Requirements: you've placed a schematic with Litematica (loaded and enabled).
 *
 * Implementation note: the target-finding itself (as opposed to the flight/pathing) works the same way
 * the community addon "litematica-printer" does - by directly comparing Litematica's virtual schematic
 * world against the real world block-by-block (see github.com/aleksilassila/litematica-printer,
 * Printer.java / SchematicBlockState.java) - rather than driving Litematica's internal, non-public
 * SchematicVerifier.
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

    private final Setting<Double> hoverHeight = sgGeneral.add(new DoubleSetting.Builder()
        .name("hover-height")
        .description("How far above the target block (in blocks) to hover, instead of flying into the block itself.")
        .defaultValue(1.5)
        .min(0.5)
        .sliderMin(0.5)
        .sliderMax(4)
        .build()
    );

    private final PlacementNavigator navigator;

    public FlyToPlacementModule() {
        super(ObotAddon.CATEGORY, "fly-to-placement", "Flies to the nearest position missing a block, according to Litematica's schematic overlay.");
        navigator = new PlacementNavigator(mc);
    }

    @Override
    public void onActivate() {
        navigator.start();

        if (!LitematicaCompat.isAvailable()) {
            warning("Litematica was not found in the mods folder - this module won't do anything.");
        }
    }

    @Override
    public void onDeactivate() {
        navigator.stop();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        boolean nothingLeft = navigator.tick(hoverHeight.get(), speed.get(), searchRadius.get());
        if (nothingLeft) {
            info("Nothing missing within range - turning off.");
            toggle();
        }
    }

    @Override
    public String getInfoString() {
        BlockPos target = navigator.getCurrentTarget();
        if (target == null) return "no target";
        if (mc.player == null) return "target found";

        return String.format("%.1fm", mc.player.getPos().distanceTo(Vec3d.ofCenter(target)));
    }
}
