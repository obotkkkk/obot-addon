package com.obot.chest;

import com.mojang.logging.LogUtils;
import com.vnaddon.chest.modules.AutoCollectModule;
import com.vnaddon.chest.modules.ChestTrackerModule;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class ChestAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();


    public static final Category CATEGORY = new Category("obot addon");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Obot Addon");

        Modules.get().add(new ChestTrackerModule());
        Modules.get().add(new AutoCollectModule());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.obot.chest";
    }

}
