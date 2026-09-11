# Gameplay 文档

玩法包围绕三族能力与成就系统逐步开发。这里按系统整理设计、当前功能与维护记录；安装和通用入口见[项目首页](../README.md)。

## 三族能力

[种族选择](races.md)说明首次选族、改族药水、外观匹配和服务器规则。身份由玩法服务器保存，各族能力在此基础上逐步开放。

[三族核心能力设计](tribe-core-abilities.md)记录独角兽、天马与陆马的玩法方向。当前状态如下：

- **独角兽**：[念力出窍](remote-presence.md)支持遥控物品、互动和携物回航，本轮接入的 9 槽、128 格范围与精神回声已提供测试版；[自我悬浮](unicorn-levitation.md)提供按住托举、Shift 定高、落地缓冲和液面悬浮。目标标记、照明与御物指令见[后续方案](unicorn-ability-plan.md)。当前暂停魔力限制。
- **天马**：飞行与气流操控属于设计阶段，详见三族草案。外观包的飞行动画与玩法能力分别维护。
- **陆马**：[震动感知](earth-sense.md)采用下蹲专注、灰白微模糊视野和地面活动色团，支持慢蹲移动与停步恢复；本轮体验验收通过。后踢与地面攻击等用户补充动画后继续制作；自然共鸣仍在设计中。

2026-09-11 确认统一开发原则：各技能先完成并开放完整功能，再安排成长等级与解锁。感知和悬浮已做的功能全部可用；矿物感知等尚未开发的功能继续列为计划。

## 成就系统

[成就系统说明](achievements.md)汇总已实现的成就及其规则文档。具体互动、触发条件与历史测试保留在各条目下。

## 开发与维护

- [开发入口与仓库边界](repository-boundary.md)：公共 API、双仓构建、存档兼容和拆仓记录。
- [待办清单](../TODO.md)：按系统整理后续工作。
- [客户端设置回归](../tests/config/README.md)：独立设置存取、启动迁移与界面处理器。
- [种族系统回归](../tests/race/README.md)：身份规则、持久保存、消息校验与权限边界。

## 验证记录

报告按当时版本保留，当前功能规则以对应系统文档为准。

- 种族系统：[首次选择、药水与服务器规则](reports/2026-09-10-race-selection.md)。
- 陆马能力：[基础震动感知](reports/2026-09-10-earth-sense.md)、[专注与色团效果](reports/2026-09-10-earth-sense-focus.md)、[边缘提示与过渡声](reports/2026-09-10-earth-sense-edge-audio.md)、[慢蹲感知与进退节奏](reports/2026-09-10-earth-sense-moving.md)。

- 独角兽能力：[出窍重构](reports/2026-09-10-remote-rebuild.md)、[反馈修复](reports/2026-09-10-remote-followup.md)、[物品朝向与拾取修复](reports/2026-09-10-remote-pickup-pose.md)、[精神回声与满级出窍](reports/2026-09-11-spiritual-echo.md)、[携物回航](reports/2026-09-11-remote-return.md)、[回声、动物互动与托举试玩修复](reports/2026-09-11-remote-playtest-fixes.md)。这些记录保留各阶段的检查范围；后续已构建测试版，2026-09-11 用户同意提交本轮改动，多人和兼容性验证继续保留。
- 独角兽悬浮：[初版独立检查](reports/2026-09-10-unicorn-levitation.md)、[首轮试玩修订](reports/2026-09-11-levitation-controls.md)、[开关、保护与惯性修订](reports/2026-09-11-levitation-protection.md)。后续液面缓冲修订已完成独立检查及隔离测试版构建，2026-09-11 用户同意推送当前版本；多人及兼容性验证继续保留。
- 成就系统：具体条目的[早期实现与验收记录](carrot-misunderstanding.md)。
