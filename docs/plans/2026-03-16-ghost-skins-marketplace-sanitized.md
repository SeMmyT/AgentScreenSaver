# Skin System & Community Marketplace — Architecture Review

## Context

Android app (Kotlin/Compose, minSdk 26) that displays real-time status from a Python/aiohttp bridge server via SSE (Server-Sent Events). The app renders status in terminal-style panes with pixel-art mascot, ASCII art animations, and color themes.

## Goal

1. Replace current hardcoded mascot (12x12 Canvas pixel grid) with a skin-aware renderer
2. Build a skin data model: mascot pixel grid, color palette, ASCII frames per state, animation params
3. Create a SkinEngine singleton that manages active/installed skins via StateFlow
4. Add CRUD endpoints to the bridge server for community skin sharing (JSON files)
5. Build a marketplace screen in the Android app (browse/install/activate skins)
6. Restructure billing: free tier = full functionality, paid tier = cosmetic marketplace access

## Architecture

### Skin Data Model (Kotlin)

```
Skin:
  id: String
  name: String
  description: String
  author: String
  version: Int
  isPremium: Boolean
  mascot: MascotDef
    grid: List<List<Int>>     // 12x12, values index into colorMap
    colorMap: Map<Int, Long>  // pixel value -> ARGB
    animation: MascotAnimation
      breatheScale: Pair<Float, Float>
      wobbleOffset: Float
      bounceHeight: Float
      blinkIntervalMs: Int
  palette: SkinPalette
    accent: Long
    accentDeep: Long
    background: Long
    textPrimary: Long
    textSecondary: Long
    textTertiary: Long
  asciiFrames: Map<AgentState, List<String>>
```

Serialized as JSON. `Skin.DEFAULT` is a lazy singleton (built-in, non-premium).

### SkinEngine

```kotlin
class SkinEngine {
    val availableSkins: StateFlow<List<Skin>>
    val activeSkin: StateFlow<Skin>

    fun setActiveSkin(skinId: String)
    fun installSkin(skin: Skin)
    fun uninstallSkin(skinId: String)  // can't uninstall built-in
}
```

No persistence layer shown — active skin ID saved to SharedPreferences, skin JSON files stored on device.

### Mascot Renderer (Compose Canvas)

Reads `MascotDef` from active skin. Draws 12x12 grid with gap. Animations driven by `rememberInfiniteTransition`:
- Breathing: scale oscillation (idle/complete states)
- Wobbling: horizontal offset (thinking/tool_call)
- Bouncing: vertical offset (awaiting_input)
- Blinking: pixel value swap (pupil → eye color) on timer

Free tier: animations disabled (static render). Pro tier: full animations.

### Bridge Skin Endpoints (Python/aiohttp)

```
GET  /skins              -> list metadata (id, name, author, isPremium)
GET  /skins/{skin_id}    -> full skin JSON
POST /skins              -> upload skin JSON (requires id + name)
DELETE /skins/{skin_id}  -> remove (protected: can't delete built-in)
```

Storage: JSON files in a directory on the bridge host. No database.

### UI Integration

Every screen that renders mascot or uses theme colors receives `skin: Skin` parameter. Hardcoded color constants replaced with `Color(skin.palette.accent.toInt())`.

Screens affected: Dashboard (4-pane grid), SimpleStatus (ASCII mode), SessionFullScreen, SessionCard, Onboarding.

### Marketplace Screen

Scrollable list. Each skin card: name, author, preview thumbnail (GhostMascot rendered with that skin's palette), install/activate buttons. Free users can browse but install is gated.

### Staged Rollout

- Stage 1: Skin data model + default mascot + SkinEngine (core, no UI changes)
- Stage 2: Wire SkinEngine into ViewModel, thread skin through all UI (largest blast radius)
- Stage 3: Bridge CRUD endpoints + marketplace screen + upload
- Stage 4: Billing restructure (remove functional gates, add cosmetic gates)

## Open Questions

1. Skin validation — what prevents a malformed skin from crashing the renderer?
2. Skin versioning — how do we handle schema changes as the skin format evolves?
3. Offline skins — if bridge is unreachable, can users still use installed skins?
4. Skin preview — can we render a thumbnail without installing the full skin?
5. Community moderation — any skin uploaded to bridge is available to all connected clients?

## Key Risk

Stage 2 Task 7 (threading skin through all UI) has the largest blast radius — touches every screen file. Color constant replacement is mechanical but error-prone.
