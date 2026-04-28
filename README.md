# Modern Manipulator

Modern Manipulator is a Minecraft 1.20.1 / Forge port of the GregTech: New Horizons Matter Manipulator, targeting GTCEu Modern 7.5.2+.

The goal is a faithful 1:1 gameplay port where possible, while replacing the old Minecraft 1.7.10, IC2, GTNH, and Forge APIs with their modern GTCEu equivalents.

## Current Status

This project is in early porting work.

Implemented so far:

- GTCEu addon template converted into the Modern Manipulator project
- `matter_manipulator` mod id and `com.raishxn.modern_manipulator` Java package
- Four manipulator tiers registered
- Original component/upgrades registered as modern items
- Original item textures and initial uplink textures copied into modern resource paths
- Dedicated Matter Manipulator creative tab
- Basic manipulator NBT state
- GTCEu electric item capability
- EU capacity and voltage tiers matching the original mod
- Coord A / Coord B marking through block interaction
- Upgrade capability gates and upgrade installation recipe
- Pending selection removal action with EU cost, range checks, and per-tier throughput
- Client-side selection preview box
- Basic tooltips and charge bar

## Reference Repositories

- Original GTNH mod: https://github.com/GTNewHorizons/MatterManipulator
- GTCEu addon template: https://github.com/GregTechCEu/GregTech-Addon-Template

## Development

Build:

```powershell
.\gradlew.bat build
```

Format:

```powershell
.\gradlew.bat spotlessApply
```

Generated jar:

```text
build/libs/modern-manipulator-0.1.0.jar
```

## Porting Roadmap

- Tier capability gates and upgrade installation
- Range validation and area selection
- Geometry, removing, copying, exchanging, moving, and cable modes
- Client selection preview rendering
- Networking and GUI
- Quantum Uplink GTCEu machine
- AE2 and inventory integration
- Full GTCEu recipe chains

## License

LGPL-3.0, following the template and original project licensing.
