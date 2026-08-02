package com.vnaddon.chest.litematica;

import com.vnaddon.chest.ChestAddon;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;

import java.util.Collections;
import java.util.Map;

/**
 * Diem truy cap DUY NHAT vao du lieu cua Litematica. Moi noi khac trong addon phai goi qua day,
 * khong duoc "import fi.dy.masa.litematica..." truc tiep o cac class khac.
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

    /** Xem README: day chinh la nguon du lieu cho "material list" (info-hub) cua Litematica. */
    public static Map<Item, Integer> getMissingMaterials() {
        if (!isAvailable()) return Collections.emptyMap();

        try {
            return LitematicaAccess.getMissingMaterials();
        } catch (Throwable t) {
            // Bat het Throwable (ke ca NoClassDefFoundError/NoSuchMethodError) de 1 ban Litematica
            // khac version khong lam crash toan bo addon - chi tinh nang lay item bi tat tam thoi.
            ChestAddon.LOG.error("[VNChestAddon] Khong doc duoc Material List tu Litematica", t);
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
}
