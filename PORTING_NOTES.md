# Matter Manipulator Modern Port Notes

This repository is based on `GregTechCEu/GregTech-Addon-Template` for Minecraft 1.20.1, Forge 47.4.x, and GTCEu Modern 7.4+.

The original GTNH 1.7.10 source is cloned beside this project at:

`C:\Users\erick\OneDrive\Documents\MatterManipulator`

The local GTCEu Modern 7.5.2 source reference is at:

`C:\Users\erick\OneDrive\Documents\GregTech-Modern-7.5.2-1.20.1`

## Current Port Layer

- Modern mod id: `matter_manipulator`
- Original mod id: `matter-manipulator`
- Package root for the modern port: `com.raishxn.modern_manipulator`
- Registered the four original manipulator tiers:
  - Prototype Matter Manipulator
  - Matter Manipulator MKI
  - Matter Manipulator MKII
  - Matter Manipulator MKIII
- Registered the original 28 meta items as first-class 1.20 item registry entries.
- Copied original item textures and uplink overlay textures into a valid 1.20 resource namespace.
- Converted the initial item names from `en_US.lang` to `en_us.json`.
- Ported the original tier capability flags and upgrade bit state.
- Added a special crafting recipe for installing manipulator upgrades.
- Added the first operational block action: remove selected blocks with GTNH-like EU cost scaling.
- Changed removal into a persisted pending action processed over time using tier `placeSpeed` and `placeTicks`.
- Added a client-side wireframe preview for the selected A/B region.

## Next Port Targets

1. Port persistent manipulator state from 1.7.10 NBT into a 1.20-friendly state class.
2. Port energy storage and GTCEu EU behavior for the four manipulator tiers.
3. Port upgrade installation recipes and capability gates.
4. Port block selection, copy, exchange, move, and cable modes.
5. Port client preview rendering using modern Forge rendering events.
6. Port networking packets to SimpleChannel.
7. Port the Quantum Uplink machine as a GTCEu Modern machine definition.
8. Rebuild GTCEu recipe chains as datagen recipes.
