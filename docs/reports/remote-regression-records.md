# 念力出窍：历史回归记录

[文档目录](../README.md#验证记录) → 独角兽能力。收录各版本的检查范围；现行规则见[念力出窍](../remote-presence.md)。

0.2.0 的检查见[重构报告](2026-09-10-remote-rebuild.md)，0.2.1 的检查见[验收反馈修复](2026-09-10-remote-followup.md)。这些历史报告分别记录独立检查、构建和实机验收范围。

以下独立回归按本地 `tests/remote` 布局维护，不随仓库发布：

- `RemoteToolMathTest`：方向、归一化速度及本体头眼跟随。
- `RemoteSessionRulesTest`：完整功能下全遮挡继续交互且不深入强退、可显式启用的旧遮挡规则、长时间停留、横移、后退、惯性、输入有序性及幂等关闭。
- `RemoteProtocolTest`：v2 各类消息、长度、尾部数据、非法数值、会话和槽位边界及动作枚举。
- `RemoteCargoInventoryTest`：上限与 NBT 兼容、真实迁入、工具耐久、坏工具空槽、多槽及稀疏保存、旧存档迁移、恢复余量、满包和重复结算数量守恒。
- `RemotePickupTest`：真实掉落物拾取归属与投掷者区分、延迟、丢出后收回、容量与 NBT、平地/半砖和实际障碍物。
- `RemoteVisibilityTest`：固定大采样半径、竖直基底、墙角渐进、贴地/半砖/楼梯采样、真实隔墙与顶棚、玻璃光学与碰撞分离、遮光玻璃及装饰植物。
- `RemoteAttributesTest`：物品攻击属性投影、保留其他属性效果及不修改本体属性实例。
- `RemotePresentationTest`：客户端会话、输入/状态次序、选槽及视觉纯规则。
- `RemoteItemPoseTest`：工具斜向上、普通物品正面朝镜头且不镜像、左右手与模型显示旋转、立体物品竖直、挥动及角度跨界连续性。
- `RemoteAimMathTest`：第三人称目标投影、画面边界及遮挡时准星透明度。
- `RemoteStructureTest.mjs`：协议、权限、真实库存、公开 API 和作用域源码边界。

携物回航另有 `RemoteReturnNavigatorTest`、`RemoteReturnMotionTest` 和 `RemoteReturnIntegrationTest`，检查来路、绕障、移动目标、碰撞及服务端交付边界。`tests/remote-client/run-tests.ps1` 检查回航持物、实体缓存生命周期、状态时序和既有朝向数学。具体记录见[携物回航报告](2026-09-11-remote-return.md)。

2026-09-11 试玩修复的范围见[反馈记录](2026-09-11-remote-playtest-fixes.md)。本地动物吸引测试使用简化世界对象运行实际关联代码，检查普通玩家优先、食品条件和失效清理；回声的持续时间、换轮衔接与渲染状态由本地回声测试覆盖。

`tests/config/run-tests.ps1` 使用真实 Gson、Mod Menu 入口和实际设置页处理器，覆盖独立保存、旧选择提前迁移、外观随后重存、专用服务端不生成客户端设置、保存失败及重试。此前版本记录为 380 项检查通过，并完成全源码 Java 17 类型检查、296 项架构与 110 项远控边界检查。玩法编译仅依赖外观公共 API。

多人负载和第三方渲染兼容取决于实际服务器与模组组合。
