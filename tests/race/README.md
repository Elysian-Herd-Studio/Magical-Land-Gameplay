# 种族系统离线回归

使用真实 Minecraft 1.20.1 named / Fabric 依赖与 Java 17 目标字节码，不调用 Gradle、不启动 Minecraft 游戏循环，不读取玩家配置或世界。NBT 文件只写入指定实验输出目录的新子目录。

- `RaceRulesTest`：三族角/翅膀映射、真实命令解析器完整读取带命名空间的 ID、注册表容量、模式与允许种族、首次选择/已有种族、管理员权限、乐观 revision 和溢出。
- `RaceStateTest`：真实 NBT 文件往返、未知合法种族、未来字段/等级数据保留、深复制、损坏规则锁定、损坏种族隔离和可恢复性。
- `RaceProtocolTest`：真实 `PacketByteBuf` 编解码；规则/玩家数量边界、全部截断、尾随字节、重复项、非法版本/模式/ID/长度，以及带正数 requestId 的独立成功/失败回执。
- `RaceIntegrationTest`：**源码结构检查**，确认真实事件绑定、服务端权限/revision校验、药水开始/结束两次检查、成功后才消耗、创造例外、离线/重生清理、能力入口门槛，以及客户端只接受对应请求的失败回执等接入仍存在。它不是实际运行服务端事件、喝药水或多人同步的证明。

## 重跑

准备 Java 17 或更新 JDK、已映射到 named 的 Minecraft/Fabric 依赖清单（Windows 下用分号连接 JAR 完整路径）。需要检查整个 Gameplay 编译边界时，另提供仅含 `top/csituka/magicaland/api/` 的 Appearance API 1.4 JAR，不要用完整 Appearance 实现替代。

```powershell
./tests/race/run-tests.ps1 -DependencyClasspathFile '<named-dependencies.txt>' -OutputDirectory '<独立实验目录>' -AppearanceApiJar '<appearance-api-1.4-named.jar>'
```

`-AppearanceApiJar` 可省略；此时只检查六个种族核心类与四组回归。`-JdkBin` 可指定本机 JDK 的 `bin`。输出目录必须在仓库外；每次创建独立子目录，保存参数、测试日志与源码 SHA-256，不删除旧结果。

当前实验依赖已在工作区 `work/remote-rebuild-20260910/verification/dependency-classpath.txt` 验证，包含 163 个真实缓存 JAR；本轮结果在 `work/race-tests-20260910`。独立 JVM 启动各测试，因此注册表容量用例不会污染其他测试或游戏。

仍需实机或真实服务器验收：首次选择提示、多玩家广播、OP即时变更、两次喝药检查的游戏时序、消耗/玻璃瓶、死亡重生以及跨维度/重连。离线通过不宣称这些场景已经运行过。
