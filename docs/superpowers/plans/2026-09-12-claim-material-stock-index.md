# Claim Material Stock Index Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox syntax.

**Goal:** Stop server-tick stalls from `claimNearest` scanning ~2500 tasks × full vault by using an incremental item-count index plus short starve throttle when a layer has no stocked work.

**Architecture:** Pure `MaterialStockIndex` holds `itemId → count` and optional `blockId` negative/positive memo via `matchItemIds`. Vault deposit/consume adjust counts incrementally; auto-craft failure marks blockIds unavailable; `BuildJob.claimNearest` consults the index and applies starve (N=10 ticks) cleared on any stock mutation.

**Tech Stack:** Java 17, existing JUnit `src/unitTest`, Minecraft Forge material helpers (`MaterialVault.matchItemIds`).

## Global Constraints

- Claim order / stand spread / layer rules unchanged when stock exists
- No full vault rescan on every consume; only incremental count updates (+ rare rebuild fallback)
- Starve window: 10 ticks; cleared on deposit/consume/craft-index update
- Keep `[TMC-Perf]` instrumentation

---

## Task 1: MaterialStockIndex (TDD)

**Files:**
- Create: `src/unitTest/java/.../MaterialStockIndexTest.java`
- Create: `src/main/java/.../MaterialStockIndex.java`

- [x] Write tests: deposit increments; consume to zero clears has; hasForBlockId uses provided match ids; markUnavailable; clearStarve signal via `generation`/`revision`; rebuildFromCounts
- [x] Implement minimal index
- [x] Unit tests pass

## Task 2: Wire into MaterialSource / BuildJob claim

**Files:**
- Modify: `BuildJob.java` (`claimNearestOnLayer`, `hasPlaceableTaskOnActiveLayer`)
- Modify: `AutoCraftingMaterialSource.java` (on craft fail → markUnavailable; on successful craft deposit → applyDeposit)
- Modify: vault consume/deposit paths used by place (`MaterialVault` callers / `BlockTerminalMaterialSource` / job materials wrapper)

- [x] Job owns index; sync from vault on job start / first use (one rebuild)
- [x] Successful tryConsume → applyConsume for matched item ids
- [x] Deposit → applyDeposit
- [x] claim uses `index.hasMaterial(blockId, matchItemIds)` instead of raw vault scan when index ready
- [x] Starve: empty claim with requireMaterial → set starveUntil; skip while starving; clear on index mutation

## Task 3: Docs + verify

- [x] `版本改动.md` + `docs/规划/03-女仆施工行为/领单与多女仆协作.md` note
- [x] Run unit tests
- [ ] Commit on `exp/construction`
