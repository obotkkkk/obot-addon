# Obot Addon (Meteor Client, Fabric 1.21.4)

Addon for [Meteor Client](https://meteorclient.com/) based on [meteor-addon-template](https://github.com/MeteorDevelopment/meteor-addon-template).
Requires: Fabric Loader + Meteor Client built for **Minecraft 1.21.4**. Litematica is **optional**
(soft dependency) - if it isn't installed, the addon still loads fine, only the `auto-collect` module
won't do anything.

## Modules (menu category: `obot`)

### `chest-tracker`
- Every time a chest-like block (Chest / Trapped Chest / Barrel / Shulker Box) gets opened, the addon
  remembers (in RAM only, nothing written to disk) what items are inside it.
- Opened chests get an outline "glow" rendered in the world - **including both halves of a double chest**
  (open one half, both halves glow).
- Toggling the module off/on, or leaving + rejoining the world, wipes the data (reset), as requested.

### `auto-collect`
- Reads Litematica's currently active Material List (the exact same one shown in the info-hub / Material
  List screen for the schematic you have placed) to know what materials are still missing and how many.
- **Automatically opens nearby chests for you** (within `search-range`, default 4.5 blocks) - you no
  longer need to manually right-click a chest to start looting it.
- Once a container screen is open, every tick it shift-clicks one item stack that matches something
  still missing. Anything not on the missing list is skipped entirely.
- Once a chest has nothing left worth taking, it auto-closes the screen and moves on to the next nearby
  chest.
- Stops taking items once your inventory (hotbar + 27 main slots) has no room left, and can optionally
  auto-disable itself once Litematica reports nothing missing anymore (`auto-disable-when-done` setting).

### Important limitation (read before using)
`auto-collect` does **not** pathfind/walk your character to chests that are far away - no Baritone-style
navigation is included. You still need to walk within range yourself; once you're close enough, the
module takes care of opening and looting chests automatically. If you want a fully hands-off flow
(auto-pathing between many chests), that would need a separate pathfinding module - not included here.

## Build

### Option 1: Build locally (needs internet access to maven.fabricmc.net / maven.meteordev.org)

> Note: this project uses Gradle 9 (required by the current Fabric Loom / meteor-client snapshot).
> `./gradlew` will download Gradle 9.5.1 automatically - you don't need Gradle preinstalled.

```bash
./gradlew build
```

The resulting jar is at `build/libs/meteor-obot-1.0.jar`. Drop it into your `mods/` folder alongside
Fabric API, Fabric Loader and Meteor Client (and Litematica if you want to use `auto-collect`).

### Option 2: Build on GitHub Actions (no internet needed on your own machine)

1. Create a new GitHub repo and push this whole folder (including `libs/*.jar` - **do not delete those
   two jar files**, they're compile-only dependencies and are never bundled into the final jar).
2. The included workflow `.github/workflows/build.yml` automatically runs `./gradlew build` on every push.
3. Go to the **Actions** tab on GitHub -> open the latest run -> download the jar from **Artifacts**
   (named `meteor-obot`).
4. If you push to `main`/`master`, the workflow also creates a "Dev Build" GitHub Release (tag `latest`)
   with the jar attached, downloadable straight from the **Releases** tab.

> If your repo uses a different default branch, update `on.push.branches` in `build.yml` accordingly.

## Folder structure

```
chest-addon/
├── build.gradle             # build config: Meteor maven + libs/ (Litematica, MaliLib)
├── gradle.properties        # MC 1.21.4, yarn mappings, mod name/version
├── libs/                    # Litematica + MaliLib jars (compile-only, never bundled)
├── .github/workflows/
│   └── build.yml            # GitHub Action: build + upload artifact + "latest" release
└── src/main/
    ├── java/com/obot/chest/
    │   ├── ObotAddon.java               # main entrypoint
    │   ├── litematica/
    │   │   ├── LitematicaCompat.java    # safe wrapper (checks the mod is actually installed)
    │   │   └── LitematicaAccess.java    # direct calls into Litematica's API
    │   └── modules/
    │       ├── ChestTrackerModule.java
    │       └── AutoCollectModule.java
    └── resources/fabric.mod.json
```

## Notes for anyone extending this code

Every Litematica API call in this project was verified against the real bytecode of the uploaded jar
(method names AND parameter types, using a small custom class-file parser since this sandbox has no
`javap`/maven access), and every Meteor Client API call was checked against the actual `meteor-client`
source for the 1.21.4 branch - so accuracy should be high. That said, the addon has only been verified
through a real GitHub Actions build up through compiling `LitematicaAccess`/`LitematicaCompat`; the two
module classes (`ChestTrackerModule`, `AutoCollectModule`) have not been build-verified yet after this
latest round of changes. If the next build fails, send the log back and it'll get fixed.
