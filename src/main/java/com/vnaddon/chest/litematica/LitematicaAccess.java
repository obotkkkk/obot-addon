package com.vnaddon.chest.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * Class nay dong cham truc tiep den cac class cua Litematica (DataManager, MaterialListBase, ...).
 * KHONG duoc goi truc tiep tu bat ky noi nao khac ngoai {@link LitematicaCompat} - vi JVM chi thuc su
 * nap (verify) class nay khi mot method cua no duoc goi lan dau. Nho vay, neu nguoi dung khong cai
 * Litematica, class nay se khong bao gio duoc dong, va addon van chay binh thuong (khong crash).
 */
final class LitematicaAccess {

    private LitematicaAccess() {
    }

    /**
     * Lay danh sach nguyen lieu con thieu tu Material List dang active cua Litematica.
     * Material List nay chinh la cai hien trong info-hub/material-list cua schematic dang duoc dat.
     *
     * @return Map[Item -> so luong con thieu]. Rong neu chua co material list nao (chua load schematic
     *         hoac chua bam "Create material list").
     */
    static Map<Item, Integer> getMissingMaterials() {
        Map<Item, Integer> result = new HashMap<>();

        MaterialListBase list = DataManager.getInstance().getMaterialList();
        if (list == null) return result;

        // getMaterialsMissingOnly() tra ve cac entry ma so luong thieu (countMissing) > 0.
        // Neu ban dang dung mot ban Litematica hoi khac ve API, hay thu getMaterialsAll() va tu loc
        // theo entry.getCountMissing() > 0 thay the.
        for (MaterialListEntry entry : list.getMaterialsMissingOnly()) {
            int missing = entry.getCountMissing();
            if (missing <= 0) continue;

            Item item = entry.getStack().getItem();
            result.merge(item, missing, Integer::sum);
        }

        return result;
    }

    /** True neu Litematica hien co material list dang active (da tao tu 1 schematic). */
    static boolean hasActiveMaterialList() {
        return DataManager.getInstance().getMaterialList() != null;
    }
}
