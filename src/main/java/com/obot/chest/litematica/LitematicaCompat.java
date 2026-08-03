package com.obot.chest.litematica;

import com.obot.chest.ObotAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.Collections;
import java.util.Map;

/**
 * The ONLY entry point into Litematica's data. Everywhere else in the addon must go through here -
 * never "import fi.dy.masa.litematica..." directly in another class.
 */
public final class LitematicaCompat {
    private static Boolean modLoaded = null;

    private LitematicaCompat() {
    }

    public static boolean isAvailable() {
        if (modLoaded == null) {
            modLoaded = FabricLoader.getInstance().isModLoaded("litematica");
        }
        return modLoaded;
    }

    /** See README: this is the data source for Litematica's "material list" (info-hub). */
    public static Map<Item, Integer> getMissingMaterials() {
        if (!isAvailable()) return Collections.emptyMap();

        try {
            return LitematicaAccess.getMissingMaterials();
        } catch (Throwable t) {
            // Catch every Throwable (including NoClassDefFoundError/NoSuchMethodError) so a different
            // Litematica version doesn't crash the whole addon - only this feature gets disabled.
            ObotAddon.LOG.error("[Obot Addon] Failed to read Material List from Litematica", t);
            return Collections.emptyMap();
        }
    }

    public static boolean hasActiveMaterialList() {
        if (!isAvailable()) return false;

        try {
            return LitematicaAccess.hasActiveMaterialList();
        } catch (Throwable t) {
            return false;
        }
    }

    /** See {@link LitematicaAccess#findNearestMissingBlockPos}. Returns null on any failure. */
    public static BlockPos findNearestMissingBlockPos(MinecraftClient mc, BlockPos near) {
        if (!isAvailable()) return null;

        try {
            return LitematicaAccess.findNearestMissingBlockPos(mc, near);
        } catch (Throwable t) {
            ObotAddon.LOG.error("[Obot Addon] Failed to query Litematica's schematic verifier", t);
            return null;
        }
    }
}
