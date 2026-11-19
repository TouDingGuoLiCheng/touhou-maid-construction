# Material Vault Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add name+id search on the construction-terminal material vault GUI without rearranging or wiping the real 2700-slot vault.

**Architecture:** Keep the existing 27-slot `pageView` mirror. Empty query uses `VaultPageMirror` page math. Non-empty query builds a list of matching real indices and maps visible cells through `VaultSearchWindow`. Write-back only touches mapped indices. Client sends the query via a new C2S packet; page count syncs through extra `ContainerData`.

**模块契约（从已批准设计原样抄入，不得省略或改写）：**

- **`VaultItemSearch`（纯匹配）**
  - 第二次使用：菜单过滤 + 单元测试；本次不做通用到 Hub/采购。
  - 公开面：`matches(ItemStack, String query)`（及必要的 query 规范化：去空白、小写）。
  - 依赖方向：只依赖物品栈与字符串；不得依赖 Menu / Screen / 网络。
  - 故意不做：拼音、物品标签、模组显示名单独匹配。
- **材料库菜单过滤窗口（扩现有菜单，不做新通用容器库）**
  - 第二次使用：仅施工终端材料库菜单。
  - 公开面：设置查询、重建匹配下标、当前页可见窗口仍映到真实 vault 槽；无查询时行为与现网一致。
  - 依赖方向：可依赖 `VaultItemSearch`、现有 vault / 翻页状态；Screen 不得直接改 vault。
  - 故意不做：客户端私自改槽位映射；搜索时自动整理真实库。
- **搜索同步包（仿 `VaultScrollPacket`）**
  - 第二次使用：仅材料库搜索。
  - 公开面：客户端 → 服务端传递 query 字符串。
  - 依赖方向：现有网络通道；不反向依赖 Screen 细节。
  - 故意不做：服务端把完整匹配列表推给客户端（客户端仍靠槽位同步看到当前页）。

**Tech Stack:** Forge 1.20.1, existing vault menu/screen, JUnit `unitTest` (no Minecraft types in JVM tests).

## Global Constraints

- 过滤是视图：不得重排、压紧、清空未显示的真实格。
- 写回只打已映射槽；尾部填充空格不写回。
- Shift 存入仍走整库 `MaterialVault.deposit`。
- 匹配：显示名或 registry id，忽略大小写，子串包含。
- 不做：自动整理、拼音、跨终端搜索。

---

### Task 1: `VaultItemSearch` string matching

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/inventory/VaultItemSearch.java`
- Test: `src/unitTest/java/com/touhoumaid/construction/inventory/VaultItemSearchTest.java`

**Interfaces:**
- Consumes: none
- Produces: `normalizeQuery(String)`, `matches(String displayName, String itemId, String query)`, `matches(ItemStack, String)`
- Must not depend on: Menu / Screen / network

- [x] **Step 1: Write failing tests** for empty query false, name substring, id substring, case-insensitive, empty stack false via empty name+id.
- [x] **Step 2: Run** `gradlew.bat unitTest --tests com.touhoumaid.construction.inventory.VaultItemSearchTest` — FAIL class missing.
- [x] **Step 3: Implement** `VaultItemSearch`.
- [x] **Step 4: Re-run tests** — PASS.
- [x] **Step 5: Commit**

### Task 2: `VaultSearchWindow` mapping and safe push

**Files:**
- Create: `src/main/java/com/touhoumaid/construction/inventory/VaultSearchWindow.java`
- Test: `src/unitTest/java/com/touhoumaid/construction/inventory/VaultSearchWindowTest.java`

**Interfaces:**
- Consumes: int-array vault stand-ins (same style as `VaultPageMirror`)
- Produces: `collectMatches`, `mappedVaultIndex` (-1 padding), `filterPageCount`, `maxPage`, `pullPage`, `pushPage` (unmapped cells ignored; unmatched vault cells unchanged)
- Must not depend on: Screen / network

- [ ] Tests: collect only matching non-zero; last-page padding -1; push filtered page does not overwrite unmatched slots; empty matches one empty page.
- [ ] Implement + unitTest PASS + commit.

### Task 3: Menu uses filter window + search packet

**Files:**
- Modify: `ConstructionTerminalMenu.java` pull/push/`maxVaultScrollPage`/`quickMoveStack`
- Create: `VaultSearchPacket.java`
- Modify: `ModNetwork.java` (append packet; bump PROTOCOL `3` → `4`)

**Interfaces:**
- Consumes: `VaultItemSearch.matches(ItemStack, String)`, `VaultSearchWindow.*`
- Produces: `setVaultQuery(String)`, extra ContainerData for search-active + page count
- Must not depend on: Screen internals

- [ ] Wire mapping; padding slots `mayPlace` false; after deposit/push while searching, rebuild match list then pull.
- [ ] Commit.

### Task 4: Screen search box + lang + docs

**Files:**
- Modify: `ConstructionTerminalScreen.java`
- Modify: `lang/zh_cn.json`, `lang/en_us.json`
- Modify: `版本改动.md`, `docs/规划/02-材料仓库与补给/材料库容量与翻页.md`, `docs/规划/02-材料仓库与补给/概览.md`, `下一步的计划-材料库搜索.md`

- [ ] EditBox, send `VaultSearchPacket`, title uses synced page count.
- [ ] Docs + changelog.
- [ ] `gradlew.bat unitTest`
- [ ] Commit.
