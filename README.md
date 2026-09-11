# Magical Land Gameplay

[English](README_EN.md) | 简体中文

Magical Land 的独立玩法 Addon，以三族能力与成就系统为核心，逐步拓展小马在 Minecraft 中的玩法。模型、贴图、动画和捏脸由 [Magical Land: Appearance](https://github.com/Magical-Land-Official/Magical-Land) 提供，两个模组分别开发与发布。

## 玩法与进度

### 三族能力

我们正在为独角兽、天马与陆马设计各具特色的能力，让每一族都有不同的探索、移动与互动方式。能力正在分阶段开发，当前功能与设计方案见[三族能力文档](docs/README.md#三族能力)。

进入世界后可先选择种族，默认通过药水改族。服务器可设置自由改族及角、翅膀的外观限制，详见[种族选择](docs/races.md)。

### 成就系统

通过原版进度系统记录玩家在游戏中的经历与互动。目前已实现少量成就，后续会随玩法逐步扩充，详见[成就系统文档](docs/achievements.md)。

当前开发版本为 `0.3.3`。独角兽可使用[自我悬浮](docs/unicorn-levitation.md)，进行魔法托举、落地缓冲和液面悬浮；陆马可通过[震动感知](docs/earth-sense.md)察觉附近的地面活动。各系统的使用说明和验证记录见[文档目录](docs/README.md)。

## 安装

适用于 Minecraft **1.20.1 / Fabric**。客户端和服务器均需安装：

- Fabric Loader 0.19.1 或更新版本、Fabric API。
- Magical Land: Gameplay `0.3.3`。
- Magical Land: Appearance `0.3.5`，以及其 GeckoLib 依赖（4.7 或更新版本）。

Gameplay 当前支持 Appearance `>=0.3.5 <0.4.0`。玩家安装普通模组 JAR；升级时同步更新客户端和服务器的 Gameplay。

Appearance 可以单独使用，外观同步服务端代码也包含在它的 JAR 中。从旧一体包升级时，先移除旧包再安装新包。

客户端可选安装 Mod Menu，以打开玩法设置页。

## 开始使用

- 首次进入世界时选择种族。独角兽可使用念力出窍和自我悬浮，陆马可使用震动感知，天马能力正在开发。
- 按住 **R** 打开能力轮盘，选中能力后松开。
- 按 **V** 使用所选能力；自我悬浮会先进入准备状态，按住跳跃键才开始托举。按键可在原版控制设置中调整。
- 安装 Mod Menu 后，从「模组 → Magical Land: Gameplay → 设置」调整能力视角、选择种族或查看服务器规则。

各能力的启用条件、专用操作和测试指令见[三族能力文档](docs/README.md#三族能力)。

## 开发

使用 Gradle 9.4.1 wrapper、Loom 1.16.3，Java 输出目标为 17。构建工具使用外观公共 API 1.5 的开发产物进行编译，运行时加载完整外观模组。构建入口、模块职责和存档迁移说明见[开发与仓库边界](docs/repository-boundary.md)；后续工作见[待办清单](TODO.md)。

## 作者与来源

项目作者：JessDaodao、MayHooves。采用 [MIT 许可](LICENSE.txt)。

本仓库从原项目的 `d4786bd` 版本拆分而来，首次导入提交为 `d6166f3`。早期完整历史与原作者记录保留在外观仓库。问题反馈可提交至 [Issues](https://github.com/Magical-Land-Official/Magical-Land-Gameplay/issues)。
