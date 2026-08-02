package com.vnaddon.chest.modules;

import com.vnaddon.chest.ChestAddon;
import com.vnaddon.chest.litematica.LitematicaCompat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.Map;

/**
 * Module 2: "Auto Collect"
 * Khi ban dung mo mot ruong (nen la ruong dang phat sang boi ChestTrackerModule), module nay se tu
 * dong shift-click nhung item con THIEU theo Material List cua Litematica (info-hub / material list
 * cua schematic dang dat) vao tui do. Item khong nam trong danh sach thieu se duoc bo qua hoan toan.
 *
 * Dieu kien de hoat dong:
 *  - Ban da dat 1 schematic bang Litematica va da bam "Create material list" (hoac mo man hinh
 *    Material List it nhat 1 lan) de Litematica tao du lieu material list.
 *  - Dang mo 1 man hinh Chest / Trapped Chest / Barrel / Shulker Box.
 *
 * LUU Y VE PHAM VI: module nay KHONG tu dong di chuyen nhan vat den tung ruong (khong co pathfinding/
 * Baritone di kem). Ban van phai tu di toi va mo tung ruong (uu tien nhung ruong dang phat sang tu
 * ChestTrackerModule) - module se tu dong "hut" dung nguyen lieu con thieu moi lan ban mo ruong ra.
 */
public class AutoCollectModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> stopWhenFull = sgGeneral.add(new BoolSetting.Builder()
        .name("dung-khi-day-tui")
        .description("Tu dung lay them item khi tui do (hotbar + inventory chinh) khong con cho trong.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDisableWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("tu-tat-khi-du-nguyen-lieu")
        .description("Tu dong TAT module khi Litematica bao khong con thieu nguyen lieu nao nua.")
        .defaultValue(false)
        .build()
    );

    // Cooldown nho de khong spam click lien tuc trong cung 1 tick / giua cac lan doc lai material list
    private int cooldown = 0;

    public AutoCollectModule() {
        super(ChestAddon.CATEGORY, "auto-collect", "Tu dong lay nguyen lieu con thieu (theo Material List cua Litematica) khi mo ruong.");
    }

    @Override
    public void onActivate() {
        if (!LitematicaCompat.isAvailable()) {
            warning("Khong tim thay Litematica trong mods folder - module se khong lam gi ca.");
        } else if (!LitematicaCompat.hasActiveMaterialList()) {
            warning("Litematica chua co Material List nao dang active. Hay load 1 schematic va tao material list truoc.");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (mc.player == null || mc.currentScreen == null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlotCount = getContainerSlotCount(handler);
        if (containerSlotCount <= 0) return; // man hinh dang mo khong phai la chest/barrel/shulker ma minh ho tro

        Map<Item, Integer> missing = LitematicaCompat.getMissingMaterials();
        if (missing.isEmpty()) {
            if (autoDisableWhenDone.get() && LitematicaCompat.hasActiveMaterialList()) {
                info("Da du nguyen lieu theo Material List - tu tat module.");
                toggle();
            }
            return;
        }

        if (stopWhenFull.get() && isInventoryFull()) return;

        java.util.List<Slot> slots = handler.slots;
        for (int i = 0; i < containerSlotCount && i < slots.size(); i++) {
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            Integer need = missing.get(item);
            if (need == null || need <= 0) continue; // khong phai nguyen lieu dang thieu -> bo qua

            // Shift-click nguyen ca stack nay vao inventory cua nguoi choi.
            InvUtils.shiftClick().slotId(i);

            cooldown = 2; // doi vai tick de server/inventory sync truoc khi doc lai
            return; // moi tick chi lay 1 stack, tranh spam qua nhieu packet cung luc
        }
    }

    private int getContainerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler generic && mc.currentScreen instanceof GenericContainerScreen) {
            return generic.getRows() * 9;
        }
        if (handler instanceof ShulkerBoxScreenHandler && mc.currentScreen instanceof ShulkerBoxScreen) {
            return 27;
        }
        return -1;
    }

    private boolean isInventoryFull() {
        // 9 hotbar + 27 main = 36 o, khong tinh armor/offhand
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public String getInfoString() {
        if (!LitematicaCompat.isAvailable()) return "no litematica";
        Map<Item, Integer> missing = LitematicaCompat.getMissingMaterials();
        return missing.size() + " loai thieu";
    }
}
