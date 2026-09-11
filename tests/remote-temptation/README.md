# 远控食品吸引

检查脚本由维护者本地保存，下方命令在本地维护快照中运行。

`RemoteBrainTemptationTest` 使用简化世界对象运行实际的远控关联代码，检查普通玩家优先、按动物使用原版食品条件，以及换槽、回航、遮挡、超距、死亡、掉线和换世界后的关联清理。还检查 Mixin 的注入方法和跟随目标，防止动物转去追玩家本体。

```powershell
./tests/remote-temptation/run-tests.ps1 -OutputDirectory C:/临时验证目录
```

测试不会下载依赖、构建模组或启动游戏。简化对象只验证关联生命周期；Minecraft 的真实 Mixin 转换另行检查，动物寻路、繁殖与恐惧反应仍需进游戏验收。

提供 `-DependencyClasspathFile` 指向已有依赖清单时，还会使用缓存的 Sponge Mixin 运行 `RemoteTemptGoalTest`。它直接调用实际 Mixin 的选择与继续方法，检查普通玩家中途接管、无冷却转交、角度重新对齐和远控恐惧基准不被逐刻重置。原版 `start()` 在此仅记录调用，真实原版方法的插入和执行仍由 Minecraft 类转换检查与实机验收覆盖。
