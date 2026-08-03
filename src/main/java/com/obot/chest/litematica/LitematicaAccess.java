package com.obot.chest.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementManager;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * Reads the missing materials from Litematica's currently active Material List.
     * This is the exact same Material List shown in Litematica's info-hub / material list screen for
     * the schematic you currently have placed.
     *
     * @return Map[Item -> amount still missing]. Empty if there's no active material list yet (no
     *         schematic loaded, or "Create material list" hasn't been pressed yet).
     */
    static Map<Item, Integer> getMissingMaterials() {
        Map<Item, Integer> result = new HashMap<>();

        MaterialListBase list = DataManager.getInstance().getMaterialList();
        if (list == null) return result;

        // getMaterialsMissingOnly(boolean refresh) returns entries where countMissing > 0.
        // refresh=true makes sure we always read the latest numbers (matching what the HUD shows).
        for (MaterialListEntry entry : list.getMaterialsMissingOnly(true)) {
            int missing = entry.getCountMissing();
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
     * Finds the position closest to {@code near} that, according to Litematica's schematic verifier,
     * should have a block placed there but currently doesn't (MismatchType.MISSING).
     *
     * This is the same underlying data Litematica's own "Verify" feature (GuiSchematicVerifier) uses.
     * The very first call for a given placement kicks off verification (a chunk-by-chunk scan) which
     * takes a bit of time to finish; this method returns {@code null} until it's ready, and the caller
     * is expected to just keep calling it again on later ticks.
     *
     * NOTE: this is the most speculative part of the Litematica integration - it relies on internal
     * verifier machinery that is normally only driven from Litematica's own GUI, not documented for
     * external use. If it misbehaves, check the log for the exact error and adjust here.
     */
    static BlockPos findNearestMissingBlockPos(MinecraftClient mc, BlockPos near) {
        SchematicPlacementManager placementManager = DataManager.getInstance().getSchematicPlacementManager();
        if (placementManager == null) return null;

        List<SchematicPlacement> placements = placementManager.getAllSchematicsPlacements();
        if (placements == null || placements.isEmpty()) return null;

        for (SchematicPlacement placement : placements) {
            if (!placement.isEnabled()) continue;

            SchematicVerifier verifier = placement.getSchematicVerifier();
            if (verifier == null) continue;

            if (!verifier.isActive() && !verifier.isFinished()) {
                // Not verified yet - kick off a scan and try again on a later tick.
                verifier.startVerification(mc.world, SchematicWorldHandler.INSTANCE.getSchematicWorld(), placement, () -> {});
                continue;
            }

            if (verifier.isActive() && !verifier.isFinished()) continue; // still scanning chunks

            verifier.updateClosestPositions(near, 5);
            List<BlockPos> closest = verifier.getClosestMismatchedPositionsFor(SchematicVerifier.MismatchType.MISSING);
            if (closest != null && !closest.isEmpty()) return closest.get(0);
        }

        return null;
    }
}
