# 设计：`/tmc debug` 运行时调试开关

> 已批准：2026-08-28。

## 目标

游戏内用命令开关施工调试日志，不必改 JVM 启动参数。

## 行为

- `/tmc debug on` / `off`：开/关
- `/tmc debug`：查询当前状态
- 权限：等级 2（OP）
- JVM `-Dtouhou_maid_construction.debug=true` 仍作为启动默认值；进游戏后可用命令覆盖
- 现有 `/tmc_blueprint` 不变；所有 `ConstructionDebug.enabled()` 调用自动生效

## 实现要点

- `ConstructionDebug`：可变运行时开关 + `setEnabled`
- `ConstructionCommands`：注册 `/tmc debug …`
