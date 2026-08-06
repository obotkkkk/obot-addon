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
  still missing. Anything not on the missing list is skipped entirely, and it stops taking more of a
  given item once it's collected enough (see note below) - it does not just vacuum up everything.
- Once a chest has nothing left worth taking, it auto-closes the screen and moves on to the next nearby
  chest.
- Stops taking items once your inventory (hotbar + 27 main slots) has no room left, and can optionally
  auto-disable itself once Litematica reports nothing missing anymore (`auto-disable-when-done` setting).

Note on quantities: earlier versions of this module read `MaterialListEntry.getCountMissing()` directly,
which turned out to be the real bug - decompiling Litematica showed that field is only set once when the
Material List is (re)created and is **never recomputed automatically** as your inventory changes (calling
`getMaterialsMissingOnly(true)` only re-filters an already-cached list; it doesn't rescan anything). So the
"missing" amount for an item just stayed at whatever it was originally, no matter how much had already
been picked up - which is why it looked like the module was "taking everything" (any item your schematic
uses at all would look permanently missing). The fix calls `MaterialListUtils.updateAvailableCounts(...)`
- the same method Litematica's own GUI uses - to refresh each entry's "available" count against your
CURRENT inventory, and computes missing as `total - available` itself instead of trusting the stale field.
On top of that, the module still keeps its own local running total between refreshes (decremented the
instant it takes a stack) so it can't over-collect even in the brief window between two refreshes.

### Important limitation (read before using)
`auto-collect` does **not** pathfind/walk your character to chests that are far away - no Baritone-style
navigation is included. You still need to walk within range yourself; once you're close enough, the
module takes care of opening and looting chests automatically. If you want a fully hands-off flow
(auto-pathing between many chests), that would need a separate pathfinding module - not included here.

### `fly-to-placement`
For servers that tolerate flying freely (like creative mode). Keeps flying toward the CLOSEST position
that still needs a block placed - i.e. the same ghost/preview blocks you see for your placed schematic.
Pairs well with Litematica's own "print" tool (or a similar addon such as
[litematica-printer](https://github.com/aleksilassila/litematica-printer)) or a manual build: this module
gets you there, you (or the print tool) place the block, and it automatically moves on to the next closest
missing position once that one is done. Settings: `speed` (blocks/tick), `search-radius` (how far around
you to look, default 32 blocks) and `hover-height` (how far above the target block to hover, so you don't
fly into the block itself).

Navigation is a small state machine (`PlacementNavigator`), not just a straight line:
- If a direct line to the target is clear, it flies straight there (`DIRECT`).
- If not, it climbs to a cruise altitude, flies over at that altitude, then descends onto the target
  (`ASCEND` -> `TRAVEL` -> `DESCEND`) instead of flying into whatever is blocking the way.
- If it still makes near-zero progress for ~1.2s in a row, it hops up and retries at a higher cruise
  altitude (up to 3 times) before giving up on that specific position for about 10 seconds and picking a
  different one, so it can never get stuck forever fighting the same obstacle.
- If it arrives and hovers at a position for 5+ seconds without that position ever actually getting
  placed (or spends more than 30s total on one target for any reason), it likewise skips it for a while
  and moves on - so it can't get stuck standing still on a target forever.

Requires an enabled Litematica schematic placement. Finds its target the same way
[litematica-printer](https://github.com/aleksilassila/litematica-printer) does: Litematica keeps a virtual
"schematic world" that already combines every active placement into real-world coordinates (that's what
draws the ghost blocks you see in-game), so this module just compares that virtual world against the real
one, block by block, expanding outward from you until it finds the nearest mismatch.

### `fly-goto`
Simple point-to-point flight: enter `x`, `y`, `z` and a `speed`, turn the module on, and it flies straight
to that position (with the same "stuck for 5s -> fly up and over" smart-avoidance as `fly-to-placement`).
Can optionally auto-disable itself once you arrive (`auto-disable-on-arrival`, on by default).

### About the flight modules in general
Both fly modules work by directly setting your velocity every tick toward the target (the same approach
Meteor's own built-in `Flight` module uses in its "Velocity" mode) - they rely on the server actually
tolerating that kind of movement, as you described your server does. They are NOT a bypass for servers
that don't allow flying/would flag it as cheating.

**Important fix:** earlier versions of both fly modules set `abilities.flying = true` on the client only,
without ever telling the server about it (`ClientPlayerEntity.sendAbilitiesUpdate()`). Without that call,
the server never learns the client thinks it's flying and keeps applying its own gravity/speed physics on
top of whatever we set client-side - this was the actual cause of `fly-goto`'s speed setting appearing to
do nothing, and it likely made `fly-to-placement` less reliable too. Both modules now call
`sendAbilitiesUpdate()` whenever the flying ability actually changes.

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
    │   ├── util/
    │   │   ├── FlightController.java    # simple "fly toward a point" logic used by fly-goto
    │   │   └── PlacementNavigator.java  # smarter phased navigator used by fly-to-placement
    │   └── modules/
    │       ├── ChestTrackerModule.java
    │       ├── AutoCollectModule.java
    │       ├── FlyToPlacementModule.java
    │       └── FlyGotoModule.java
    └── resources/fabric.mod.json
```

## Notes for anyone extending this code

Every Litematica API call in this project was verified against the real bytecode of the uploaded jar
(method names AND parameter types, using a small custom class-file parser since this sandbox has no
`javap`/maven access), and every Meteor Client API call was checked against the actual `meteor-client`
source for the 1.21.4 branch. `findNearestMissingBlockPos` (used by `fly-to-placement`) was additionally
cross-checked against the real source of two other Litematica-related projects to make sure the approach
matches how actual working tools do it:
- [maruohon/litematica](https://github.com/maruohon/litematica) - the mod itself.
- [aleksilassila/litematica-printer](https://github.com/aleksilassila/litematica-printer) - a community
  addon that does almost exactly what `fly-to-placement` needs (find the next block that doesn't match
  the schematic yet). Its `Printer.java`/`SchematicBlockState.java` showed that comparing
  `WorldSchematic.getBlockState(pos)` against the real world directly is the right approach - much
  simpler (and, it turns out, actually public/usable) compared to the previous attempt at this module,
  which tried to drive Litematica's internal `SchematicVerifier` and failed to compile because the
  methods it needed (`updateClosestPositions`, `getClosestMismatchedPositionsFor`) are private.

`PlacementNavigator`'s phased flight (ASCEND/TRAVEL/DESCEND/HOVER/DIRECT, skip-list, watchdogs) is based
on a decompiled reference addon the user provided (`javap -c -p` on an "autoflyer" jar's
`LitematicaNavigator` class) - it fixes an earlier bug where `fly-to-placement` could arrive at a target
and then just sit there forever if that position never actually got marked "done" for any reason.

`auto-collect` also had a bug where it effectively took every matching item out of a chest instead of
stopping once enough of each material was collected, because it only checked Litematica's live "missing"
count (which doesn't necessarily update instantly as items are taken) rather than tracking its own
running total - see the note in the `auto-collect` section above for the fix.

All of the above said, none of this has been checked by a real compile yet - if the next GitHub Actions
build fails, send the log back (build logs are the most useful thing to share when something goes wrong).
