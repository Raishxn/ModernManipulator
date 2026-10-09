![ModernManipulator Banner](https://raw.githubusercontent.com/Raishxn/ModernManipulator/main/banner.png)

# ⚡ ModernManipulator

**Next-generation handheld building, structure duplication, and automated fabrication for GregTech CEu Modern (Forge 1.20.1).**

**ModernManipulator** is a dedicated modern remaster and standalone port of the legendary **[MatterManipulator](https://github.com/GTNewHorizons/MatterManipulator)** from *GregTech: New Horizons*, adapted specifically for **GregTech CEu Modern 7.5.3+** and **Applied Energistics 2**.

One handheld tool to build, copy, move, exchange, and wire whole structures, high-tier factories, and complex multiblock lines—pulling items and fluids seamlessly from your inventory, your AE2 ME network, or the interdimensional **Quantum Uplink**.

---

## 🌟 Key Features

### 🛠️ Four Manipulator Tiers
Scales smoothly with your GregTech progression:
* **Prototype (MV)**: 16-block range, 1 block/tick, 100k EU buffer.
* **MK I (HV)**: 32-block range, 2 blocks/tick, 1M EU buffer.
* **MK II (EV)**: 64-block range, 4 blocks/tick, 10M EU buffer.
* **MK III (LuV/ZPM/UV)**: 128-block range, 8 blocks/tick (configurable), 100M EU buffer.

### 📐 Versatile Manipulation Modes
* **Geometry Mode**: Procedurally construct 3D geometric shapes (**Line**, **Cube**, **Sphere**, **Cylinder**) with independent block rules for corners, edges, faces, and volume.
* **Copying Mode**: Duplicate entire structures with full 3D rotation (90° steps), mirroring (X/Z axes), and linear stacking/arrays.
* **Moving Mode**: Pick up and relocate active machines and complex multiblocks without breaking blocks or dropping loose items.
* **Exchanging Mode**: Replace blocks in the world using customizable whitelists.
* **Cables & Pipes Mode**: Run and replace GregTech power cables, fluid pipes, and AE2 cables in seconds.

### ⚙️ Deep GregTech CEu Modern Integration
Preserves all critical machine data and multiblock states:
* **Covers & Configurations**: Transfers robot arms, conveyors, pumps, and regulators with all internal filter and conditional logic intact.
* **Machine State & Rotations**: Preserves facing directions, secondary rotations, muffled sound states, and circuit settings.
* **Painting & Aesthetics**: Preserves custom block paint dyes and machine colors.
* **Connections & Auto-Output**: Preserves fluid/item auto-output toggle states, pipe/cable connections, and blocked faces.
* **Large Turbine Rotors**: Safely preserves turbine rotors inside multiblock turbines.

### 🧠 Applied Energistics 2 & Smart Copy
* **Wireless Access Point Linking**: Insert an MKI+ Matter Manipulator into an AE2 Wireless Access Point to draw items directly from that ME system.
* **Cable Bus Attachments**: Replicates import/export buses, storage buses, level emitters, memory card settings, and cable facades.
* **Smart Copy (Pattern Buffers to Proxies)**: Automatically converts ME Pattern Buffers into Pattern Buffer Proxies when duplicating automated setups.

### 🌌 The Quantum Uplink Multiblock (9×9×9)
A monumental multiblock for end-game interdimensional automation:
* **Infinite Range ME Sync**: Right-click the controller to bind your Matter Manipulator for unlimited interdimensional reach.
* **Automated Crafting with Plans**: Calculates missing blocks for your blueprint and commands connected AE2 crafting CPUs to fabricate everything automatically.
* **Energy Tunnel Power Provider**: Powers MK III manipulators wirelessly when equipped with the Energy Tunnel upgrade.

### 🔌 Modular Upgrades
* **Excavation Upgrade**: Grants block-breaking/clearing capabilities to the Prototype tier.
* **Auxiliary Teleporter Upgrade**: Doubles the block placement rate per tick.
* **Adaptive Wiring Harness Upgrade**: Reduces EU power consumption by 50%.
* **Energy Tunnel Upgrade**: Draws wireless EU power directly from Quantum Uplinks.

---

## 🎮 Controls & Quick Start

* **Open Radial Menu**: `Right-Click Air` with the manipulator in hand.
* **Mark Copy**: `C` (select two opposite corners).
* **Mark Paste**: `V` (select paste destination).
* **Mark Cut**: `X` (in Moving mode).
* **Undo / Reset**: `Z`.
* **Middle-Click**: Sample any block in the world or inside GUI inventory slots.

---

## 📦 Requirements

* **Minecraft**: `1.20.1`
* **Forge**: `47.4.0` or newer
* **[GregTech CEu Modern](https://www.curseforge.com/minecraft/mc-mods/gregtechceu-modern)**: `7.5.3` or newer (**Required**)
* **[Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2)**: `15.0.0` or newer (*Recommended / Required for ME features and Quantum Uplink*)

---

## 🤝 Credits & Attribution

* **[GTNewHorizons/MatterManipulator](https://github.com/GTNewHorizons/MatterManipulator)** (LGPL-3.0) by **RecursivePineapple** and the GTNH team for the original mod design, algorithms, and multiblock concepts.
* **[GregTech CEu Modern](https://github.com/GregTechCEu/GregTech-Modern)** team for the modern 1.20.1 GT API.
* **[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)** team for modern ME grid systems.

---

## 📄 License

ModernManipulator is licensed under the **GNU Lesser General Public License v3.0** (LGPL-3.0), matching the original MatterManipulator.
