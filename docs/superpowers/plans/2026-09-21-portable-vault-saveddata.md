# Portable Vault SavedData Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]` syntax for tracking).

**Goal:** Held construction terminals no longer carry the 2700-slot vault in item NBT; the item keeps a UUID key and the vault lives in overworld SavedData.

**Architecture:** `PortableVaultPolicy` migrates `BlockEntityTag.Items` into a UUID-keyed map and strips `Items` from the item. `PortableVaultSavedData` persists that map on the overworld data storage (not chunk tickets). Place copies the store into the block entity; break writes BE vault back to the store and drops an item with `VaultId` only. Client inventory sync uses `getShareTag` so leftover `Items` never go to the hotbar.

**Tech Stack:** Minecraft 1.20.1 Forge, JUnit unitTest source set (CompoundTag only).

## Global Constraints

- Do not bump `mod_version`.
- Item mode stays portable (no requirement to place the terminal).
- Block terminals keep vault on the block entity while placed.
- First version: creative-cloned items that share `VaultId` share one store (no copy-on-write).
- No orphan GC in v1.

---

### Task 1: Policy (TDD)

**Files:**
- Create: `src/unitTest/java/com/touhoumaid/construction/item/PortableVaultPolicyTest.java`
- Create: `src/main/java/com/touhoumaid/construction/item/PortableVaultPolicy.java`

- [x] Migrate embedded `Items` → store + `VaultId`; strip from item tag
- [x] Prefer non-empty store over leftover item `Items`
- [x] Share-tag copy strips `BlockEntityTag.Items`

### Task 2: World store + item IO

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/item/PortableVaultSavedData.java`
- Modify: `ItemTerminalData`, `ConstructionTerminalItem`, `ConstructionTerminalMenu`, `ItemTerminalMaterialSource`, `BuildJobService`, `ConstructionTerminalBE`, `TerminalActionPacket`

- [x] Overworld SavedData file `touhou_maid_construction_portable_vaults`
- [x] Server load/save vault by UUID; migrate legacy item `Items` on first access
- [x] `saveProjects` must not rewrite vault onto the item
- [x] Place: copy store → BE; pickup: BE → store, drop without `Items`
- [x] `getShareTag` strips `Items`

### Task 3: Docs

- [x] `版本改动.md` + `docs/规划/` 手持终端 / 材料库持久化边界
