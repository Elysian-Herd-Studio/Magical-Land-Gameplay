# 出窍与物理回航独立检查

检查脚本由维护者本地保存，下方命令在本地维护快照中运行。

`run-return-tests.ps1` 使用已有 Minecraft 开发命名空间依赖和 JDK，单独编译三个回航类及四个测试，不下载依赖、不运行 Gradle。输出必须位于仓库之外；每次运行创建独立目录，保存编译参数、四项日志及 `result.json`。

```powershell
.\tests\remote\run-return-tests.ps1 `
    -DependencyClasspathFile 'C:\verification\dependency-classpath.txt' `
    -OutputDirectory 'C:\verification\return-tests' `
    -JdkBin 'C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot\bin'
```

依赖文件是一行以系统路径分隔符分隔的现有类路径；Windows 使用分号。需要 Java 17 或更新版本，编译目标为 Java 17。

| 测试 | 范围 |
| --- | --- |
| `RemoteReturnNavigatorTest` | 来路、增量寻路、动态障碍、移动目标、缓存与预算限制。 |
| `RemoteReturnMotionTest` | 加减速、转向、速度限制和无效输入。 |
| `RemoteFlightCollisionTest` | 原版 `Box` 下的光团体积扫掠、薄墙、窄通道和细分预算。 |
| `RemoteReturnIntegrationTest` | 源码中的退出、回航、抵达结算、溢出、恢复和客户端边界。 |

这些检查覆盖算法、方块盒和源码边界，未启动 Minecraft 世界或图形窗口；通过不等于实机验收，也不覆盖真实区块加载、网络跟踪或注入后的完整世界行为。

完整库存、旧档和结算回归 `RemoteCargoInventoryTest`（当前 40,231 项）需要 Fabric Knot 类加载器，不包含在此普通类路径入口中。客户端动画、姿态与相机检查使用本地维护快照内的 `tests/remote-client/run-tests.ps1`，同样不替代实机验收。
