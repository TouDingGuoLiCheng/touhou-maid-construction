# 低层禁用脚手架规划 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use `- [ ]` for tracking.

**Goal:** 相对待建第一层（`startLayer` 滤后 `layerIndex` 从 0 起）的第 1、2 层禁用脚手架规划；第 3 层起恢复。

**Architecture:** 纯策略 `ScaffoldNeedPolicy.allowsScaffoldPlanning(layer)`；`ScaffoldService.tryScaffold` 与 `MaidConstructionBehavior` 不可达/强行搭架路径共用该闸门。够不着时走现有无架恢复（直接放置/延后），不搭临时架。

**Tech Stack:** Java 17、JUnit 5（`src/unitTest`）

---

### Task 1: 策略 + 单测

**Files:**
- Modify: `src/main/java/.../ScaffoldNeedPolicy.java`
- Modify: `src/unitTest/java/.../ScaffoldNeedPolicyTest.java`

- [x] 测试：layer 0/1 false，≥2 true，负数 false
- [x] 实现 `allowsScaffoldPlanning`
- [x] 跑单测绿

### Task 2: 接入入口

**Files:**
- Modify: `ScaffoldService.java`（tryScaffold 入口）
- Modify: `MaidConstructionBehavior.java`（强行搭架条件 + 不可达体）

- [x] 低层跳过规划，走无架恢复
- [x] 文档：`docs/规划/04/.../何时触发.md`、`版本改动.md`
