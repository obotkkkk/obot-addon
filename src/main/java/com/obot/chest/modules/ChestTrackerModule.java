package com.obot.chest.modules;

import com.obot.chest.ObotAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.*;

/**
 * Module 1: "Chest Tracker"
 * - Remembers (in memory only, nothing saved to disk) the items that were in each chest the player
 *   opened.
 * - Opened chests get an outline "glow" rendered in the world, including BOTH halves of a double chest.
 * - Data only exists while the module is ON. Toggling it off, or leaving/rejoining the world, wipes it
 *   (reset).
 */
public class ChestTrackerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> onlyChests = sgGeneral.add(new BoolSetting.Builder()
        .name("chests-only")
        .description("Only track Chest/Trapped Chest/Barrel/Shulker Box (ignores Ender Chest, hoppers, furnaces, etc).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color of opened chests.")
        .defaultValue(new SettingColor(255, 225, 0, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("fill-color")
        .description("Fill color of opened chests.")
        .defaultValue(new SettingColor(255, 225, 0, 60))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Render lines, fill, or both.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    // pos -> items that were in the chest as of the last update
    private final Map<BlockPos, List<ItemStack>> chestContents = new HashMap<>();
    // Every position that has ever been "opened" - used for the glow render (includes both halves
    // of a double chest).
    private final Set<BlockPos> openedPositions = new HashSet<>();

    // The chest the player JUST right-clicked, used to attach the contents once the screen opens
    // right after.
    private BlockPos pendingPrimary = null;
    private BlockPos pendingSecondary = null;

    public ChestTrackerModule() {
        super(ObotAddon.CATEGORY, "chest-tracker", "Tracks + glows opened chests and remembers what's inside them.");
    }

    @Override
    public void onActivate() {
        reset();
    }

    @Override
    public void onDeactivate() {
        reset();
    }

    private void reset() {
        chestContents.clear();
        openedPositions.clear();
        pendingPrimary = null;
        pendingSecondary = null;
    }

    /** Lets AutoCollectModule read the data without exposing the whole field publicly. */
    public Map<BlockPos, List<ItemStack>> getChestContents() {
        return chestContents;
    }

    /** Lets AutoCollectModule know which positions have been opened/tracked (for double-chest glow too). */
    public Set<BlockPos> getOpenedPositions() {
        return openedPositions;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    // Step 1: when the player right-clicks a block, remember the position (and the other half if it's
    // a double chest).
    @EventHandler
    private void onInteractBlock(InteractBlockEvent event) {
        if (mc.world == null) return;

        BlockPos pos = event.result.getBlockPos();
        BlockEntity blockEntity = mc.world.getBlockEntity(pos);
        if (blockEntity == null) return;

        pendingPrimary = pos;
        pendingSecondary = null;

        BlockState state = mc.world.getBlockState(pos);
        if ((state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)) && state.contains(ChestBlock.CHEST_TYPE)) {
            ChestType chestType = state.get(ChestBlock.CHEST_TYPE);

            if (chestType != ChestType.SINGLE) {
                Direction facing = state.get(ChestBlock.FACING);
                Direction offset = chestType == ChestType.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
                pendingSecondary = pos.offset(offset);
            }
        }

        openedPositions.add(pos);
        if (pendingSecondary != null) openedPositions.add(pendingSecondary);
    }

    // Step 2: every tick while the chest screen is open, refresh the contents snapshot (items can
    // still be syncing from the server for the first tick or two, so continuously overwriting until
    // the screen closes is the safest option).
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pendingPrimary == null) return;
        if (mc.currentScreen == null) {
            // Screen closed, nothing left to update for this interaction.
            pendingPrimary = null;
            pendingSecondary = null;
            return;
        }

        List<ItemStack> stacks = readOpenContainerStacks();
        if (stacks == null) return; // the open screen isn't a container type we know how to read

        List<ItemStack> snapshot = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) snapshot.add(s.copy());

        chestContents.put(pendingPrimary, snapshot);
        if (pendingSecondary != null) {
            // For double chests, Minecraft merges both halves into a single 54-slot inventory - to
            // keep things simple and avoid double-counting items when aggregating materials, we store
            // the same snapshot under both positions and only count it once in AutoCollectModule.
            chestContents.put(pendingSecondary, snapshot);
        }
    }

    /** Reads the slots of the currently open container (only supports Chest/Trapped Chest/Barrel/Shulker Box). */
    private List<ItemStack> readOpenContainerStacks() {
        ScreenHandler handler = mc.player != null ? mc.player.currentScreenHandler : null;
        if (handler == null) return null;

        int containerSlotCount;
        if (handler instanceof GenericContainerScreenHandler generic && mc.currentScreen instanceof GenericContainerScreen) {
            containerSlotCount = generic.getRows() * 9;
        } else if (handler instanceof ShulkerBoxScreenHandler && mc.currentScreen instanceof ShulkerBoxScreen) {
            containerSlotCount = 27;
        } else {
            return null;
        }

        List<ItemStack> result = new ArrayList<>(containerSlotCount);
        List<Slot> slots = handler.slots;
        for (int i = 0; i < containerSlotCount && i < slots.size(); i++) {
            result.add(slots.get(i).getStack());
        }
        return result;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        for (BlockPos pos : openedPositions) {
            if (onlyChests.get() && mc.world != null) {
                BlockState state = mc.world.getBlockState(pos);
                boolean isTracked = state.isOf(Blocks.CHEST) || state.isOf(Blocks.TRAPPED_CHEST)
                    || state.isOf(Blocks.BARREL)
                    || state.getBlock() instanceof ShulkerBoxBlock;
                if (!isTracked) continue;
            }

            double x1 = pos.getX(), y1 = pos.getY(), z1 = pos.getZ();
            double x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1;

            event.renderer.box(x1, y1, z1, x2, y2, z2, fillColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    @Override
    public String getInfoString() {
        return chestContents.size() + " chests";
    }
}
