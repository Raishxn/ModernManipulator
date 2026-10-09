# Modern Manipulator

A Minecraft 1.20.1 / Forge 47 port of the [GregTech: New Horizons Matter Manipulator](https://github.com/GTNewHorizons/MatterManipulator) for GTCEu Modern 7.5.3+.

One handheld tool to build, copy, move, exchange and wire whole structures, pulling the items from your inventory, your ME system or a Quantum Uplink.

## Features

- Four tiers: Prototype, MKI, MKII and MKIII, with the original ranges, speeds, EU buffers and upgrades.
- Modes: Geometry (line, cube, sphere, cylinder), Copying (rotate, flip, array), Moving, Exchanging and Cables.
- GregTech: copies covers and their settings, pipe/cable connections, painting, auto output, circuits, machine configs, multiblock controller settings (voiding, batch, recipe type) and turbine rotors.
- AE2 (optional): parts, facades, memory card settings, cable mode, ME access through a Wireless Access Point, and Smart Copy (pattern buffers to proxies).
- Quantum Uplink multiblock: infinite range access to an ME system and automatic crafting of missing items through plans.
- Ghost block previews, a radial menu and keybinds (Ctrl + X/C/V/Z).

## Requirements

- Minecraft 1.20.1, Forge 47.4+
- GregTech CEu Modern 7.5.3+
- Applied Energistics 2 15+ (optional; needed for ME features and the Quantum Uplink)

## Quick start (copying)

1. Right click the air with the manipulator to open the radial menu and pick **Copying**.
2. Press **C** (Mark Copy) and right click two opposite corners of what you want to copy.
3. Press **V** (Mark Paste) and right click where the copy should go.
4. With a MKIII bound to a Quantum Uplink, open **Planning** in the radial menu to have AE2 craft the missing items.
5. Build.

## Development

- `./gradlew build` builds the jar into `build/libs`.
- `./gradlew runGameTestServer` runs the game tests.
- See [PORTING_NOTES.md](PORTING_NOTES.md) for what was ported, adapted or left out, and the other dev tasks.

## Credits

- Original mod by RecursivePineapple and the GTNewHorizons team.
- Based on the [GregTechCEu addon template](https://github.com/GregTechCEu/GregTech-Addon-Template).

## License

LGPL-3.0, like the original mod.
