# ModernManipulator Changelog

All notable changes to this project will be documented in this file.

---

## [0.1.0] - 2026-10-09

### Initial Release — Modern Matter Manipulation & Automation for GTCEu Modern (Forge 1.20.1)

ModernManipulator is a standalone Forge 1.20.1 port and modern remaster of **[MatterManipulator](https://github.com/GTNewHorizons/MatterManipulator)** from *GregTech: New Horizons*, engineered from the ground up for **GregTech CEu Modern 7.5.3+** and **Applied Energistics 2**.

---

### 🌟 New Features & Highlights

#### 🛠️ Handheld Matter Manipulator Progression (4 Tiers)
* **Tier Progression**: 4 balanced tiers mirroring original GTNH specs:
  * **Prototype (MV)**: 16-block range, 1 place/tick, 100k EU internal buffer.
  * **MK I (HV)**: 32-block range, 2 places/tick, 1M EU internal buffer.
  * **MK II (EV)**: 64-block range, 4 places/tick, 10M EU internal buffer.
  * **MK III (LuV/ZPM/UV)**: 128-block range, 8 places/tick (configurable), 100M EU internal buffer.
* **Modular Upgrades**:
  * **Excavation Upgrade**: Grants block-breaking/clearing abilities to the Prototype tier.
  * **Auxiliary Teleporter Upgrade**: Doubles block placement rate per tick (MK I+).
  * **Adaptive Wiring Harness Upgrade**: Reduces EU power consumption by 50% (MK I+).
  * **Energy Tunnel Upgrade**: Draws wireless EU power directly from laser or multi-amp hatches on linked Quantum Uplinks (MK III).

#### 📐 Complete Suite of Manipulation Modes
* **Geometry Mode**: Procedurally generate 3D primitives (**Line**, **Cube**, **Sphere**, **Cylinder**) with independent weighted block assignments for **Corners**, **Edges**, **Faces**, and internal **Volume**.
* **Copying Mode**: Select two points to copy entire volumes of blocks. Includes complete 3D transformations: rotate in 90° increments, flip across X or Z axes, and linear arrays/stacks.
* **Moving Mode**: Relocate running machines and full multiblock assemblies in-place without breaking blocks or dropping loose items.
* **Exchanging Mode**: In-place block replacement using configurable whitelist filters (e.g. swap all stone blocks for high-tier machine casings).
* **Cables & Pipes Mode**: Rapid wiring of GregTech energy cables, fluid pipes, and AE2 glass/dense cables across long distances.

#### ⚙️ Deep GregTech CEu Modern Integration
* **Cover Preservation**: Full transfer of robot arms, conveyors, pumps, and fluid regulators—including internal IO directions, filter slots, and conditional rules.
* **Machine State & Rotation**: Preserves horizontal facings, upwards facings, secondary rotations, muffled sound states, and circuit configurations.
* **Aesthetic Preservation**: Replicates machine painting and custom block dye colors.
* **Connections & Auto-Output**: Preserves fluid/item auto-output toggle states, pipe/cable connections, and blocked faces.
* **Turbine Rotors & Inventories**: Safely preserves turbine rotor items inside large multiblock turbines and handles battery buffer inventories.

#### 🧠 Applied Energistics 2 Integration & Smart Copy
* **Wireless Access Point Linking**: Place an MKI+ Matter Manipulator into an AE2 Wireless Access Point link slot to draw construction materials directly from your ME network within wireless range.
* **Cable Bus Attachments**: Replicates import/export buses, storage buses, level emitters, memory card configurations, and cable facades.
* **Smart Copy (Pattern Buffers to Proxies)**: Automatically converts ME Pattern Buffers into Pattern Buffer Proxies when duplicating automated processing setups.

#### 🌌 The Quantum Uplink Multiblock (9×9×9)
* **Interdimensional Megablock**: Authentic 9×9×9 structure built from High Power Casings, Fusion Casings MK2, Fusion Coils, and Trinium/Naquadah Alloy frames.
* **One-Click Binding**: Link your Matter Manipulator simply by right-clicking the uplink controller.
* **Infinite Range ME Sync**: Provides unlimited interdimensional access to your ME system via the Quantum Uplink ME Connector Hatch.
* **Plasma & Power Consumption**: Consumes 1A ZPM while active and a small amount of plasma per transfer from attached fluid hatches.
* **Automated Crafting with Plans**:
  * Inspect the bill of materials for your selection in the radial menu.
  * Automatically injects temporary processing patterns and triggers auto-crafting requests across your AE2 crafting CPUs for all missing blocks.

#### 🎮 Intuitive Controls & Shaders
* **Interactive Radial Menu**: Access all tiers, modes, shapes, removal rules, and transform editors via `Right-Click Air`.
* **Standard Keybindings**: Quick-mark shortcuts for Copy (`C`), Paste (`V`), Cut (`X`), and Undo/Reset (`Z`).
* **Middle-Click Picker**: Sample blocks in the world or directly inside GUI inventory slots.
* **Custom Core Shaders**: High-tech animated CAD bounding boxes (`mm_fancybox`) and translucent holographic ghost block previews (`mm_ghost`).

#### 🎨 Visual Branding & Assets
* Authorial Minecraft pixel art branding generated programmatically via Python ([tools/build_modern_manipulator_assets.py](tools/build_modern_manipulator_assets.py)).
* Full-resolution GitHub header banner (`banner.png` — 1200×380).
* Smooth 32-frame animated loop icon with transparent background (`logo.gif` — 512×512).
* In-game Forge mod menu icons (`icon.png` / `src/main/resources/icon.png` / `logoFile` in `mods.toml`).

---

### 📦 Dependencies & Requirements

* **Minecraft**: `1.20.1`
* **Forge**: `47.4.0` or newer
* **[GregTech CEu Modern](https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern)**: `7.5.3` or newer (**Required**)
* **[Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)**: `15.0.0` or newer (*Recommended / Required for ME features and Quantum Uplink*)

---

### 🤝 Credits & Acknowledgements

* **GTNewHorizons/MatterManipulator** (LGPL-3.0) by **RecursivePineapple** and the GTNH team for the original mod design, algorithms, radial menu concepts, and Quantum Uplink multiblock.
* **GregTech CEu Modern** team for the modern 1.20.1 GT API and framework.
* **Applied Energistics 2** team for modern ME grid and capability APIs.
