# Claim Radar Cursor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop `claimNearest` from material-checking an entire layer (~1300 cells) each call; scan outward from a frozen center with a per-tick budget of 48 and a per-maid cursor that resumes next tick.

**Architecture:** Pure `ClaimRadarPolicy` owns window/cursor math. `BuildJob` caches a distance-sorted cheap candidate list per maid, freezes the search center for the pass, and only runs `hasMaterial` on the current window. Starve only after a full pass finds zero stocked cells.

**Tech Stack:** Java 17, JUnit 5, existing `ClaimScanBudgetPolicy` / `ClaimStarvePolicy`.

## Global Constraints

- Material checks per claim call: **N = 48**
- Cross-tick cursor resume until claim, full-pass starve, or stock/queue invalidation
- Do not change layer gating or claim mutex semantics
- Survive budget remains ≤48; stocked-order cap remains 96

---

### Task 1: ClaimRadarPolicy (pure)

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/build/ClaimRadarPolicy.java`
- Test: `src/unitTest/java/com/touhoumaid/construction/build/ClaimRadarPolicyTest.java`

**Interfaces:**
- Produces: `MAX_MATERIAL_CHECKS_PER_CLAIM = 48`, `nextWindow(cursor, size, budget)`, `shouldStarveAfterPass(...)`, reset helpers

- [x] **Step 1–4:** TDD window slicing, exhausted→cursor 0, starve only when pass exhausted and no stock accepted
- [x] **Step 5:** Commit on exp branch

### Task 2: Wire BuildJob.claimNearestOnLayer

**Files:**
- Modify: `BuildJob.java` (`claimNearestOnLayer`, per-maid radar state, invalidate on stock revision / pending shape)
- Modify: `ClaimScanBudgetPolicy.java` (doc note that material budget is radar-owned)
- Test: extend `ClaimRadarPolicyTest` / optional job-level if feasible without MC bootstrap

- [x] **Step 1–4:** Rebuild ordered list only when needed; material-check window only; clear cursor on claim; starve only on full pass
- [x] **Step 5:** Run unit tests; commit

### Task 3: Docs

**Files:**
- Modify: `版本改动.md`, `docs/规划/07-多人协作与任务分派/同层领单与防抢.md` (+ 概览 if needed)

- [x] Changelog + planning sync; commit
