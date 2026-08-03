package com.obot.chest.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import net.minecraft.item.Item;

import java.util.HashMap;
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
}
