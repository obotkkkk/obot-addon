package com.obot.chest.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListUtils;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class directly touches Litematica's classes (DataManager, MaterialListBase, ...).
 * It must NEVER be referenced from anywhere except {@link LitematicaCompat} - the JVM only actually
 * loads/verifies this class the first time one of its methods is called. That way, if the user doesn't
 * have Litematica installed, this class is simply never loaded and the addon keeps working fine
 * (no crash from missing classes).
 */
final class LitematicaAccess {

    private LitematicaAccess() {
    }

    /**
     * Reads the missing materials from Litematica's currently active Material List, computed against
     * the player's CURRENT inventory.
     *
     * IMPORTANT: {@code MaterialListEntry.getCountMissing()} is a plain stored field - it is set once
     * when the material list is (re)created and is NOT recomputed automatically as items enter your
     * inventory (confirmed by decompiling Litematica: {@code getMaterialsMissingOnly(refresh)} only
     * re-filters the cached list, it never rescans anything). Relying on it directly was the cause of
     * auto-collect over-collecting - it kept seeing the same stale "missing" amount no matter how much
     * had already been picked up, so it just kept taking matching items.
     *
     * The fix: call {@code MaterialListUtils.updateAvailableCounts(entries, player)} - the same public
     * method Litematica's own GUI uses - to refresh each entry's "available" count against the player's
     * CURRENT inventory, then compute missing ourselves as {@code total - available} instead of trusting
     * the stale field.
     *
     * @return Map[Item -> amount still missing]. Empty if there's no active material list yet.
     */
    static Map<Item, Integer> getMissingMaterials(MinecraftClient mc) {
        Map<Item, Integer> result = new HashMap<>();
        if (mc.player == null) return result;

        MaterialListBase list = DataManager.getInstance().getMaterialList();
        if (list == null) return result;

        List<MaterialListEntry> entries = list.getMaterialsAll();
        MaterialListUtils.updateAvailableCounts(entries, mc.player);

        for (MaterialListEntry entry : entries) {
            int missing = entry.getCountTotal() - entry.getCountAvailable();
            if (missing <= 0) continue;

            Item item = entry.getStack().getItem();
            result.merge(item, missing, Integer::sum);
        }

        return result;
    }

    /** True if Litematica currently has an active material list (created from a loaded schematic). */
    static boolean hasActiveMaterialList() {
        return DataManager.getInstance().getMaterialList() != null;
    }

    /**
     * True if the real world at {@code pos} already matches what the schematic wants there (or the
     * schematic doesn't want anything there - air). Used by {@link com.obot.chest.util.PlacementNavigator}
     * to know when its current target is actually done and it should look for a new one.
     */
    static boolean isPlacedCorrectly(MinecraftClient mc, BlockPos pos) {
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null || mc.world == null) return true;

        BlockState targetState = schematicWorld.getBlockState(pos);
        if (targetState.isAir()) return true;

        return targetState.equals(mc.world.getBlockState(pos));
    }

    /**
     * Finds the position closest to {@code center} (within {@code maxRadius} blocks, excluding anything
     * in {@code skip}) that Litematica's schematic overlay says should have a block, but the real world
     * doesn't match yet - i.e. the same ghost/preview blocks you see rendered for your placed schematic(s).
     *
     * This mirrors exactly how the community addon "litematica-printer" finds its next block to place
     * (github.com/aleksilassila/litematica-printer, see Printer.java / SchematicBlockState.java):
     * Litematica maintains a {@link WorldSchematic} - a virtual World that already combines every active
     * schematic placement into real world coordinates (it's what powers the ghost-block rendering) - so
     * we simply compare {@code schematicWorld.getBlockState(pos)} (what SHOULD be there) against
     * {@code mc.world.getBlockState(pos)} (what IS there) for each position, instead of driving
     * Litematica's internal (and non-public) SchematicVerifier ourselves.
     *
     * Positions are checked in expanding "shells" outward from {@code center} (radius 0, then 1, then 2,
     * ...) so it can return as soon as it finds a match in the closest shell, instead of always scanning
     * the entire {@code maxRadius} cube. {@code skip} lets the caller exclude positions it has already
     * given up on for now (see {@link com.obot.chest.util.PlacementNavigator}'s skip-list/watchdog logic -
     * without this, a target that can't actually be completed for some reason would be found as "nearest"
     * forever and the navigator would get stuck sitting on top of it).
     *
     * @return the closest position needing a block, or {@code null} if none was found within range (or
     *         there's no schematic loaded at all).
     */
    static BlockPos findNearestMissingBlockPos(MinecraftClient mc, BlockPos center, int maxRadius, Set<BlockPos> skip) {
        WorldSchematic schematicWorld = SchematicWorldHandler.getSchematicWorld();
        if (schematicWorld == null || mc.world == null) return null;

        for (int r = 0; r <= maxRadius; r++) {
            BlockPos best = null;
            double bestDistSq = Double.MAX_VALUE;

            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    for (int z = -r; z <= r; z++) {
                        // Only look at the surface of this shell - closer positions were already
                        // checked (and would have returned) on a previous, smaller value of r.
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) != r) continue;

                        BlockPos pos = center.add(x, y, z);
                        if (skip.contains(pos)) continue;

                        BlockState targetState = schematicWorld.getBlockState(pos);
                        if (targetState.isAir()) continue;

                        BlockState currentState = mc.world.getBlockState(pos);
                        if (targetState.equals(currentState)) continue;

                        double distSq = (double) x * x + (double) y * y + (double) z * z;
                        if (distSq < bestDistSq) {
                            bestDistSq = distSq;
                            best = pos;
                        }
                    }
                }
            }

            if (best != null) return best;
        }

        return null;
    }
}
