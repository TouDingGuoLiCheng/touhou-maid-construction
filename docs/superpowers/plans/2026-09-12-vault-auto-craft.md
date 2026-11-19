# Vault Auto-Craft Implementation Plan

> **For agentic workers:** Execute task-by-task. Steps use checkbox syntax.

**Goal:** When a project enables auto-craft, missing vault materials are virtually crafted (crafting table + stonecutter, recursive) into the terminal vault before normal place/consume/bubble logic.

**Architecture:** Pure `VaultAutoCraftPlanner` over abstract item counts + recipe views; game `VaultCraftRecipes` builds views from `RecipeManager`; `AutoCraftingMaterialSource` wraps existing `MaterialSource` and calls ensure-on-miss; `BuildProject.autoCraftMaterials` (default false) + Hub toggle.

**Tech Stack:** Java 17, Minecraft 1.20.1 Forge, existing JUnit unitTest source set.

## Global Constraints

- Ingredients: vault only
- Recipes: crafting + stonecutter only
- Overflow: keep full recipe output
- Failure: identical to current missing-material behavior
- Do not bump `mod_version`

## File map

| File | Role |
|------|------|
| `build/VaultAutoCraftPlanner.java` | Recursive plan on `Map<String,Integer>` |
| `build/VaultCraftRecipeView.java` | Immutable recipe DTO for planner |
| `build/VaultAutoCraft.java` | Apply plan to `Container` via MC recipes |
| `build/VaultCraftRecipes.java` | Index crafting+stonecutter by result item id |
| `build/AutoCraftingMaterialSource.java` | Wrap `MaterialSource` |
| `project/BuildProject.java` | `autoCraftMaterials` flag + NBT |
| `network/TerminalActionPacket.java` | `SET_AUTO_CRAFT` |
| `client/ConstructionHubScreen.java` | Toggle on project page |
| `build/BuildJobService.java` | Wrap materials when starting/recreating jobs |
| `unitTest/.../VaultAutoCraftPlannerTest.java` | Planner tests |
| lang zh/en | Button labels |
| `docs/规划/02-.../缺料自动合成.md` | Planning topic |

---

### Task 1: Planner + unit tests

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/build/VaultCraftRecipeView.java`
- Create: `src/main/java/com/touhoumaid/construction/build/VaultAutoCraftPlanner.java`
- Create: `src/unitTest/java/com/touhoumaid/construction/build/VaultAutoCraftPlannerTest.java`

**Interfaces:**
- `VaultCraftRecipeView(String id, List<Ingredient> ingredients, String resultItemId, int resultCount)`
- `Ingredient(List<String> itemIds, int count)` — any one matching item id satisfies
- `VaultAutoCraftPlanner.plan(stock, targetItemId, needCount, recipesByResult, maxDepth) -> Optional<List<CraftStep>>`
- `CraftStep(recipeId, times)` — apply `times` crafts of that recipe in order

- [ ] **Step 1:** Write tests: log→4 planks need 1 succeeds with overflow; plank→stairs with only logs recurses; cycle fails; depth cap fails; prefer direct recipe over recursive when both exist
- [ ] **Step 2:** Implement planner (greedy: score recipes by how many ingredients already in stock without craft, then shallower recursion estimate; DFS with visited set on target item ids)
- [ ] **Step 3:** Run `./gradlew unitTest --tests com.touhoumaid.construction.build.VaultAutoCraftPlannerTest` — PASS
- [ ] **Step 4:** Commit planner

### Task 2: Apply to vault + MaterialSource wrapper

**Files:**
- Create: `VaultCraftRecipes.java`, `VaultAutoCraft.java`, `AutoCraftingMaterialSource.java`
- Modify: `BuildJobService.java` (wrap sources)

**Interfaces:**
- `VaultCraftRecipes.from(RecipeManager) -> index`
- `VaultAutoCraft.tryEnsure(Container vault, String blockId, VaultCraftRecipes recipes, int need) -> boolean`
- `AutoCraftingMaterialSource(MaterialSource inner, BooleanSupplier enabled, Supplier<VaultCraftRecipes> recipes, Supplier<Container> vault)` — `hasMaterial`/`tryConsume` ensure first when enabled

- [ ] **Step 1:** Implement recipe index (CraftingRecipe + StonecutterRecipe only)
- [ ] **Step 2:** tryEnsure: map blockId→item ids via `MaterialVault.matchItemIds`; if any already counted ≥ need return true; else plan+apply on vault copy then copy back; deposit overflow
- [ ] **Step 3:** Wrap in `start`/`recreateJob` paths; enabled from project flag; refresh recipes from level RecipeManager
- [ ] **Step 4:** Commit

### Task 3: Project flag + Hub + packet

**Files:**
- Modify: `BuildProject.java`, `TerminalActionPacket.java`, `ConstructionHubScreen.java`, lang files
- Update running job wrapper enabled flag when toggled

- [ ] **Step 1:** Field `autoCraftMaterials` default false; save/load `AutoCraftMaterials`
- [ ] **Step 2:** Action `SET_AUTO_CRAFT` like cover terrain; sync live job if present
- [ ] **Step 3:** Hub page 2 toggle button + zh/en
- [ ] **Step 4:** Commit

### Task 4: Docs

- [ ] Planning topic + overview row; `版本改动.md` 新增内容条；mark milestones in 下一步的计划
- [ ] Commit
