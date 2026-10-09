# ModernManipulator — port notes

Port of [GTNewHorizons/MatterManipulator](https://github.com/GTNewHorizons/MatterManipulator) to Minecraft 1.20.1 / Forge 47 / GTCEu Modern 7.5.3.
Mod id: `modern_manipulator`. The package layout mirrors the original (`common.building`, `common.items.manipulator`, `common.networking`, `common.uplink`, ...).

## Ported 1:1
- 4 tiers (Prototype, MKI, MKII, MKIII) with the original ranges, place speeds/ticks, voltages, EU buffers, capabilities and allowed upgrades. MK3 speed is still configurable.
- Upgrades (Excavation, Auxiliary Teleporter, Adaptive Wiring Harness, Energy Tunnel) and the install-upgrade crafting recipe.
- All modes: Geometry (line, cube, sphere, cylinder with corner/edge/face/volume weighted block lists), Copying (with rotation/flip transform and stacking/array), Moving, Exchanging (whitelist + replacement), Cables.
- Radial menu with the same tree of options, the transform/coordinate editor window, key bindings (Ctrl + X/C/V/Z), middle click to pick blocks (also on items inside GUIs).
- Building algorithm: chunk/protection checks, remove modes, dependency shuffling (torches, levers...), EU cost formula (hardness, block entity penalty, distance^1.25, power efficiency upgrade), item sourcing order (pending drops → player → ME → uplink), drops returned through ME → uplink → inventory → ground, fluids into containers.
- Preview: region boxes, rulers, ghost block hints (with build error/warning tinting), HUD text.
- Plans: required item report, manual/auto plans through the uplink (fake processing patterns + auto crafting requests).
- Quantum Uplink multiblock (original 9x9x9 structure) + ME connector hatch: 1A ZPM while active, plasma cost per transfer, plan patterns, power refill for the Energy Tunnel upgrade.
- GregTech: colour, covers (with their config), auto output, circuit, muffled, distinct, memory-card config (`ICopyable`), pipe/cable connections and blocked faces, cable mode with connections, ore voiding, battery buffer inventories. Facing/upwards facing are part of the block state.
- AE2: parts on cable busses (with memory card settings), facades, AE machine settings, cable mode, exchanging cables, ME linking through the Wireless Access Point (put the MKI+ manipulator in the WAP link slot), Smart Copy: pattern buffers → pattern buffer proxies.

## Adapted for 1.20
- `BlockSpec` stores a full `BlockState`; this replaces the old meta + `BlockPropertyRegistry` (1.7.10 only). Transforms use vanilla `rotate`/`mirror` for horizontal transforms and a generic direction/axis/half mapping otherwise.
- The uplink is bound by right clicking its controller with the manipulator (GTCEu controllers have no controller slot).
- ME connection uses AE2's grid linking instead of encryption keys / security terminals.

## Not ported (no 1.20 equivalent)
- Integrations with 1.7.10 only mods (Forge Multipart, Carpenter's Blocks, ArchitectureCraft, OpenComputers, Thaumcraft, EnderIO conduits, Blood Magic teleposing).
- Smart Copy "Interfaces → P2P" (AE2 1.20 has no interface P2P tunnel holding patterns) and wireless connector/hub relinking (removed from AE2).

## Testing
- `./gradlew runGameTestServer` runs the game tests (`gametest/MMGameTests`, `gametest/MMCompatGameTests`).
- `./gradlew runData` regenerates the uplink machine models. Delete `src/generated/resources/assets/modern_manipulator/lang/` afterwards (the hand-written langs in `src/main/resources` are the real ones: en_us, pt_br, zh_cn).
