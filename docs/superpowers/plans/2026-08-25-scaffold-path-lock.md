# 脚手架路径锁定（一次算通，严格执行）

> 验收完成后把本文件勾选更新；全部完成后移入 `docs/归档/`。

## 目标

脚手架 SIM 成功一次后锁定整条路径；之后只按锁定路径增量 PLACE/CLIMB/APPROACH，不再每 tick 用当前脚底重算。仅当下一格放不进去，或女仆明显偏离锁定路径时才清空并重算。

## 你问-我回答

- Q: 偏离路径时？ A: B — 明显偏离才允许重算
- Q: 低处目标？ A: 寻路含下降；已有格走下去，不往上搭
- Q: 方案？ A: 锁定 path，执行层不调 Planner，直到 PLACE 被挡或离路径

## 文件

| 文件 | 职责 |
|------|------|
| `ScaffoldPathLockPolicy.java` | 是否偏离、是否应重算（纯逻辑） |
| `ScaffoldPathLockPolicyTest.java` | 单测 |
| `ScaffoldService.java` | `advanceLockedPath`：不调 Planner，只沿锁定 path 增量 |
| `MaidConstructionBehavior.java` | 有锁走 advance；偏离/放挡才 `tryScaffold` 重算 |

## 任务

- [x] Task 1: `ScaffoldPathLockPolicy` + 测试（偏离阈值、应否重算）
- [x] Task 2: `ScaffoldService.advanceLockedPath` + PLACE 失败返回可触发重算
- [x] Task 3: Behavior 接线：冷却 tick 不再 `tryScaffold` 重算；偏离/放挡才重算
- [x] Task 4: 版本改动（并入 0.1.0 脚手架结果）+ 实验分支 commit

## 验收

- [ ] 有锁定路径时日志不应每格出现新的 `SIM … from=更高Y`（需游戏内复测）
- [ ] PLACE 被挡后才会再次 `TRY`/`SIM`（需游戏内复测）
- [ ] 女仆掉下塔等明显偏离后允许重算（需游戏内复测）
- [x] 单元测试通过（ScaffoldPathLockPolicyTest / ScaffoldIncrementPolicyTest）
