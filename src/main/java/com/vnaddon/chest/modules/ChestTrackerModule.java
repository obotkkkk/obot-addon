package com.vnaddon.chest.modules;

import com.vnaddon.chest.ChestAddon;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
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
 * - Ghi lai (luu tam thoi, chi trong RAM) item hien co trong tung ruong ma nguoi choi mo ra.
 * - Ruong da mo se duoc "phat sang" (ve outline mau) tren the gioi, ke ca 2 nua cua ruong doi.
 * - Du lieu chi ton tai khi module dang BAT. Tat module hoac roi/vao lai the gioi se xoa sach (reset).
 */
public class ChestTrackerModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> onlyChests = sgGeneral.add(new BoolSetting.Builder()
        .name("chi-tinh-ruong")
        .description("Chi theo doi Chest/Trapped Chest/Barrel/Shulker Box (bo qua Ender Chest, lo, furnace...).")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("mau-vien")
        .description("Mau vien cua ruong da mo.")
        .defaultValue(new SettingColor(255, 225, 0, 255))
        .build()
    );

    private final Setting<SettingColor> fillColor = sgRender.add(new ColorSetting.Builder()
        .name("mau-fill")
        .description("Mau to (fill) cua ruong da mo.")
        .defaultValue(new SettingColor(255, 225, 0, 60))
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
        .name("kieu-ve")
        .description("Ve line/fill/ca hai.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    // pos -> danh sach item hien co trong ruong tai thoi diem cuoi cung duoc cap nhat
    private final Map<BlockPos, List<ItemStack>> chestContents = new HashMap<>();
    // Tat ca vi tri "da tung mo" - dung de ve glow (bao gom ca 2 nua ruong doi)
    private final Set<BlockPos> openedPositions = new HashSet<>();

    // Vi tri cua ruong nguoi choi VUA rIGHT-CLICK, dung de gan noi dung khi man hinh mo len ngay sau do
    private BlockPos pendingPrimary = null;
    private BlockPos pendingSecondary = null;

    public ChestTrackerModule() {
        super(ChestAddon.CATEGORY, "chest-tracker", "Theo doi + phat sang cac ruong da mo, luu lai item ben trong.");
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

    /** Cho AutoCollectModule doc du lieu ma khong can public hoa toan bo field. */
    public Map<BlockPos, List<ItemStack>> getChestContents() {
        return chestContents;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        reset();
    }

    // Buoc 1: khi nguoi choi right-click vao 1 block, ghi nho vi tri (va nua con lai neu la ruong doi)
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

    // Buoc 2: moi tick trong khi man hinh ruong dang mo, cap nhat lai noi dung (item co the con dang
    // sync tu server vai tick dau, nen cu ghi de lien tuc cho toi khi dong man hinh la an toan nhat)
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pendingPrimary == null) return;
        if (mc.currentScreen == null) {
            // man hinh da dong, khong con gi de cap nhat cho tuong tac nay nua
            pendingPrimary = null;
            pendingSecondary = null;
            return;
        }

        List<ItemStack> stacks = readOpenContainerStacks();
        if (stacks == null) return; // man hinh dang mo khong phai la 1 container ma minh biet doc

        List<ItemStack> snapshot = new ArrayList<>(stacks.size());
        for (ItemStack s : stacks) snapshot.add(s.copy());

        chestContents.put(pendingPrimary, snapshot);
        if (pendingSecondary != null) {
            // Voi ruong doi, Minecraft gop chung 1 inventory 54 o cho ca 2 nua - de don gian va tranh
            // dem trung item khi tong hop nguyen lieu, ta luu chung 1 snapshot cho ca 2 vi tri va danh
            // dau o AutoCollectModule bang cach chi lay item 1 lan (xem AutoCollectModule).
            chestContents.put(pendingSecondary, snapshot);
        }
    }

    /** Doc slot cua container hien dang mo (chi ho tro Chest/Trapped Chest/Barrel/Shulker Box). */
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
        return chestContents.size() + " ruong";
    }
}
