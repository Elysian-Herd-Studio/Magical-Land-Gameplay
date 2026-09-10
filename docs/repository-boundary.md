# 开发与仓库边界

[文档目录](README.md) → 开发与维护

Gameplay 负责三族能力与成就系统，包括玩法规则、服务端判定、客户端操作与 HUD、交互 Mixin、进度及语言资源。外观主仓库负责资源、编辑器、通用渲染和外观同步，通过公共 API 为玩法提供表现能力。

客户端通过 `top.csituka.magicaland.api` 的公开契约对接外观：只读外观查询、可撤回的手持隐藏/注视覆盖及视觉入口。外观内部配置、缓存、骨骼和合成状态由外观包管理。

服务端判定能力授权、交互权限与结果，管理距离、遮挡、碰撞、库存、死亡和伤害规则。能力授权独立于玩家的捏脸选择。

两个 Mod 分别维护版本，通过 Maven 发布物对接。Gameplay 编译仅使用 API 产物，运行实现随外观 JAR 提供一次；两个仓库保持各自的构建流程。

客户端和服务器均需安装兼容的外观与 Gameplay。外观可以单独使用，外观同步的服务端实现也包含在外观 JAR 中。

## 开发入口

使用 Gradle 9.4.1 wrapper、Loom 1.16.3，Java 输出目标为 17。先在外观仓库发布开发产物：

```powershell
.\gradlew.bat publishMavenJavaPublicationToLocalDevelopmentRepository
```

再在 Gameplay 仓库提供该发布目录：

```powershell
.\gradlew.bat build -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
.\gradlew.bat runClient -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
```

也支持外观 `publishToMavenLocal`。两个仓库可分别打开或放在同一编辑器工作区，各自使用 `run/client`、`run/server`，保持正在使用的存档相互独立。

文档按「三族能力」「成就系统」组织；具体能力和成就条目维护各自的操作说明与测试记录。设计草案注明当前实现状态，历史报告保留当时的版本与验证范围。

## 来源与存档兼容

来源：原仓库保全 `d4786bd`，初始导入 `d6166f3`；保留原作者和 [MIT 许可](../LICENSE.txt)，早期历史在 [Magical-Land](https://github.com/Magical-Land-Official/Magical-Land)。

拆仓期间保留了模组 ID `magicaland_gameplay`、进度 `magicaland:not_what_i_meant` 与条件 `misunderstanding`、`magicaland_remote_cargo` 和 NBT 字段、远控通道/实体 ID、授权标签。

后续出窍重构采用独立的 v2 控制通道，客户端和服务器需同步升级。库存沿用 `magicaland_remote_cargo` 并原位读取旧格式；模组、进度、实体 ID 与授权标签继续保留。当前契约见[出窍说明](remote-presence.md)，验证见[重构检查](reports/2026-09-10-remote-rebuild.md)。

## 迁移验证（2026-09-10）

全部 26 个 Gameplay 生产 Java 源码在真实 Minecraft/Fabric 依赖下，仅使用公开 API 类组成的临时开发命名空间测试 JAR 编译通过；编译 classpath 不包含外观内部实现。架构 169 项、远控结构 77 项检查通过，Gameplay 公共/服务端代码与原快照保持一致。

方向/飞行、彩蛋状态/顺序/食物、携带槽共 5 项无窗口回归通过，合计 727,810 条检查。使用当前源码、真实外观 0.3.0/Gameplay 0.1.0 元数据及 Fabric 类加载器的隔离夹具，验证了 18 个 Mixin 目标转换；没有进入游戏主循环，不等于正式发布 JAR 的重映射或游戏效果验收。

在本节记录的迁移检查阶段，Gradle 文件语法检查已通过，完整构建、发布和游戏测试尚未执行。后续需先发布外观 `0.3.0` 的主模组和 API 文件，再验证 Gameplay 的 Maven 依赖解析与实机表现。正式发布 JAR 的验证另行记录。
