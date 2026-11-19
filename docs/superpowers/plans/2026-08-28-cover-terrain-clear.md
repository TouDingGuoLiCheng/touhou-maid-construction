# Cover-terrain obstruction clear Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** In cover-terrain mode, construction maids instantly break blueprint-AABB coverable terrain that blocks the current task’s line of sight or work stand, depositing drops into the terminal vault.

**Architecture:** A Minecraft-free `CoverTerrainClearPolicy` owns may-clear / tick budget / exclusive AABB. `CoverTerrainClear` uses clip + `destroyBlock(false)` + `MaterialVaultHelpers.deposit`. `BuildJob` stores `coverTerrain`. Behavior calls clear from move / no-stand / before place. No per-cell ledger, no DIGGING phase.

**Tech Stack:** Minecraft 1.20.1 Forge, JUnit 5 unitTest source set, existing `PlacementRules.isTerrainCoverable`.

## Global Constraints

- Do not bump `mod_version`.
- Experimental branch `exp/construction` only; local commit after the change.
- Debug logs stay behind `-Dtouhou_maid_construction.debug=true`.
- Only `isTerrainCoverable` blocks may be broken; never bedrock / player builds outside that set.

---

### Task 1: CoverTerrainClearPolicy

**Files:**
- Create: `src/unitTest/java/com/touhoumaid/construction/build/CoverTerrainClearPolicyTest.java`
- Create: `src/main/java/com/touhoumaid/construction/build/CoverTerrainClearPolicy.java`

**Interfaces:**
- Consumes: none
- Produces: `CoverTerrainClearPolicy.MAX_CLEARS_PER_TICK` (2); `inExclusiveAabb(...)`; `mayClear(coverTerrain, inBounds, scaffold, matchesPlanned, terrainCoverable)`; `remainingClears(alreadyThisTick)`

- [x] **Step 1: Write the failing tests** (TDD)
- [x] **Step 2: Implement policy**
- [x] **Step 3: `./gradlew unitTest --tests com.touhoumaid.construction.build.CoverTerrainClearPolicyTest`**

### Task 2: Job flag + world clear + behavior

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/build/CoverTerrainClear.java`
- Modify: `BuildJob.java` (field + ctor + getters + `alreadyBuilt` / `taskAt`)
- Modify: `BuildJobService.java` (pass `project.coverTerrain()`)
- Modify: `MaidConstructionBehavior.java` (doMove / assignClaimedTask / handleUnreachableStand)
- Modify: `BuildJob.tryPlace` / `forcePlaceDirect` (harvest target cell first)
- Modify: `版本改动.md`
- Modify: `下一步的计划-覆盖地形清障.md` checkboxes when done

**Interfaces:**
- Consumes: Task 1 policy; `PlacementRules.isTerrainCoverable`; `MaterialVaultHelpers.deposit`
- Produces: `CoverTerrainClear.tryClearObstructions(level, job, maid, target, workStand)`; `tryHarvest(level, job, pos)`

- [ ] **Step 1: Wire coverTerrain on BuildJob**
- [ ] **Step 2: CoverTerrainClear harvest + clip**
- [ ] **Step 3: Call from behavior and place**
- [ ] **Step 4: Changelog + unitTest compile**
