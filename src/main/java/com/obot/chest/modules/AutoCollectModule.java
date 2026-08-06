package com.obot.chest.modules;

import com.obot.chest.ObotAddon;
import com.obot.chest.litematica.LitematicaCompat;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Module 2: "Auto Collect"
 *
 * While active, this module:
 *  1. Looks for nearby Chest / Trapped Chest / Barrel / Shulker Box blocks within {@code search-range}
 *     and automatically right-clicks (opens) them itself - you no longer need to open chests by hand.
 *  2. Once a container screen is open (whether it opened it, or you opened one manually), it shift-clicks
 *     every item stack that matches something still MISSING according to Litematica's active Material
 *     List (the same one shown in the info-hub / material list screen for your placed schematic).
 *     Anything not on the missing list is skipped entirely.
 *  3. Once a chest has nothing left to take, it closes the screen and moves on to the next nearby chest.
 *  4. Stops taking items when your inventory is full, and (optionally) turns itself off once nothing is
 *     missing anymore.
 *
 * Requirements to actually do anything:
 *  - You've placed a schematic with Litematica and created its Material List at least once (open the
 *    Material List screen, or press "Create material list").
 *  - You are standing within reach of at least one Chest/Trapped Chest/Barrel/Shulker Box.
 *
 * SCOPE NOTE: this module does NOT move/pathfind your character to chests that are further away - you
 * still need to walk within range yourself. Once you're close enough, it takes care of opening the
 * chest and looting it for you.
 */
public class AutoCollectModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoOpen = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-open-chests")
        .description("Automatically right-click nearby chests instead of waiting for you to open one by hand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> searchRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("search-range")
        .description("How far around you (in blocks) to look for chests to auto-open.")
        .defaultValue(4.5)
        .min(1)
        .max(6)
        .sliderMin(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> stopWhenFull = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-inventory-full")
        .description("Stop taking items once your hotbar + main inventory has no empty slots left.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDisableWhenDone = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-disable-when-done")
        .description("Automatically turn OFF this module once Litematica reports nothing is missing anymore.")
        .defaultValue(false)
        .build()
    );

    // Small cooldown so we don't spam interact/click packets across ticks.
    private int cooldown = 0;

    // Position of the chest we are CURRENTLY looting (either we opened it, or the player did).
    private BlockPos currentTarget = null;

    // Positions we've already fully looted this session - skipped so we don't just reopen them forever.
    private final Set<BlockPos> doneChests = new HashSet<>();

    // Our own running total of how much of each item is still needed. Litematica's own "missing" count
    // doesn't necessarily update the instant we take an item out of a chest (it may only refresh
    // periodically), so relying on it alone would keep letting us take stacks of an item we've actually
    // already collected enough of - this local copy is decremented immediately after every shift-click,
    // and is what actually gates whether we take more of that item. It's re-synced from Litematica every
    // REFRESH_INTERVAL_TICKS so it never drifts far from the truth (e.g. if requirements change because
    // the schematic placement moved).
    private final Map<Item, Integer> remainingNeeded = new HashMap<>();
    private int refreshCooldown = 0;
    private static final int REFRESH_INTERVAL_TICKS = 40; // 2 seconds

    public AutoCollectModule() {
        super(ObotAddon.CATEGORY, "auto-collect", "Auto-opens nearby chests and takes whatever materials Litematica's Material List is still missing.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        doneChests.clear();
        remainingNeeded.clear();
        cooldown = 0;
        refreshCooldown = 0;

        if (!LitematicaCompat.isAvailable()) {
            warning("Litematica was not found in the mods folder - this module won't do anything.");
        } else if (!LitematicaCompat.hasActiveMaterialList()) {
            warning("Litematica doesn't have an active Material List yet. Load a schematic and create its material list first.");
        }
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        doneChests.clear();
        remainingNeeded.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        if (refreshCooldown <= 0) {
            refreshCooldown = REFRESH_INTERVAL_TICKS;
            // Re-sync from Litematica. We deliberately OVERWRITE rather than merge: this is what lets a
            // freshly-confirmed lower number (Litematica catching up after we took some items) actually
            // stick, instead of the stale higher number lingering forever.
            remainingNeeded.clear();
            remainingNeeded.putAll(LitematicaCompat.getMissingMaterials(mc));
        }

        if (remainingNeeded.isEmpty()) {
            if (currentTarget != null && mc.currentScreen != null) closeCurrentScreen();

            if (autoDisableWhenDone.get() && LitematicaCompat.hasActiveMaterialList()) {
                info("Nothing missing according to the Material List - turning off.");
                toggle();
            }
            return;
        }

        // Case 1: we (or the player) currently have a supported container screen open -> loot it.
        ScreenHandler handler = mc.player.currentScreenHandler;
        int containerSlotCount = mc.currentScreen != null ? getContainerSlotCount(handler) : -1;

        if (containerSlotCount > 0) {
            if (stopWhenFull.get() && isInventoryFull()) return;

            boolean tookSomething = tryTakeOneStack(handler, containerSlotCount);
            if (tookSomething) {
                cooldown = 2; // give the inventory/server a moment to sync before the next click
                return;
            }

            // Nothing left worth taking in this chest - mark it done and close it so we can move on.
            if (currentTarget != null) doneChests.add(currentTarget);
            closeCurrentScreen();
            cooldown = 4;
            return;
        }

        // Case 2: no relevant screen open - try to auto-open a nearby chest.
        if (mc.currentScreen != null) return; // some other, unrelated screen is open (inventory, chat, etc.)
        if (!autoOpen.get()) return;
        if (stopWhenFull.get() && isInventoryFull()) return;

        BlockPos target = findNearestChest();
        if (target == null) return;

        openChest(target);
        cooldown = 4; // wait a few ticks for the server to actually open the screen
    }

    private BlockPos findNearestChest() {
        Vec3d playerPos = mc.player.getPos();
        double range = searchRange.get();
        int r = (int) Math.ceil(range);

        BlockPos playerBlockPos = mc.player.getBlockPos();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerBlockPos.add(x, y, z);
                    if (doneChests.contains(pos)) continue;

                    double distSq = Vec3d.ofCenter(pos).squaredDistanceTo(playerPos);
                    if (distSq > range * range) continue;

                    if (!isEligibleChest(mc.world.getBlockState(pos))) continue;

                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = pos;
                    }
                }
            }
        }

        return best;
    }

    private boolean isEligibleChest(BlockState state) {
        return state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)
            || state.isOf(Blocks.BARREL)
            || state.getBlock() instanceof ShulkerBoxBlock;
    }

    private void openChest(BlockPos pos) {
        currentTarget = pos;

        BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        BlockUtils.interact(hitResult, Hand.MAIN_HAND, true);
    }

    private void closeCurrentScreen() {
        if (mc.player != null) mc.player.closeHandledScreen();
        currentTarget = null;
    }

    /**
     * Shift-clicks the first container slot that matches an item we still need at least 1 more of, and
     * immediately decrements our local {@link #remainingNeeded} tracker by however much that stack had
     * (clamped so it can't go negative-and-still-count). Returns true if it took something.
     */
    private boolean tryTakeOneStack(ScreenHandler handler, int containerSlotCount) {
        List<Slot> slots = handler.slots;

        for (int i = 0; i < containerSlotCount && i < slots.size(); i++) {
            ItemStack stack = slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            Integer need = remainingNeeded.get(item);
            if (need == null || need <= 0) continue; // not something we still need -> skip entirely

            InvUtils.shiftClick().slotId(i);
            remainingNeeded.put(item, need - stack.getCount());
            return true;
        }

        return false;
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
        // 9 hotbar + 27 main = 36 slots, armor/offhand not counted.
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public String getInfoString() {
        if (!LitematicaCompat.isAvailable()) return "no litematica";
        Map<Item, Integer> missing = LitematicaCompat.getMissingMaterials(mc);
        return missing.size() + " missing";
    }
}
