# 开发与仓库边界

[文档目录](README.md) → 开发与维护

Gameplay 负责三族能力与成就系统，包括玩法规则、服务端判定、客户端操作和 HUD。[Magical Land 主模组](https://github.com/Elysian-Herd-Studio/Magical-Land)负责模型资源、捏脸、通用渲染和外观同步，通过公共 API 与 Gameplay 对接。

客户端使用 `top.csituka.magicaland.api` 查询外观，并注册可撤回的手持隐藏、注视、角翅限制和飞行姿态。主模组管理内部配置、缓存、骨骼和纹理合成；Gameplay 不直接依赖这些实现。

服务端判定能力授权、交互权限与结果，管理距离、遮挡、碰撞、库存、死亡和伤害规则。能力授权独立于玩家的捏脸选择。

Gameplay 0.3.4 对接 Magical Land 0.3.6 的公共 API 1.6，兼容范围为 `>=0.3.6 <0.4.0`。种族身份和服务器规则由 Gameplay 保存，角翅通过临时覆盖显示，不改写玩家的外观预设。天马运动由 Gameplay 结算，主模组呈现拍翼、滑翔和回弹。选族与改族流程见[种族系统](races.md)。

两个模组独立维护版本和构建流程，通过 Maven 产物对接。Gameplay 编译时只依赖公共 API；运行时由 Magical Land JAR 提供实现。

Magical Land 可以单独使用，其 JAR 包含外观同步服务端代码。使用 Gameplay 时，客户端和服务器都需要安装这两个模组，详见[安装说明](../README.md#安装)。

## 开发入口

使用 Gradle 9.4.1 wrapper、Loom 1.16.3，Java 输出目标为 17。先在 Magical Land 主仓库发布开发产物：

```powershell
.\gradlew.bat publishMavenJavaPublicationToLocalDevelopmentRepository
```

再在 Gameplay 仓库提供该发布目录：

```powershell
.\gradlew.bat build -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
.\gradlew.bat runClient -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
```

主模组也支持 `publishToMavenLocal`。两个仓库可放在同一编辑器工作区，各自使用 `run/client`、`run/server`，测试存档相互独立。

## 来源与存档兼容

本仓库从原项目 `d4786bd` 拆分，首次导入为 `d6166f3`。作者记录与 [MIT 许可](../LICENSE.txt)一并保留，早期历史见 [Magical Land 主仓库](https://github.com/Elysian-Herd-Studio/Magical-Land)。

拆仓期间保留了模组 ID `magicaland_gameplay`、进度 `magicaland:not_what_i_meant` 与条件 `misunderstanding`、`magicaland_remote_cargo` 和 NBT 字段、远控通道/实体 ID、授权标签。

后续出窍重构采用独立的 v2 控制通道，客户端和服务器需同步升级。库存沿用 `magicaland_remote_cargo` 并原位读取旧格式；模组、进度、实体 ID 与授权标签继续保留。当前契约见[出窍说明](remote-presence.md)，验证见[重构检查](reports/2026-09-10-remote-rebuild.md)。

### 迁移验证（2026-09-10）

全部 26 个 Gameplay 生产 Java 源码在真实 Minecraft/Fabric 依赖下，仅使用公开 API 类组成的临时开发命名空间测试 JAR 编译通过；编译 classpath 不包含外观内部实现。架构 169 项、远控结构 77 项检查通过，Gameplay 公共/服务端代码与原快照保持一致。

方向/飞行、彩蛋状态/顺序/食物、携带槽共 5 项无窗口回归通过，合计 727,810 条检查。使用当前源码、真实外观 0.3.0/Gameplay 0.1.0 元数据及 Fabric 类加载器的隔离夹具，验证了 18 个 Mixin 目标转换；没有进入游戏主循环，不等于正式发布 JAR 的重映射或游戏效果验收。

在本节记录的迁移检查阶段，Gradle 文件语法检查已通过，完整构建、发布和游戏测试尚未执行；上述结果只覆盖当时的迁移检查。
