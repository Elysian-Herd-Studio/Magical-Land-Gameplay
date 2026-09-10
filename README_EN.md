# Magical Land Gameplay

English | [简体中文](README.md)

Magical Land's gameplay addon focuses on pony tribe abilities and achievements. [Magical Land: Appearance](https://github.com/Magical-Land-Official/Magical-Land) provides models, textures, animations and customization. The two mods are developed and released separately.

## Gameplay and progress

### Tribe abilities

We are designing distinct abilities for unicorns, pegasi and earth ponies, giving each tribe its own ways to explore, travel and interact. Abilities are being developed in stages; see the [tribe ability documentation](docs/README.md#三族能力) for available features and design plans.

### Achievement system

Achievements use Minecraft's advancement system to record your experiences and interactions. A small number of achievements are available so far, with more planned as gameplay expands. See the [achievement guide](docs/achievements.md) for details.

The current version is `0.2.2`, which completed this round of in-game acceptance testing on 2026-09-10. Multiplayer and third-party mod combinations need separate testing. Guides and verification records are collected in the [documentation index](docs/README.md).

## Installation

For Minecraft **1.20.1 / Fabric**. Install the following on both client and server:

- Fabric Loader 0.19.1 or newer, and Fabric API.
- Magical Land: Gameplay `0.2.2`.
- Magical Land: Appearance `0.3.3` and its GeckoLib dependency (4.7 or newer).

Gameplay currently supports Appearance `>=0.3.3 <0.4.0`. Players install the regular mod JARs. Upgrade Gameplay on both client and server together.

Appearance also works on its own, with server-side appearance synchronization included in its JAR. When upgrading from the old combined mod, remove that package before installing the new ones.

Mod Menu is an optional client-side addition that opens the gameplay settings page.

## Getting started

- Hold **R** to open the ability wheel, select an ability and release.
- Press **V** to use the selected ability. For ongoing abilities, press **V** again to end them. Bindings can be changed in Minecraft's controls settings.
- With Mod Menu installed, open “Mods → Magical Land: Gameplay → Configure” for the addon's client settings.

Access requirements, ability-specific controls and test commands are covered in the [tribe ability documentation](docs/README.md#三族能力).

## Development

The project uses the Gradle 9.4.1 wrapper and Loom 1.16.3, with Java 17 as its output target. The build tools use Appearance's public API 1.3 artifacts for compilation; the full Appearance mod is loaded at runtime. Build commands, module responsibilities and save migration are covered in [development and repository boundaries](docs/repository-boundary.md). Planned work is tracked in [TODO](TODO.md).

## Authors and origins

Project authors: JessDaodao and MayHooves. Licensed under [MIT](LICENSE.txt).

This repository was split from the original project at revision `d4786bd`. Its first import is commit `d6166f3`. Earlier history and original author records remain in the Appearance repository. Report problems through [Issues](https://github.com/Magical-Land-Official/Magical-Land-Gameplay/issues).
