# Magical Land Gameplay

English | [简体中文](README.md)

Magical Land's gameplay addon focuses on pony tribe abilities and achievements. [Magical Land: Appearance](https://github.com/Magical-Land-Official/Magical-Land) provides models, textures, animations and customization. The two mods are developed and released separately.

## Gameplay and progress

### Tribe abilities

We are designing distinct abilities for unicorns, pegasi and earth ponies, giving each tribe its own ways to explore, travel and interact. Abilities are being developed in stages; see the [tribe ability documentation](docs/README.md#三族能力) for available features and design plans.

Choose a race when you enter a world. Changing race normally requires a potion. Servers can allow free changes and decide whether horns and wings must match your race. See [race selection](docs/races.md).

### Achievement system

Achievements use Minecraft's advancement system to record your experiences and interactions. A small number of achievements are available so far, with more planned as gameplay expands. See the [achievement guide](docs/achievements.md) for details.

The current development version is `0.3.2`. Earth ponies can crouch and focus to sense nearby ground activity as soft colored clouds. Moving slowly while crouched weakens the sense; standing still restores it. See the [tremor sense guide](docs/earth-sense.md). Other guides and verification records are collected in the [documentation index](docs/README.md).

## Installation

For Minecraft **1.20.1 / Fabric**. Install the following on both client and server:

- Fabric Loader 0.19.1 or newer, and Fabric API.
- Magical Land: Gameplay `0.3.2`.
- Magical Land: Appearance `0.3.4` and its GeckoLib dependency (4.7 or newer).

Gameplay currently supports Appearance `>=0.3.4 <0.4.0`. Players install the regular mod JARs. Upgrade Gameplay on both client and server together.

Appearance also works on its own, with server-side appearance synchronization included in its JAR. When upgrading from the old combined mod, remove that package before installing the new ones.

Mod Menu is an optional client-side addition that opens the gameplay settings page.

## Getting started

- Choose a race when you first enter a world. Unicorns can use telekinetic projection and earth ponies can use tremor sense. Pegasus abilities are in development.
- Hold **R** to open the ability wheel, select an ability and release.
- Press **V** to use the selected ability. For ongoing abilities, press **V** again to end them. Bindings can be changed in Minecraft's controls settings.
- With Mod Menu installed, open “Mods → Magical Land: Gameplay → Configure” to adjust the ability camera, choose a race or view server rules.

Access requirements, ability-specific controls and test commands are covered in the [tribe ability documentation](docs/README.md#三族能力).

## Development

The project uses the Gradle 9.4.1 wrapper and Loom 1.16.3, with Java 17 as its output target. The build tools use Appearance's public API 1.4 artifacts for compilation; the full Appearance mod is loaded at runtime. Build commands, module responsibilities and save migration are covered in [development and repository boundaries](docs/repository-boundary.md). Planned work is tracked in [TODO](TODO.md).

## Authors and origins

Project authors: JessDaodao and MayHooves. Licensed under [MIT](LICENSE.txt).

This repository was split from the original project at revision `d4786bd`. Its first import is commit `d6166f3`. Earlier history and original author records remain in the Appearance repository. Report problems through [Issues](https://github.com/Magical-Land-Official/Magical-Land-Gameplay/issues).
