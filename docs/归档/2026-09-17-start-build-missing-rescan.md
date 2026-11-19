# 开工扫缺块补放 Implementation Plan

> 已归档：2026-09-17。原路径：`docs/superpowers/plans/2026-09-17-start-build-missing-rescan.md`

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On Hub start and login BUILDING restore, rescan the world once and re-queue missing non-ignored schematic cells for normal maid placement.

**Architecture:** Reuse `BuildJob.syncAlreadyBuilt`. Add a small policy so post-scan progress uses world counts and does not skip pending via inflated NBT placed counts. Call rescan from `BuildJobService.start` (existing job) and restore kickoff/recreate paths.

**Tech Stack:** Java 17, Forge 1.20.1, JUnit 5 unitTest source set

## Global Constraints

- Experiment branch `exp/construction` only; do not bump `mod_version`
- Player-facing: re-queue only (no instant fill); layer gating stays `activeLayer`
- Chinese planning docs + `版本改动.md` on finish

---

### Task 1: Policy + unit tests

**Files:**
- Create: `src/main/java/.../BuildStartRescanPolicy.java`
- Create: `src/unitTest/java/.../BuildStartRescanPolicyTest.java`
- Modify: `BuildProgressRestore.java` / test if needed for world-authoritative project counts

- [x] RED: tests for (1) after repair rescan, do not skip pending from saved counts; (2) project placedByBlockId uses scanned map not max(saved, scanned)
- [x] GREEN: implement policy helpers
- [x] Commit

### Task 2: Wire BuildJobService

**Files:**
- Modify: `BuildJobService.java`
- Modify: `BuildJob.java` (optional: shrink `consumedMaterials` when matched placed shrinks on sync)

- [x] Existing-job `start`: `syncAlreadyBuilt` + write project from world
- [x] `kickoffRestoredBuildingProject`: rescan before refresh
- [x] `recreateJob`: keep `syncAlreadyBuilt`; stop `restorePlacedProgress` + `reconcileStalePending` from skipping missing cells; keep `restoreConsumedMaterials`
- [x] `refreshProjectProgressFromJob`: placed counts from job (world), not Math.max merge for placedByBlockId
- [x] Run `gradlew unitTest`
- [x] Commit

### Task 3: Docs

- [x] `版本改动.md`, `docs/规划/.../破坏补放与完成判定.md`, check off `下一步的计划-开工扫缺块补放.md`
- [x] Commit
