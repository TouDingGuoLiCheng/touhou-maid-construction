# Ignore Overlaps Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hub 工程页增加「忽略重叠方块」开关；开启后可破坏重叠不算选址冲突且女仆可清障，基岩等不可破坏仍硬冲突。

**Architecture:** 工程字段 `ignoreOverlaps` 与 `coverTerrain` 独立。`PlacementRules.isConflict` / `CoverTerrainClearPolicy.mayClear` 扩展策略参数；清障仍走 `CoverTerrainClear` 挂点。不可破坏判定：`Block.defaultDestroyTime() < 0`。

**Tech Stack:** Forge 1.20.1、现有 unitTest（Minecraft-free policy + PlacementRules 测）

## Global Constraints

- 忽略与覆盖互不依赖；覆盖仍只控锚点贴地替换
- 基岩等不可破坏：选址硬冲突 + 永不挖
- 脚手架 / 已匹配蓝图格：仍不清障
- 默认关；NBT `IgnoreOverlaps`

---

### Task 1: Conflict + clear policy (TDD)

**Files:**
- Modify: `CoverTerrainClearPolicy.java`, `PlacementRules.java`
- Test: `CoverTerrainClearPolicyTest.java`, new `PlacementRulesConflictTest.java`

- [x] Extend `mayClear(..., ignoreOverlaps, destroyable)` — clear when `(cover&&coverable)||(ignore&&destroyable)` plus existing guards
- [x] Add `isUnbreakable` / `isConflict(..., ignoreOverlaps)` — ignore skips conflict unless unbreakable
- [x] Unit tests red→green

### Task 2: Persist + job flag

**Files:** `BuildProject.java`, `BuildJob.java`, `BuildJobService.java`, callers constructing `BuildJob`

- [x] `ignoreOverlaps` getter/setter + NBT
- [x] Pass into `BuildJob` / clear paths

### Task 3: Site pick + network + Hub UI

**Files:** `ConflictScanner`, `SitePickController`, packets, `ConstructionHubScreen`, lang zh/en

- [x] Scan/confirm/start-pick carry `ignoreOverlaps`
- [x] Hub toggle button + `SET_IGNORE_OVERLAPS` action
- [x] Hint strings when ignore on

### Task 4: Docs + jar

- [x] `版本改动.md` + `docs/规划` 相关主题
- [x] `gradlew jar` + unit tests
- [x] Commit on `exp/construction`
