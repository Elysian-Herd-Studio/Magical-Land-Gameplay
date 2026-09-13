# Gameplay 文档

这里收录能力说明、设计方案和维护记录。安装与入门见[项目首页](../README.md)。

## 三族能力

[种族选择](races.md)：首次选族、改族药水、角翅匹配和服务器规则。

- **独角兽**：[念力出窍](remote-presence.md)、[自我悬浮](unicorn-levitation.md)、[御物](telekinesis.md)。
- **天马**：[飞行](pegasus-flight.md)。
- **陆马**：[震动感知](earth-sense.md)。

后续玩法见[三族核心能力规划](tribe-core-abilities.md)和[独角兽能力规划](unicorn-ability-plan.md)。最初的单物品与单块采集阶段保存在[早期方案归档](archive/2026-09-09-tribe-stages.md)。

## 成就系统

[成就系统说明](achievements.md)：已加入的成就与触发条件。

## 开发与维护

- [开发入口与仓库边界](repository-boundary.md)：公共 API、双仓构建、存档兼容和拆仓记录。
- [待办清单](../TODO.md)：按系统整理后续工作。

## 验证记录

报告保留各版本的检查范围与结果，当前规则见对应系统文档。测试源码和脚本在本地维护，需要复跑时请联系维护者。

- 种族系统：[首次选择、药水与服务器规则](reports/2026-09-10-race-selection.md)。
- 陆马能力：[基础震动感知](reports/2026-09-10-earth-sense.md)、[专注与色团效果](reports/2026-09-10-earth-sense-focus.md)、[边缘提示与过渡声](reports/2026-09-10-earth-sense-edge-audio.md)、[慢蹲感知与进退节奏](reports/2026-09-10-earth-sense-moving.md)。
- 独角兽能力：[出窍重构](reports/2026-09-10-remote-rebuild.md)、[反馈修复](reports/2026-09-10-remote-followup.md)、[物品朝向与拾取修复](reports/2026-09-10-remote-pickup-pose.md)、[精神回声与满级出窍](reports/2026-09-11-spiritual-echo.md)、[携物回航](reports/2026-09-11-remote-return.md)、[回声、动物互动与托举试玩修复](reports/2026-09-11-remote-playtest-fixes.md)、[回归测试范围与维护入口](reports/remote-regression-records.md)。
- 独角兽悬浮：[初版独立检查](reports/2026-09-10-unicorn-levitation.md)、[首轮试玩修订](reports/2026-09-11-levitation-controls.md)、[开关、保护与惯性修订](reports/2026-09-11-levitation-protection.md)、[空中续按与视角纠正](reports/2026-09-11-levitation-repress.md)。
- 成就系统：具体条目的[早期实现与验收记录](carrot-misunderstanding.md)。
