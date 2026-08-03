package com.obot.chest;

import com.mojang.logging.LogUtils;
import com.obot.chest.modules.AutoCollectModule;
import com.obot.chest.modules.ChestTrackerModule;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class ObotAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();

    // Custom category shown in the Meteor module list (next to Combat/Render/...).
    public static final Category CATEGORY = new Category("obot");

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

    // No public Github repo configured, so getRepo() is not overridden - the addon still works fine,
    // Meteor just won't be able to auto-check for updates.
}
