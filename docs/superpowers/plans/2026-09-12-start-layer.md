# Start Layer Construction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 工程可设「起始层 N」；低于蓝图局部 Y=N 的格不预览、不算料、不施工、不挡完工。

**Architecture:** 纯逻辑 `StartLayerPolicy` 为单一真相；`BuildProject.startLayer` 持久化；幽灵/冲突/材料/任务生成共用 `includesLocalY`；开工后拒绝改 N；无待建格禁止开工。

**Tech Stack:** Forge 1.20.1、JUnit5 `unitTest`、现有 Hub/Packet 模式

## Global Constraints

- N 相对蓝图局部 Y（region `y`），不是 Job `layerIndex`
- 默认 0；旧档缺字段当 0
- 仅开工前可改（`BUILDING`/`PAUSED` 拒绝）
- 跳过层完全当不存在（材料/进度/完工）
- 不抬锚点、不为跳过层主动清障

---

### Task 1: StartLayerPolicy（TDD）

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/build/StartLayerPolicy.java`
- Test: `src/unitTest/java/com/touhoumaid/construction/build/StartLayerPolicyTest.java`

**Produces:**
- `int clamp(int startLayer, int height)`
- `boolean includesLocalY(int localY, int startLayer)`
- `boolean canStartBuild(int startLayer, int height, boolean hasIncludedNonAir)`

- [ ] **Step 1:** 写失败单测（夹紧、includes、无格不可开工）
- [ ] **Step 2:** `gradlew unitTest --tests StartLayerPolicyTest` 确认 RED
- [ ] **Step 3:** 最小实现
- [ ] **Step 4:** 单测 GREEN
- [ ] **Step 5:** Commit

```java
public final class StartLayerPolicy {
    private StartLayerPolicy() {}
    public static int clamp(int startLayer, int height) {
        int h = Math.max(1, height);
        return Math.max(0, Math.min(startLayer, h - 1));
    }
    public static boolean includesLocalY(int localY, int startLayer) {
        return localY >= Math.max(0, startLayer);
    }
    public static boolean canStartBuild(int startLayer, int height, boolean hasIncludedNonAir) {
        clamp(startLayer, height); // side-effect free validate path
        return hasIncludedNonAir;
    }
}
```

---

### Task 2: BuildProject 字段 + 任务生成过滤

**Files:**
- Modify: `BuildProject.java`（getter/setter/NBT `StartLayer`）
- Modify: `BlueprintTaskBuilder.java` — `build(..., int startLayer)` 跳过 `!includesLocalY(y, startLayer)`
- Modify: 所有 `BlueprintTaskBuilder.build` 调用点传入 `project.startLayer()`

- [ ] 字段默认 0；读写 NBT
- [ ] 任务生成过滤
- [ ] Commit

---

### Task 3: 冲突 / 材料 / 幽灵过滤

**Files:**
- Modify: `ConflictScanner.scan` 增加 `startLayer`，循环内 `continue` 若 `!includesLocalY(y, startLayer)`
- Modify: `SchematicFiles.summarize` 重载带 `startLayer`（按 x,y,z 计，跳过低层）
- Modify: Hub/材料刷新调用带 N 的 summarize
- Modify: `GhostBlockRenderer.render` + `SitePickController` 携带 `startLayer`
- Modify: `StartSitePickPacket` / confirm 路径传入 `startLayer`
- 更新所有 `ConflictScanner.scan` 调用方

- [ ] 三处过滤接线
- [ ] Commit

---

### Task 4: 网络 + Hub UI + 开工守卫

**Files:**
- Modify: `TerminalActionPacket` — `SET_START_LAYER`，`stringArg` 传绝对 N（服务端 clamp）
- Modify: `ConstructionHubScreen` — 选址页 −/+ 与 `N / 高度`；开工中禁用
- Modify: `START_BUILD` — 无过滤后非空气格则拒绝并提示
- Lang: `zh_cn.json` / `en_us.json`

- [ ] UI + packet + 开工守卫
- [ ] Commit

---

### Task 5: 文档收尾 + 回归

**Files:**
- `版本改动.md`
- `docs/规划/01-.../贴地覆盖与朝向旋转.md`
- `docs/规划/03-.../任务绑定与分层建造.md`
- `下一步的计划-起始层施工.md` 勾选完成项

- [ ] `gradlew unitTest` + `jar`
- [ ] 文档
- [ ] Commit
