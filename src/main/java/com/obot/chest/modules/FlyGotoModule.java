package com.obot.chest.modules;

import com.obot.chest.ObotAddon;
import com.obot.chest.util.FlightController;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

/**
 * Module 4: "Fly Goto"
 *
 * Flies straight to the X/Y/Z coordinates entered in the settings, at the given speed. Same "smart"
 * obstacle handling as {@link FlyToPlacementModule} - see {@link FlightController}.
 */
public class FlyGotoModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> x = sgGeneral.add(new DoubleSetting.Builder()
        .name("x")
        .description("Target X coordinate.")
        .defaultValue(0.0)
        .build()
    );

    private final Setting<Double> y = sgGeneral.add(new DoubleSetting.Builder()
        .name("y")
        .description("Target Y coordinate.")
        .defaultValue(64.0)
        .build()
    );

    private final Setting<Double> z = sgGeneral.add(new DoubleSetting.Builder()
        .name("z")
        .description("Target Z coordinate.")
        .defaultValue(0.0)
        .build()
    );

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("speed")
        .description("Flight speed, in blocks per tick.")
        .defaultValue(0.6)
        .min(0.05)
        .sliderMin(0.05)
        .sliderMax(3)
        .build()
    );

    private final Setting<Double> arriveDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("arrive-distance")
        .description("How close (in blocks) counts as having arrived.")
        .defaultValue(1.0)
        .min(0.2)
        .sliderMin(0.2)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> autoDisableOnArrival = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable-on-arrival")
        .description("Automatically turns this module off once you arrive at the target.")
        .defaultValue(true)
        .build()
    );

    private final FlightController flight;

    public FlyGotoModule() {
        super(ObotAddon.CATEGORY, "fly-goto", "Flies straight to the given X/Y/Z coordinates.");
        flight = new FlightController(mc);
    }

    @Override
    public void onActivate() {
        flight.start();
    }

    @Override
    public void onDeactivate() {
        flight.stop();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        Vec3d dest = new Vec3d(x.get(), y.get(), z.get());
        double distance = flight.tick(dest, speed.get());

        if (distance <= arriveDistance.get()) {
            mc.player.setVelocity(Vec3d.ZERO);

            if (autoDisableOnArrival.get()) {
                info("Arrived.");
                toggle();
            }
        }
    }

    @Override
    public String getInfoString() {
        if (mc.player == null) return "";
        return String.format("%.1fm", mc.player.getPos().distanceTo(new Vec3d(x.get(), y.get(), z.get())));
    }
}
