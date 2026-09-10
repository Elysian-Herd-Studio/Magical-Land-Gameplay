# 玩法客户端设置回归

```powershell
./tests/config/run-tests.ps1 -GsonJar <Gson依赖jar> -ModMenuJar <ModMenu 7.2.2 命名映射jar>
```

需要 JDK 17+，可用 `-JavaBin` 指定。直接编译当前玩法配置、设置页及 Mod Menu 入口，使用真实 Gson 和 Mod Menu 接口。FabricLoader 只替换配置目录，Minecraft 控件用轻量桩；不运行 Gradle 或启动游戏。

覆盖真实 main 初始化的客户端提前迁移与服务端不写配置、外观客户端随后重存配置时旧选择不丢失、默认值、旧外观设置只读迁移、已有玩法配置优先、未知字段保留、异常配置与保存失败、实际按钮处理器即时保存、两种模式的中英文标签、连续点击不重建页面、完成/关闭返回原页面。测试数据仅写入新建临时目录；可用 `-OutputDirectory` 指定尚不存在的目录。

这是逻辑和存取回归，不覆盖游戏内绘制、键盘焦点或实际能力相机切换。
