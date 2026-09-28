# Litematica Printer — Hana TGP

**Made especially for the 6b6t anarchy server — [6b6t.org](https://6b6t.org)**  
Built in the name of **TGP — The Project Group** for the 6b6t community, as a tool to help make our server look even more awesome than it already is.  
Anyone is welcome to use it — it works on other servers too.

A client-side Fabric mod that adds reliable automated schematic building to Litematica.  
It restores schematics by placing the correct blocks around the player and also provides filling, fluid removal, mining, bedrock breaking, inventory assistance, and related utilities.

This fork is based on **[Litematica Printer — Hana](https://github.com/Yur1Ca/litematica-printer)** by Yur1Ca.  
Hana is an excellent continuation of earlier printer work. I forked it because I wanted to ship certain fixes and features quickly, in my own way, without waiting for upstream timing — especially improvements that make the printer more resilient on laggy / rate-limited anarchy servers like 6b6t.

The entire mod has been translated to English as the default language. This makes development easier for me and also avoids configuration / translation conflicts with the original Hana printer when both are present.

Huge thanks to **Yur1Ca** for the outstanding work on Hana — this project would not exist without it.

---

## Download

- **[GitHub Releases](https://github.com/Fractal420/litematica-printer-hana-TGP/releases)** — recommended. Standalone JARs per Minecraft version + multi-version builds.
- Current version naming follows the pattern `Hana-TGP-Vx` (e.g. `Hana-TGP-V4`).

There is currently no Modrinth page for this fork. Use the GitHub releases only.

**Important:** If you previously used original Hana, delete the old config file and start with the new defaults the first time you launch this fork. The defaults are tuned differently (especially for server-revert recovery).

---

## Supported Minecraft versions

- 1.18.2
- 1.19.4
- 1.20.1, 1.20.2, 1.20.4, and 1.20.6
- 1.21 through 1.21.11
- 26.1.x and 26.2

Versions older than 1.18.2 are not supported. Intermediate Minecraft versions may work when they fall inside the same compatibility range, but are not always built as separate JARs.

---

## Requirements

**Required:**

- [Fabric Loader](https://fabricmc.net/)
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [MaLiLib](https://modrinth.com/mod/malilib)
- [Litematica](https://modrinth.com/mod/litematica)

**Optional integrations:**

- [Tweakeroo](https://modrinth.com/mod/tweakeroo)
- [Quick Shulker](https://github.com/MoRanpcy/quickshulker)
- [Take It Out](https://modrinth.com/mod/takeitout) — on supported 1.21.x and 26.x versions
- [Chest Tracker](https://github.com/ponuing/ChestTracker) — on versions with a matching upstream build

Dependency versions **must** match the Minecraft version you are launching.

---

## Features

### Printing and performance

- Player-centered placement ordered by distance.
- Scanner caching and time-budgeted iteration for large schematic areas.
- Packet-based placement for faster operation without client-side ghost blocks.
- Placement progress and missing-material HUDs.
- Server-lag and round-trip-time safeguards for mixed-material printing.
- Safer handling for directional and state-sensitive blocks.
- Continuous retry after server reverts / rate limits — positions are kept and re-checked until the world actually matches the schematic (or a timeout occurs). Cooldowns are cleared on mismatch so the printer can recover without needing to be toggled off and on.
- Sorted target queue that refills when low or dirty instead of only after a full batch is consumed.
- Defaults tuned for dense / laggy areas (lazy scan effectively off, shorter place cooldown, conservative blocks-per-tick).

### Building tools

- Fill within the active schematic or selection area.
- Drain water and lava source blocks.
- Mine blocks within the selected area.
- Independent handling of extra blocks and wrong-state blocks.
- Bedrock-breaking mode with an allowlist.
- Waterlogged block placement and ice-breaking water placement.
- Replacement of dead coral in the schematic using live coral.

### Selection ranges (print & mine)

- Litematica Selection
- Litematica Render Layer
- Below Player (full Litematica selection)
- Above Player (full Litematica selection)
- Below Player + Render Layer
- Above Player + Render Layer

Available for both print and mine selection ranges. Above/Below modes correctly respect wrong / extra / wrong-state block breaking.

### Convenience & safety

- Pause printing / mining while eating (or holding food).
- Pause printing / mining while holding a sword (attacking).
- Drop empty shulkers option.

### Material refill systems

The printer can pull materials automatically when they run out. Several paths are available:

**Manual Vanilla Refill** (no extra mods required)

- Places a shulker box from your inventory, opens it, takes the needed items, closes it, breaks it, and picks it back up — all using vanilla interactions.
- Pauses printing while a refill is in progress so placement does not fight the refill sequence.
- Optional **refill from ender chest**: places one or two ender chests, opens them, pulls shulker boxes out into your inventory, then uses those shulkers for materials. You can choose how many ender chests to place (1 or 2).
- Optional **enchanted golden apple** auto-refill (keeps gapples stocked while working).
- Optional **netherite pickaxe** auto-refill / swap (useful when mining or bedrock-breaking; respects remaining durability).

**Quick Shulker** (optional mod)

- Opens shulker boxes directly from the inventory without placing them in the world.
- Ordered return of items into the original shulker when possible.
- Store-orderly mode for tidying inventory near full.

**Other inventory helpers**

- Take It Out remote material retrieval on supported versions.
- Chest Tracker retrieval from containers explicitly cached inside the active Litematica selection on supported versions.
- Material switching safeguards intended to prevent placement with the previous hotbar item.

The printer also contains special placement logic for many vanilla blocks, including stairs, doors, trapdoors, hoppers, chests, levers, redstone wire, vines, hanging plants, grindstones, crafters, flower clusters, and other directional blocks.

---

## Basic usage

1. Load a schematic in the world with Litematica.
2. Move within interaction range of the schematic blocks.
3. Press **Caps Lock** to enable the printer. (Configurable)
4. Adjust printer settings in the configuration screen when required by the server (especially work interval / place cooldown on strict or laggy servers).

Most options include tooltips in the configuration interface.

**Tip for laggy / rate-limited servers (including 6b6t):** start with the defaults. If placements are still being reverted, slightly increase place cooldown or lower blocks-per-tick rather than turning the printer off and on repeatedly.

---

## Known unsupported content

Some content cannot currently be printed reliably and may be skipped or placed with an incorrect state:

- Skulls, signs, banners, and other blocks with unusually complex state or data handling.
- Cauldrons containing fluids.
- Entities such as item frames, armor stands, and paintings.
- Non-vanilla content unless it is explicitly supported.

If a vanilla block is placed incorrectly even at a conservative work interval, open an issue with the block ID, target state, Minecraft version, printer version, and a reproducible example.

---

## Troubleshooting

### The printer does not work after being enabled

- Some anti-cheat systems reject the printer’s silent rotation or placement interaction.
- A very short work interval can exceed a server’s placement-rate limit. Increase the interval or try packet-based printing when the server supports it.
- Account or session state can occasionally interfere with interaction packets. Reconnecting may help.

### Blocks keep disappearing / getting reverted

- The printer continuously re-checks positions and recovers from server reverts. Make sure you deleted any old config from a previous printer so the current defaults are used.
- If the printer still goes idle, try toggling it off/on once so the scan fully resets, then leave it running.

### Blocks are placed with the wrong state

- Server anti-cheat or high latency may prevent the simulated rotation from being accepted.
- The work interval may be too short for the server to acknowledge material switching and placement.
- The block may require placement logic that is not implemented yet.

### Quick Shulker does not work

- The server must support opening shulker boxes from the inventory for the selected integration mode.
- The configured mode must match the mod or server behaviour actually available.
- Litematica’s `pickBlockableSlots` must contain usable hotbar slots and should not be filled entirely with shulker boxes.

---

## Building

JDK 25 is recommended when building the complete multi-version Wrapper.

Multi-version Wrapper:
```bash
./gradlew :fabricWrapper:build
```

Standalone version (example for 1.21.11):
```bash
./gradlew :1.21.11:build
```

Build outputs:

- Multi-version Wrapper: `fabricWrapper/build/libs/`
- Standalone version JARs: `fabricWrapper/build/libs/jars/` (or the combined output folder depending on the latest build script)

The first build downloads Minecraft and mod dependencies and requires a working connection to their Maven repositories.

---

## Support and contributing

- Use [GitHub Issues](https://github.com/Fractal420/litematica-printer-hana-TGP/issues) for reproducible bugs, block support problems, and concrete feature requests.
- Search existing issues before opening a new report.
- Keep one independently reproducible problem per issue and attach `latest.log`, crash reports, screenshots, or short recordings where relevant.

This is a personal fork focused on practical improvements for anarchy servers. Pull requests that improve reliability, English documentation, or 6b6t-friendly behaviour are welcome.

---

## Acknowledgements

- **[Yur1Ca / Litematica Printer — Hana](https://github.com/Yur1Ca/litematica-printer)** — the foundation of this project. Thank you for the excellent work.
- [bunny_i](https://github.com/bunnyi116) for broad support and contributions to the Hana lineage.
- [aleksilassila/litematica-printer](https://github.com/aleksilassila/litematica-printer) for the original project.
- [zhaixianyu/litematica-printer](https://github.com/zhaixianyu/litematica-printer) for earlier fixes and features.
- [MoRanpcy/quickshulker](https://github.com/MoRanpcy/quickshulker) for Quick Shulker integration.
- [bunnyi116/fabric-bedrock-miner](https://github.com/bunnyi116/fabric-bedrock-miner) for the bedrock-breaking foundation.
- Everyone who tests, reports issues, or helps improve the printer on anarchy servers.

---

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE.md).
