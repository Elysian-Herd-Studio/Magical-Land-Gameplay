# 陆马震动感知离线回归

`run-tests.ps1` 只运行九组服务端核心回归，不包含同目录的客户端数学、渲染结构或 GPU 测试；后者使用其独立 runner。

- `EarthSenseRulesTest`：14 格范围、距离单调强弱、独立活动强度、活动节奏、四类目标及预算。
- `EarthSenseLeaseTest`：开启、续租、60 tick 到期、关闭 token 墓碑、迟到/乱序输入和最大 token。
- `EarthSenseGroundTest`：真实搜索算法的平地、墙基、沟壑、悬空平台、支柱、半砖碰撞面与硬节点/探测预算。
- `EarthSenseProtocolTest`：v2 真实 Minecraft `PacketByteBuf` 往返、截断、非法布尔/字段/数量、重复短号、坐标/体型/活动的有限值与边界、关闭/离地状态；不包含实体 UUID 或原始实体 ID。
- `EarthSenseFocusTest`：两 tick 接地确认、移动与无伤轻推不退出、受伤/吸收、离地立即退出；旧攻击者自动清理不误报受伤。
- `EarthSenseViewerMotionTest`：实际位移采样、静止/慢蹲/快速移动的质量与范围、同 tick 重复采样、两 tick 变弱和十 tick 恢复、非法值与时间回退。
- `EarthSenseSignalsTest`：最近 32 个独立目标、稳定会话短号、离开删除与重新进入、位置/粗体型的 0.25 格量化、体型上限、独立活动脉冲和短号耗尽。
- `EarthSenseDamageApiTest`：读取真实 Minecraft `LivingEntity` 字节码，确认自身被击与主动攻击使用不同时间字段，并覆盖 `setAttacker(null)` 自动清理路径；不启动游戏。
- `EarthSenseIntegrationTest`：**仅源码接入守卫**，不等同于真实服务器事件、采样世界或多人网络验收。

```powershell
./tests/sense/run-tests.ps1 -DependencyClasspathFile '<named-dependencies.txt>' -OutputDirectory '<仓库外实验目录>' -AppearanceApiJar '<appearance-api-1.4-named.jar>'
```

需要 Java 17 或更新 JDK，及分号分隔的真实 Minecraft 1.20.1 named / Fabric 依赖清单；`-JdkBin` 可显式指定。可选 `AppearanceApiJar` 必须只含公开 Appearance API 类，提供时另做整个 Gameplay 的 Java 17 类型检查。脚本不运行 Gradle，不启动游戏，不读取玩家世界或配置；只在输出目录中新建一次性结果目录，保存日志与源码散列。

本轮现成依赖为工作区 `work/remote-rebuild-20260910/verification/dependency-classpath.txt`，v2 结果位于 `work/sense-focus-tests-20260910`；早期 v1 结果仍保存在 `work/sense-tests-20260910`，不能代替 v2 验证。

## 视觉回归

```powershell
./tests/sense/run-render-tests.ps1 -ClasspathFile '<named-dependencies.txt>' -JavaBin '<JDK bin目录>' -OutputDirectory '<新的实验输出目录>'
```

此入口分别检查方向与脉冲数学、实际滤镜着色器及 Minecraft 渲染接入位置。GPU 测试只创建隐藏的独立 OpenGL 上下文，不启动游戏，也不操作现有游戏窗口；需要依赖清单包含适合本机的 LWJGL native 库。

滤镜检查包括去饱和、alpha 与深度保留、状态恢复、重建尺寸、关闭和失败路径。它不代替整个游戏或第三方光影模组的兼容验收。

地面连通测试验证的是有限范围碰撞体近似：不证明任意模组方块的声学合理性，也不保证预算饱和或拥挤区域所有目标都被采样。仍需实机核对步行/奔跑/落地节奏、慢蹲减弱与停步恢复、受伤/离地退出、无伤轻推、失联关闭、跨维度/死亡/换族、不同地形及多玩家负载。v2 已取消旧版六 tick 离地容错，离地立即终止专注。
