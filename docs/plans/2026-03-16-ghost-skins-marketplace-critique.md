**[INVARIANT] Renderer accepts impossible skin shapes**
- **Failure mode:** A skin with a non-`12x12` grid, out-of-range pixel indices, or missing blink-related colors installs successfully and then crashes or misrenders when the mascot is previewed or activated.
- **Where in plan:** `Skin Data Model (Kotlin)` — `grid: List<List<Int>> // 12x12, values index into colorMap`; `colorMap: Map<Int, Long>`; `Open Questions` item `1. Skin validation`
- **Severity:** blocking
- **Suggested resolution:** Define install-time validation rules for grid dimensions, color index bounds, required palette entries, and animation fields, and reject invalid skins before persistence.

**[INVARIANT] ASCII coverage is not constrained to real runtime states**
- **Failure mode:** The bridge emits an `AgentState` that has no `asciiFrames` entry or has mismatched frame shapes, so `SimpleStatus` or `SessionFullScreen` renders blank output, indexes missing frames, or flickers on state transitions.
- **Where in plan:** `Skin Data Model (Kotlin)` — `asciiFrames: Map<AgentState, List<String>>`; `UI Integration` — screens affected include `SimpleStatus` and `SessionFullScreen`
- **Severity:** blocking
- **Suggested resolution:** Specify whether every runtime `AgentState` requires frames and define a mandatory fallback when a skin omits a state or uses inconsistent frame dimensions.

**[TEMPORAL] Active-skin recovery path is undefined**
- **Failure mode:** `SharedPreferences` points to a skin that was deleted, half-written, or not loaded yet on startup, and `activeSkin` becomes invalid just as screens read theme values, producing startup crashes or inconsistent fallback behavior.
- **Where in plan:** `SkinEngine` — `active skin ID saved to SharedPreferences, skin JSON files stored on device`; `fun uninstallSkin(skinId: String)`
- **Severity:** blocking
- **Suggested resolution:** Define atomic fallback semantics so missing or removed active skins revert to `Skin.DEFAULT` before any observer can consume invalid state.

**[INTERFACE] `version` does not tell clients whether JSON is compatible**
- **Failure mode:** A newer bridge serves skins with an evolved JSON shape, but older clients only see `version: Int` and cannot distinguish “new content” from “new schema,” leading to parse failures or silent field loss.
- **Where in plan:** `Skin Data Model (Kotlin)` — `version: Int`; `Open Questions` item `2. Skin versioning`
- **Severity:** blocking
- **Suggested resolution:** Separate schema compatibility from skin content version and define explicit accept/reject behavior for unknown schema versions.

**[COUPLING] Billing rules conflict across sections**
- **Failure mode:** Different parts of implementation enforce different entitlements, so a free user may be told they have “full functionality” while animations stay disabled and installs remain gated, depending on which stage ships first.
- **Where in plan:** `Goal` item `6`; `Mascot Renderer` — `Free tier: animations disabled`; `Marketplace Screen` — `Free users can browse but install is gated`; `Staged Rollout` — `Stage 4: Billing restructure`
- **Severity:** blocking
- **Suggested resolution:** Lock a single entitlement matrix now and reference that same rule set in renderer, marketplace, and rollout stages.

**[INTEGRATION] Marketplace previews need data the list endpoint does not provide**
- **Failure mode:** The marketplace list needs a thumbnail rendered from skin data, but `GET /skins` returns only metadata, forcing one extra fetch per card or leaving previews empty, which makes the screen slow or incomplete as the catalog grows.
- **Where in plan:** `Bridge Skin Endpoints (Python/aiohttp)` — `GET /skins -> list metadata (id, name, author, isPremium)`; `Marketplace Screen` — `preview thumbnail`
- **Severity:** degrading
- **Suggested resolution:** Define what minimal preview payload is available from the list response or explicitly accept placeholder previews until full skin fetch completes.

**[INTERFACE] Shared CRUD endpoints have no collision or ownership contract**
- **Failure mode:** Two clients upload the same `id`, or one client deletes another client’s skin, causing silent overwrite/removal of marketplace entries for every connected user on that bridge host.
- **Where in plan:** `Bridge Skin Endpoints (Python/aiohttp)` — `POST /skins` and `DELETE /skins/{skin_id}`; `Open Questions` item `5. Community moderation`
- **Severity:** blocking
- **Suggested resolution:** Define ID uniqueness, overwrite behavior, and deletion authorization before exposing multi-client skin CRUD.

**[FALSIFIABILITY] Stage 2 has no proof that all hardcoded UI paths were removed**
- **Failure mode:** One rarely used screen or state path still references hardcoded colors or the old mascot renderer, but Stage 2 appears “done” until production users hit that path and see mixed themes.
- **Where in plan:** `Staged Rollout` — `Stage 2: Wire SkinEngine into ViewModel, thread skin through all UI`; `Key Risk`
- **Severity:** degrading
- **Suggested resolution:** Add explicit completion criteria for each affected screen/state so leftover hardcoded rendering paths are detectable before release.

### Survivability Assessment
- **Total critiques:** 8 (6 blocking, 2 degrading, 0 cosmetic)
- **Highest-risk area:** `Stage 2: Wire SkinEngine into ViewModel, thread skin through all UI` because it amplifies every unresolved contract problem—invalid skins, missing fallbacks, and inconsistent billing all surface across the entire app at once.
- **Top 3 risks** that would make you nervous during implementation
- Invalid or incomplete skin JSON reaching the renderer without enforceable validation
- Conflicting entitlement rules causing inconsistent behavior across marketplace and renderer
- Shared bridge CRUD semantics allowing collisions or destructive actions across clients
- **Verdict:** This plan does not yet survive contact with a real codebase. The first thing likely to break is not the rendering code itself but the contract around what counts as a valid, loadable, activatable skin. Once Stage 2 fans that contract through every screen, any ambiguity in validation, fallback, or entitlement handling turns into app-wide instability instead of a contained feature bug.