# Magical Land Gameplay

[English](README_EN.md) | 简体中文

Magical Land 的独立玩法 Addon。外观主模组位于 [Magical-Land](https://github.com/Magical-Land-Official/Magical-Land)，本仓库不复制模型、贴图、动画或捏脸代码。

## 内容与安装

- 实验性独角兽念力出窍：能力轮盘、带碰撞飞行、远程交互、唯一携带槽及本体注视表现。
- 「不是这个意思！」金胡萝卜马匹互动彩蛋。
- [三族核心能力方案](docs/tribe-core-abilities.md)中尚未实现的部分仍是规划。

最新出窍修订仍待游戏验收，见[使用说明和限制](docs/remote-presence.md)。

Minecraft Fabric 1.20.1。客户端和服务器均安装 Gameplay、兼容外观主模组及其依赖。外观不依赖本 Addon；同步仍由同一个外观 JAR 的服务端代码提供，没有第三个同步安装包。

本仓库从 `0.1.0` 开始，初始开发目标是外观 `0.3.0`、公共 API v1，运行兼容范围见 `src/main/resources/fabric.mod.json`。两包版本号无需相同；玩家不要手动安装 API 编译 JAR。

Addon 注册自定义投影实体，不宣称只装服务端即可让原版客户端加入。旧一体包不能与外观主模组并装。

## 开发

使用 Gradle 9.4.1 wrapper、Loom 1.16.3，Java 输出目标为 17。先在外观仓库发布开发产物：

```powershell
.\gradlew.bat publishMavenJavaPublicationToLocalDevelopmentRepository
```

再在本仓库提供发布目录：

```powershell
.\gradlew.bat build -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
.\gradlew.bat runClient -PappearanceMavenRepo=C:/absolute/path/to/appearance/build/repo
```

也支持外观 `publishToMavenLocal`。命令仅为开发入口，不表示已构建或完成游戏验收。主 Mod 是运行依赖，编译针对公共 API；不得依赖外观源码路径、配置管理器、网络缓存或内部渲染类。

两个仓库可以分开打开或放入同一个编辑器工作区；各自使用 `run/client`、`run/server`，不要共享正在使用的存档。

## 来源与兼容

模组 ID 保持 `magicaland_gameplay`；进度 `magicaland:not_what_i_meant`、条件 `misunderstanding`、携带槽存档 `magicaland_remote_cargo`、原通道和授权标签不变。

来源为原仓库工作区保全 `d4786bd`，本仓库首次导入 `d6166f3`。保留原作者和 MIT 许可，早期完整历史仍在外观仓库。见[接口与仓库边界](docs/repository-boundary.md)。
