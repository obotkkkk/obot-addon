package com.vnaddon.chest;

import com.mojang.logging.LogUtils;
import com.vnaddon.chest.modules.AutoCollectModule;
import com.vnaddon.chest.modules.ChestTrackerModule;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class ChestAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // Danh muc rieng cho addon, se hien trong menu Meteor (phia ben trai, cung hang voi Combat/Render/...)
    public static final Category CATEGORY = new Category("obot");

    @Override
    public void onInitialize() {
        LOG.info("Initializing VN Chest Addon");

        Modules.get().add(new ChestTrackerModule());
        Modules.get().add(new AutoCollectModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.vnaddon.chest";
    }

    // Khong co repo Github rieng nen khong override getRepo() - addon van hoat dong binh thuong,
    // chi la Meteor se khong the tu check update cho addon nay.
}
