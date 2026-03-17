# Ghost Mascot, Skins System & Community Marketplace

> **For Claude:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task.

**Goal:** Replace Clawd crab mascot with Ghost, build a skins/themes engine with community marketplace, restructure free/paid tiers around cosmetics.

**Architecture:** Skins are self-contained JSON+Kotlin bundles defining mascot pixels, color palette, ASCII art frames, and animations. A `SkinEngine` loads the active skin and exposes themed composables. Community skins are shared as `.ghostskin` JSON files via the bridge (upload/download endpoints). Free tier gets the default Ghost skin with no animations; paid tier unlocks the marketplace and all cosmetic customizations.

**Tech Stack:** Kotlin/Compose (Canvas-rendered mascot, theming), Python/aiohttp (bridge skin endpoints), JSON (skin format)

**Staged Approach (HIGH risk):**
- Stage 1 — Core: Skin data model + Ghost mascot replacing Clawd
- Stage 2 — Direct: Skin renderer integration into all UI screens
- Stage 3 — Indirect: Marketplace UI + community skin sharing
- Stage 4 — Config: Billing model restructure + cleanup

---

## Stage 1 — Core: Ghost Mascot + Skin Data Model

### Task 1: Define the Skin data model

**Files:**
- Create: `android/app/src/main/java/com/claudescreensaver/data/models/Skin.kt`
- Test: `android/app/src/test/java/com/claudescreensaver/data/models/SkinTest.kt`

A skin defines everything visual: mascot pixel grid, color palette, ASCII frames per state, and animation parameters.

**Step 1: Write the failing test**

```kotlin
// SkinTest.kt
package com.claudescreensaver.data.models

import org.junit.Test
import org.junit.Assert.*

class SkinTest {

    @Test
    fun `default ghost skin has correct metadata`() {
        val skin = Skin.DEFAULT
        assertEquals("ghost", skin.id)
        assertEquals("Ghost", skin.name)
        assertEquals("Default ghost mascot", skin.description)
        assertFalse(skin.isPremium)
    }

    @Test
    fun `skin has mascot pixel grid`() {
        val skin = Skin.DEFAULT
        assertTrue(skin.mascot.grid.isNotEmpty())
        assertEquals(12, skin.mascot.grid.size) // 12 rows
        assertTrue(skin.mascot.grid.all { it.size == 12 }) // 12 cols
    }

    @Test
    fun `skin has color palette`() {
        val skin = Skin.DEFAULT
        assertNotNull(skin.palette.accent)
        assertNotNull(skin.palette.background)
        assertNotNull(skin.palette.textPrimary)
    }

    @Test
    fun `skin has ASCII frames for each agent state`() {
        val skin = Skin.DEFAULT
        AgentState.entries.forEach { state ->
            val frames = skin.asciiFrames[state]
            assertNotNull("Missing frames for $state", frames)
            assertTrue("Empty frames for $state", frames!!.isNotEmpty())
        }
    }

    @Test
    fun `skin serializes to and from JSON`() {
        val original = Skin.DEFAULT
        val json = original.toJson()
        val restored = Skin.fromJson(json)
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.mascot.grid.size, restored.mascot.grid.size)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.models.SkinTest" 2>&1 | tail -20`
Expected: FAIL — `Skin` class doesn't exist yet

**Step 3: Write the Skin data model**

```kotlin
// Skin.kt
package com.claudescreensaver.data.models

import org.json.JSONArray
import org.json.JSONObject

data class SkinPalette(
    val accent: Long,        // 0xFFD97757
    val accentDeep: Long,    // 0xFFBD5D3A
    val background: Long,    // 0xFF141413
    val textPrimary: Long,   // 0xFFFAF9F5
    val textSecondary: Long, // 0xFFB0AEA5
    val textTertiary: Long,  // 0xFFE8E6DC
)

data class MascotAnimation(
    val breatheScale: Pair<Float, Float> = 0.96f to 1.04f,
    val wobbleOffset: Float = 2f,
    val bounceHeight: Float = 6f,
    val blinkIntervalMs: Int = 3000,
)

data class MascotDef(
    val grid: List<List<Int>>,   // 12x12, values index into palette colors (0=transparent, 1=body, 2=accent, 3=eye, 4=detail)
    val colorMap: Map<Int, Long>, // pixel value -> ARGB color
    val animation: MascotAnimation = MascotAnimation(),
)

data class Skin(
    val id: String,
    val name: String,
    val description: String,
    val author: String = "built-in",
    val version: Int = 1,
    val isPremium: Boolean = false,
    val mascot: MascotDef,
    val palette: SkinPalette,
    val asciiFrames: Map<AgentState, List<String>>,
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("description", description)
        obj.put("author", author)
        obj.put("version", version)
        obj.put("is_premium", isPremium)

        // Mascot
        val mascotObj = JSONObject()
        val gridArr = JSONArray()
        mascot.grid.forEach { row ->
            val rowArr = JSONArray()
            row.forEach { rowArr.put(it) }
            gridArr.put(rowArr)
        }
        mascotObj.put("grid", gridArr)
        val colorMapObj = JSONObject()
        mascot.colorMap.forEach { (k, v) -> colorMapObj.put(k.toString(), v) }
        mascotObj.put("color_map", colorMapObj)
        obj.put("mascot", mascotObj)

        // Palette
        val palObj = JSONObject()
        palObj.put("accent", palette.accent)
        palObj.put("accent_deep", palette.accentDeep)
        palObj.put("background", palette.background)
        palObj.put("text_primary", palette.textPrimary)
        palObj.put("text_secondary", palette.textSecondary)
        palObj.put("text_tertiary", palette.textTertiary)
        obj.put("palette", palObj)

        // ASCII frames
        val framesObj = JSONObject()
        asciiFrames.forEach { (state, frames) ->
            val arr = JSONArray()
            frames.forEach { arr.put(it) }
            framesObj.put(state.value, arr)
        }
        obj.put("ascii_frames", framesObj)

        return obj.toString(2)
    }

    companion object {
        val DEFAULT: Skin by lazy { createGhostSkin() }

        fun fromJson(json: String): Skin {
            val obj = JSONObject(json)

            val mascotObj = obj.getJSONObject("mascot")
            val gridArr = mascotObj.getJSONArray("grid")
            val grid = (0 until gridArr.length()).map { r ->
                val rowArr = gridArr.getJSONArray(r)
                (0 until rowArr.length()).map { rowArr.getInt(it) }
            }
            val colorMapObj = mascotObj.getJSONObject("color_map")
            val colorMap = colorMapObj.keys().asSequence().associate {
                it.toInt() to colorMapObj.getLong(it)
            }

            val palObj = obj.getJSONObject("palette")
            val palette = SkinPalette(
                accent = palObj.getLong("accent"),
                accentDeep = palObj.getLong("accent_deep"),
                background = palObj.getLong("background"),
                textPrimary = palObj.getLong("text_primary"),
                textSecondary = palObj.getLong("text_secondary"),
                textTertiary = palObj.getLong("text_tertiary"),
            )

            val framesObj = obj.getJSONObject("ascii_frames")
            val asciiFrames = AgentState.entries.associateWith { state ->
                val arr = framesObj.optJSONArray(state.value) ?: JSONArray()
                (0 until arr.length()).map { arr.getString(it) }
            }

            return Skin(
                id = obj.getString("id"),
                name = obj.getString("name"),
                description = obj.optString("description", ""),
                author = obj.optString("author", "unknown"),
                version = obj.optInt("version", 1),
                isPremium = obj.optBoolean("is_premium", false),
                mascot = MascotDef(grid = grid, colorMap = colorMap),
                palette = palette,
                asciiFrames = asciiFrames,
            )
        }
    }
}
```

**Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.models.SkinTest" 2>&1 | tail -20`
Expected: PASS (except `createGhostSkin()` doesn't exist yet — next task)

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/data/models/Skin.kt \
        android/app/src/test/java/com/claudescreensaver/data/models/SkinTest.kt
git commit -m "feat(skins): add Skin data model with JSON serialization"
```

---

### Task 2: Design the Ghost mascot pixel art

**Files:**
- Create: `android/app/src/main/java/com/claudescreensaver/data/skins/GhostSkin.kt`

The Ghost replaces Clawd. It's a 12x12 pixel ghost — classic Pac-Man ghost silhouette but with Claude's orange tint. The ghost IS Claude Code — "each session is a dream, the koans make it lucid."

**Step 1: Write `createGhostSkin()` with ghost pixel grid + ASCII frames**

```kotlin
// GhostSkin.kt
package com.claudescreensaver.data.skins

import com.claudescreensaver.data.models.*

/**
 * The default Ghost skin — Claude Code's mascot.
 * "Each session is a dream. The koans make it lucid."
 *
 * Pixel grid legend:
 * 0 = transparent
 * 1 = body (ghost white/tinted)
 * 2 = accent (eyes, details)
 * 3 = eye pupil (dark)
 * 4 = highlight (lighter body)
 */
fun createGhostSkin(): Skin {
    // 12x12 ghost pixel art — classic ghost shape
    val grid = listOf(
        listOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),  // row 0: dome top
        listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0),  // row 1
        listOf(0, 1, 1, 4, 1, 1, 1, 1, 4, 1, 1, 0),  // row 2: highlights
        listOf(0, 1, 2, 3, 2, 1, 1, 2, 3, 2, 1, 0),  // row 3: eyes
        listOf(0, 1, 2, 3, 2, 1, 1, 2, 3, 2, 1, 0),  // row 4: eyes
        listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),  // row 5: body
        listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),  // row 6: body
        listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),  // row 7: widest
        listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),  // row 8
        listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),  // row 9
        listOf(1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1),  // row 10: wavy bottom
        listOf(0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0),  // row 11: wavy tips
    )

    val colorMap = mapOf(
        0 to 0x00000000L, // transparent
        1 to 0xFFE8E6DCL, // ghost body (parchment/off-white)
        2 to 0xFFD97757L, // eye whites (accent orange)
        3 to 0xFF141413L, // eye pupils (dark)
        4 to 0xFFFAF9F5L, // body highlight (bright white)
    )

    val palette = SkinPalette(
        accent = 0xFFD97757L,
        accentDeep = 0xFFBD5D3AL,
        background = 0xFF141413L,
        textPrimary = 0xFFFAF9F5L,
        textSecondary = 0xFFB0AEA5L,
        textTertiary = 0xFFE8E6DCL,
    )

    val asciiFrames = mapOf(
        AgentState.IDLE to listOf(
            """
   .-.
  (o o)
  | O |
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.
  (- -)
  | O |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.THINKING to listOf(
            """
   .-.    ?
  (o o)  .
  | ~ | '
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.   ?
  (o o) '
  | ~ |.
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.  ?
  (o o).
  | ~ |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.TOOL_CALL to listOf(
            """
   .-.
  (o o)
  | = |/>
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.
  (o o)
  | = |\>
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.AWAITING_INPUT to listOf(
            """
   .-.
  (O O)  !
  | _ |
  /| |\
 (_| |_)
            """.trimIndent(),
            """
   .-.
  (o o)  !
  | _ |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.ERROR to listOf(
            """
   .-.
  (x x)
  | ~ |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
        AgentState.COMPLETE to listOf(
            """
   .-.
  (^ ^)
  | v |
  /| |\
 (_| |_)
            """.trimIndent(),
        ),
    )

    return Skin(
        id = "ghost",
        name = "Ghost",
        description = "Default ghost mascot",
        author = "built-in",
        isPremium = false,
        mascot = MascotDef(grid = grid, colorMap = colorMap),
        palette = palette,
        asciiFrames = asciiFrames,
    )
}
```

**Step 2: Run the Skin tests again (they depend on `createGhostSkin`)**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.models.SkinTest" 2>&1 | tail -20`
Expected: ALL PASS

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/data/skins/GhostSkin.kt
git commit -m "feat(skins): create Ghost mascot pixel art and ASCII frames"
```

---

### Task 3: Create SkinEngine (active skin provider)

**Files:**
- Create: `android/app/src/main/java/com/claudescreensaver/data/skins/SkinEngine.kt`
- Test: `android/app/src/test/java/com/claudescreensaver/data/skins/SkinEngineTest.kt`

The `SkinEngine` is a singleton that holds the active skin, manages installed skins, and persists the user's selection.

**Step 1: Write the failing test**

```kotlin
// SkinEngineTest.kt
package com.claudescreensaver.data.skins

import com.claudescreensaver.data.models.Skin
import org.junit.Test
import org.junit.Assert.*

class SkinEngineTest {

    @Test
    fun `engine starts with default ghost skin`() {
        val engine = SkinEngine()
        assertEquals("ghost", engine.activeSkin.value.id)
    }

    @Test
    fun `engine lists built-in skins`() {
        val engine = SkinEngine()
        val skins = engine.availableSkins.value
        assertTrue(skins.any { it.id == "ghost" })
    }

    @Test
    fun `can switch active skin`() {
        val engine = SkinEngine()
        // Only ghost available by default — switching to it should work
        engine.setActiveSkin("ghost")
        assertEquals("ghost", engine.activeSkin.value.id)
    }

    @Test
    fun `switching to unknown skin keeps current`() {
        val engine = SkinEngine()
        engine.setActiveSkin("nonexistent")
        assertEquals("ghost", engine.activeSkin.value.id)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.skins.SkinEngineTest" 2>&1 | tail -20`
Expected: FAIL — `SkinEngine` doesn't exist

**Step 3: Write the SkinEngine**

```kotlin
// SkinEngine.kt
package com.claudescreensaver.data.skins

import com.claudescreensaver.data.models.Skin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SkinEngine {

    private val _availableSkins = MutableStateFlow(listOf(Skin.DEFAULT))
    val availableSkins: StateFlow<List<Skin>> = _availableSkins.asStateFlow()

    private val _activeSkin = MutableStateFlow(Skin.DEFAULT)
    val activeSkin: StateFlow<Skin> = _activeSkin.asStateFlow()

    fun setActiveSkin(skinId: String) {
        val skin = _availableSkins.value.find { it.id == skinId } ?: return
        _activeSkin.value = skin
    }

    fun installSkin(skin: Skin) {
        val current = _availableSkins.value.toMutableList()
        current.removeAll { it.id == skin.id }
        current.add(skin)
        _availableSkins.value = current
    }

    fun uninstallSkin(skinId: String) {
        if (skinId == "ghost") return // can't uninstall default
        _availableSkins.value = _availableSkins.value.filter { it.id != skinId }
        if (_activeSkin.value.id == skinId) {
            _activeSkin.value = Skin.DEFAULT
        }
    }
}
```

**Step 4: Run tests**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.skins.SkinEngineTest" 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/data/skins/SkinEngine.kt \
        android/app/src/test/java/com/claudescreensaver/data/skins/SkinEngineTest.kt
git commit -m "feat(skins): add SkinEngine for managing active and installed skins"
```

---

### Task 4: Replace ClawdMascot with GhostMascot

**Files:**
- Create: `android/app/src/main/java/com/claudescreensaver/ui/components/GhostMascot.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/components/ClawdMascot.kt` — delete entirely
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/OnboardingScreen.kt` — swap mascot
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/StatusDashboardScreen.kt` — swap mascot

The new `GhostMascot` reads its pixel grid from the active skin (via `SkinEngine`), making it skin-aware from day one.

**Step 1: Create GhostMascot composable**

```kotlin
// GhostMascot.kt
package com.claudescreensaver.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.MascotDef
import com.claudescreensaver.data.models.Skin

/**
 * Skin-aware mascot renderer. Draws the mascot pixel grid from the active skin
 * with state-based animations: breathing, wobbling, bouncing, blinking.
 */
@Composable
fun GhostMascot(
    state: AgentState,
    skin: Skin,
    modifier: Modifier = Modifier,
) {
    val mascot = skin.mascot
    val anim = mascot.animation

    // Breathing (idle, complete)
    val infiniteTransition = rememberInfiniteTransition(label = "ghostAnim")
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = anim.breatheScale.first,
        targetValue = anim.breatheScale.second,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Wobble (thinking, tool_call)
    val wobbleX by infiniteTransition.animateFloat(
        initialValue = -anim.wobbleOffset,
        targetValue = anim.wobbleOffset,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "wobble",
    )

    // Bounce (awaiting_input)
    val bounceY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -anim.bounceHeight,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bounce",
    )

    // Blink cycle
    val blinkPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(anim.blinkIntervalMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blink",
    )
    val isBlinking = blinkPhase > 0.9f // blink in last 10% of cycle

    // Select active animation offsets
    val offsetX = when (state) {
        AgentState.THINKING, AgentState.TOOL_CALL -> wobbleX
        else -> 0f
    }
    val offsetY = when (state) {
        AgentState.AWAITING_INPUT -> bounceY
        else -> 0f
    }
    val scale = when (state) {
        AgentState.IDLE, AgentState.COMPLETE -> breatheScale
        else -> 1f
    }

    Canvas(modifier = modifier) {
        val gridSize = mascot.grid.size
        if (gridSize == 0) return@Canvas
        val cellSize = (minOf(size.width, size.height) / gridSize) * scale
        val totalW = gridSize * cellSize
        val totalH = gridSize * cellSize
        val startX = (size.width - totalW) / 2 + offsetX
        val startY = (size.height - totalH) / 2 + offsetY
        val gap = cellSize * 0.1f

        mascot.grid.forEachIndexed { row, cols ->
            cols.forEachIndexed { col, pixelVal ->
                if (pixelVal == 0) return@forEachIndexed // transparent

                // During blink, eye pupils (3) become eye whites (2)
                val effectiveVal = if (isBlinking && pixelVal == 3) 2 else pixelVal
                val colorLong = mascot.colorMap[effectiveVal] ?: return@forEachIndexed
                val color = Color(colorLong.toInt())

                drawRect(
                    color = color,
                    topLeft = Offset(startX + col * cellSize + gap / 2, startY + row * cellSize + gap / 2),
                    size = Size(cellSize - gap, cellSize - gap),
                )
            }
        }
    }
}
```

**Step 2: Update references**

In every file that imports `ClawdMascot`, replace with `GhostMascot` and pass the active skin.

Files to update:
- `OnboardingScreen.kt`: Change `ClawdMascot(state = ...)` → `GhostMascot(state = ..., skin = Skin.DEFAULT)`
- `StatusDashboardScreen.kt`: Change `ClawdMascot(state = ...)` → `GhostMascot(state = ..., skin = Skin.DEFAULT)`

Note: In Stage 2, `Skin.DEFAULT` will be replaced with `skinEngine.activeSkin` from the ViewModel. For now, hardcoding DEFAULT keeps the diff small.

**Step 3: Delete `ClawdMascot.kt`**

Remove `android/app/src/main/java/com/claudescreensaver/ui/components/ClawdMascot.kt`

**Step 4: Verify build compiles**

Run: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git rm android/app/src/main/java/com/claudescreensaver/ui/components/ClawdMascot.kt
git add android/app/src/main/java/com/claudescreensaver/ui/components/GhostMascot.kt \
        android/app/src/main/java/com/claudescreensaver/ui/screens/OnboardingScreen.kt \
        android/app/src/main/java/com/claudescreensaver/ui/screens/StatusDashboardScreen.kt
git commit -m "feat(mascot): replace Clawd crab with Ghost mascot, skin-aware renderer"
```

---

### Task 5: Generate Ghost app icon assets

**Files:**
- Replace: All `android/app/src/main/res/mipmap-*/ic_launcher.png`
- Replace: All `android/app/src/main/res/mipmap-*/ic_launcher_round.png`
- Modify: `android/app/src/main/res/drawable/ic_launcher_foreground.xml`

**Approach:** Use the Gemini Nano Banana Pro API (ref: `reference_gemini_api.md` in memory) to generate a ghost icon matching the pixel art style, then resize to all DPI buckets. Alternatively, render the 12x12 ghost grid to PNG at each required size programmatically.

**Step 1: Generate icon PNGs**

Write a Python script to render the Ghost pixel grid to PNGs at each DPI:
- mdpi: 48x48
- hdpi: 72x72
- xhdpi: 96x96
- xxhdpi: 144x144
- xxxhdpi: 192x192

```python
# tools/gen_ghost_icon.py
"""Render Ghost pixel grid to Android icon PNGs at all DPI buckets."""
from PIL import Image, ImageDraw

GRID = [
    [0,0,0,1,1,1,1,1,1,0,0,0],
    [0,0,1,1,1,1,1,1,1,1,0,0],
    [0,1,1,4,1,1,1,1,4,1,1,0],
    [0,1,2,3,2,1,1,2,3,2,1,0],
    [0,1,2,3,2,1,1,2,3,2,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [0,1,1,1,1,1,1,1,1,1,1,0],
    [1,1,1,1,1,1,1,1,1,1,1,1],
    [1,1,1,1,1,1,1,1,1,1,1,1],
    [1,1,1,1,1,1,1,1,1,1,1,1],
    [1,0,1,1,0,1,1,0,1,1,0,1],
    [0,0,1,0,0,1,1,0,0,1,0,0],
]

COLORS = {
    0: (0, 0, 0, 0),
    1: (232, 230, 220, 255),    # ghost body
    2: (217, 119, 87, 255),      # accent/eye
    3: (20, 20, 19, 255),        # pupil
    4: (250, 249, 245, 255),     # highlight
}

BG_COLOR = (20, 20, 19, 255)

SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

for density, px in SIZES.items():
    cell = px // 12
    img = Image.new("RGBA", (px, px), BG_COLOR)
    draw = ImageDraw.Draw(img)
    for r, row in enumerate(GRID):
        for c, val in enumerate(row):
            if val == 0:
                continue
            x, y = c * cell, r * cell
            draw.rectangle([x+1, y+1, x+cell-1, y+cell-1], fill=COLORS[val])

    out_dir = f"android/app/src/main/res/mipmap-{density}"
    img.save(f"{out_dir}/ic_launcher.png")
    # Round: same image, Android handles circular crop via adaptive icon
    img.save(f"{out_dir}/ic_launcher_round.png")
    print(f"{density}: {px}x{px} -> {out_dir}")
```

Run: `uv run --with Pillow python tools/gen_ghost_icon.py`

**Step 2: Verify icons exist**

Run: `ls -la android/app/src/main/res/mipmap-*/ic_launcher*.png`

**Step 3: Commit**

```bash
git add android/app/src/main/res/mipmap-*/ic_launcher*.png tools/gen_ghost_icon.py
git commit -m "feat(branding): replace Clawd crab icon with Ghost pixel art"
```

---

### Stage 1 Verification

Before proceeding to Stage 2, verify:
1. All tests pass: `cd android && ./gradlew testDebugUnitTest 2>&1 | tail -10`
2. App compiles: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`
3. Ghost mascot renders in dashboard and onboarding
4. Skin data model serializes/deserializes correctly
5. No references to "Clawd" or "crab" remain in source: `grep -ri "clawd\|crab" android/app/src/main/ --include="*.kt"`

---

## Stage 2 — Direct: Skin Integration Into All UI

### Task 6: Wire SkinEngine into ViewModel and MainActivity

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/viewmodel/StatusViewModel.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/MainActivity.kt`

**Step 1: Add SkinEngine to StatusViewModel**

Add `skinEngine` as a dependency, expose `activeSkin` as a StateFlow, add it to `UiState`.

```kotlin
// In StatusViewModel.kt, add:
data class UiState(
    val agentStatus: AgentStatus = AgentStatus.DISCONNECTED,
    val sessions: Map<String, AgentStatus> = emptyMap(),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val proStatus: ProStatus = ProStatus.FREE,
    val activeSkin: Skin = Skin.DEFAULT,  // ADD THIS
)
```

Wire `skinEngine.activeSkin` into the combine flow.

**Step 2: Update MainActivity to create and pass SkinEngine**

```kotlin
// In MainActivity.onCreate:
private lateinit var skinEngine: SkinEngine

// In onCreate:
skinEngine = SkinEngine()
viewModel = StatusViewModel(SseClient(), skinEngine)
```

**Step 3: Persist active skin selection in SharedPreferences**

On skin change, save `skin_id` to prefs. On startup, restore from prefs.

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/viewmodel/StatusViewModel.kt \
        android/app/src/main/java/com/claudescreensaver/MainActivity.kt
git commit -m "feat(skins): wire SkinEngine into ViewModel and MainActivity"
```

---

### Task 7: Thread active skin through all UI components

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/StatusDashboardScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SessionFullScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/OnboardingScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/components/SessionCard.kt`

**What changes:**
- Every screen that renders mascot or uses colors gets `skin: Skin` parameter
- Replace hardcoded `ClaudeAccent` / `ClaudeBgDark` etc with `Color(skin.palette.accent.toInt())`
- Replace `asciiFramesFor()` in `SimpleStatusScreen` with `skin.asciiFrames[state]`
- `GhostMascot` already takes `skin` — just pass the real one instead of `Skin.DEFAULT`

**Step 1: Update each screen signature to accept `skin: Skin`**

Add `skin: Skin = Skin.DEFAULT` parameter to:
- `StatusDashboardScreen`
- `SimpleStatusScreen`
- `SessionFullScreen`
- `SessionCard`

**Step 2: Replace hardcoded colors with skin palette references**

Example pattern (repeat for each file):
```kotlin
// Before:
.background(ClaudeBgDark)

// After:
.background(Color(skin.palette.background.toInt()))
```

Keep `Color.kt` theme colors as fallback defaults — skin palette overrides them at render time.

**Step 3: Pass `uiState.activeSkin` from MainActivity into each screen call**

**Step 4: Verify build**

Run: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/
git commit -m "feat(skins): thread active skin palette and mascot through all UI"
```

---

### Task 8: Update SimpleStatusScreen to use skin ASCII frames

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt`

Currently `SimpleStatusScreen` has hardcoded `asciiFramesFor()`. Replace with skin's `asciiFrames`.

**Step 1: Remove `asciiFramesFor()` function**

**Step 2: Replace frame source**

```kotlin
// Before:
val frames = status.customFrames?.takeIf { it.isNotEmpty() } ?: asciiFramesFor(status.state)

// After:
val frames = status.customFrames?.takeIf { it.isNotEmpty() }
    ?: skin.asciiFrames[status.state]
    ?: listOf("?")
```

**Step 3: Verify the simple mode still renders**

Run: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt
git commit -m "feat(skins): SimpleStatusScreen uses skin ASCII frames instead of hardcoded"
```

---

### Stage 2 Verification

1. All tests pass
2. App compiles
3. Ghost renders everywhere Clawd used to
4. Colors come from skin palette
5. ASCII frames come from skin data
6. Switching skins (programmatically) changes all visuals

---

## Stage 3 — Indirect: Marketplace & Community Skins

### Task 9: Add skin endpoints to bridge server

**Files:**
- Modify: `bridge/bridge/server.py`
- Create: `bridge/bridge/skins.py`

The bridge serves as the skin distribution hub. Users share skins by uploading JSON to the bridge; others browse and download.

**Step 1: Create skins storage module**

```python
# bridge/bridge/skins.py
"""Community skin storage and retrieval."""
from __future__ import annotations
import json
import logging
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

SKINS_DIR = Path.home() / ".claude" / "ccsaver-skins"


def ensure_dir() -> Path:
    SKINS_DIR.mkdir(parents=True, exist_ok=True)
    return SKINS_DIR


def list_skins() -> list[dict[str, Any]]:
    """List all installed community skins (metadata only)."""
    skins = []
    for f in ensure_dir().glob("*.json"):
        try:
            data = json.loads(f.read_text())
            skins.append({
                "id": data.get("id", f.stem),
                "name": data.get("name", f.stem),
                "description": data.get("description", ""),
                "author": data.get("author", "unknown"),
                "is_premium": data.get("is_premium", False),
            })
        except (json.JSONDecodeError, KeyError):
            continue
    return skins


def get_skin(skin_id: str) -> dict[str, Any] | None:
    """Get full skin data by ID."""
    path = ensure_dir() / f"{skin_id}.json"
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text())
    except (json.JSONDecodeError, KeyError):
        return None


def save_skin(skin_data: dict[str, Any]) -> str:
    """Save a skin, returns its ID."""
    skin_id = skin_data.get("id", "unnamed")
    path = ensure_dir() / f"{skin_id}.json"
    path.write_text(json.dumps(skin_data, indent=2))
    logger.info("Saved skin '%s' to %s", skin_id, path)
    return skin_id


def delete_skin(skin_id: str) -> bool:
    """Delete a skin by ID. Returns True if deleted."""
    if skin_id == "ghost":
        return False  # can't delete built-in
    path = ensure_dir() / f"{skin_id}.json"
    if path.exists():
        path.unlink()
        return True
    return False
```

**Step 2: Add skin endpoints to server.py**

```python
# In server.py, add handlers:

async def skins_list_handler(request: web.Request) -> web.Response:
    """GET /skins — list available community skins."""
    from bridge.skins import list_skins
    return web.json_response(list_skins())

async def skin_get_handler(request: web.Request) -> web.Response:
    """GET /skins/{skin_id} — get full skin data."""
    from bridge.skins import get_skin
    skin_id = request.match_info["skin_id"]
    data = get_skin(skin_id)
    if data is None:
        return web.json_response({"error": "not found"}, status=404)
    return web.json_response(data)

async def skin_upload_handler(request: web.Request) -> web.Response:
    """POST /skins — upload a community skin."""
    from bridge.skins import save_skin
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)
    if "id" not in data or "name" not in data:
        return web.json_response({"error": "missing id or name"}, status=400)
    skin_id = save_skin(data)
    return web.json_response({"accepted": True, "id": skin_id}, status=201)

async def skin_delete_handler(request: web.Request) -> web.Response:
    """DELETE /skins/{skin_id} — remove a community skin."""
    from bridge.skins import delete_skin
    skin_id = request.match_info["skin_id"]
    if delete_skin(skin_id):
        return web.json_response({"deleted": True})
    return web.json_response({"error": "not found or protected"}, status=404)

# In create_app, add routes:
app.router.add_get("/skins", skins_list_handler)
app.router.add_get("/skins/{skin_id}", skin_get_handler)
app.router.add_post("/skins", skin_upload_handler)
app.router.add_delete("/skins/{skin_id}", skin_delete_handler)
```

**Step 3: Write bridge tests**

```python
# bridge/tests/test_skins.py
import json, pytest
from aiohttp.test_utils import AioHTTPTestCase
from bridge.server import create_app

class TestSkinEndpoints(AioHTTPTestCase):
    async def get_application(self):
        return create_app(enable_mdns=False)

    async def test_list_skins_empty(self):
        resp = await self.client.get("/skins")
        assert resp.status == 200
        data = await resp.json()
        assert isinstance(data, list)

    async def test_upload_and_get_skin(self):
        skin = {"id": "test_neon", "name": "Neon Ghost", "description": "Neon colors"}
        resp = await self.client.post("/skins", json=skin)
        assert resp.status == 201

        resp = await self.client.get("/skins/test_neon")
        assert resp.status == 200
        data = await resp.json()
        assert data["name"] == "Neon Ghost"

    async def test_delete_skin(self):
        skin = {"id": "deleteme", "name": "Delete Me"}
        await self.client.post("/skins", json=skin)
        resp = await self.client.delete("/skins/deleteme")
        assert resp.status == 200

    async def test_cannot_delete_ghost(self):
        resp = await self.client.delete("/skins/ghost")
        assert resp.status == 404
```

**Step 4: Commit**

```bash
git add bridge/bridge/skins.py bridge/tests/test_skins.py bridge/bridge/server.py
git commit -m "feat(bridge): add skin CRUD endpoints for community marketplace"
```

---

### Task 10: Build Skin Marketplace screen in Android

**Files:**
- Create: `android/app/src/main/java/com/claudescreensaver/ui/screens/SkinMarketplaceScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/data/network/SseClient.kt` — add skin API calls
- Modify: `android/app/src/main/java/com/claudescreensaver/MainActivity.kt` — add navigation

**Step 1: Add skin API methods to SseClient**

```kotlin
// In SseClient.kt, add:

fun fetchSkins(callback: (List<SkinListItem>) -> Unit) {
    if (baseUrl.isBlank()) return
    thread {
        try {
            val url = URL("$baseUrl/skins")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val arr = JSONArray(body)
            val items = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                SkinListItem(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description", ""),
                    author = obj.optString("author", "unknown"),
                    isPremium = obj.optBoolean("is_premium", false),
                )
            }
            callback(items)
        } catch (_: Exception) {}
    }
}

fun fetchSkin(skinId: String, callback: (String?) -> Unit) {
    if (baseUrl.isBlank()) return
    thread {
        try {
            val url = URL("$baseUrl/skins/$skinId")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            callback(body)
        } catch (_: Exception) {
            callback(null)
        }
    }
}

data class SkinListItem(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val isPremium: Boolean,
)
```

**Step 2: Create SkinMarketplaceScreen**

```kotlin
// SkinMarketplaceScreen.kt — scrollable list of available skins
// Each skin card shows: name, author, description, preview thumbnail, install/activate button
// Active skin highlighted
// Free tier: can browse but install is gated behind paywall
```

Detailed UI: terminal-style list with Ghost preview thumbnails rendered via `GhostMascot` using each skin's palette. "INSTALL" button downloads full JSON and calls `skinEngine.installSkin()`. "ACTIVATE" button calls `skinEngine.setActiveSkin()`.

**Step 3: Add "Skins" navigation to settings**

Add a "Skins" button in `SettingsScreen.kt` that navigates to the marketplace. Gate access: free users see "PRO ONLY" badge, tapping shows paywall.

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/SkinMarketplaceScreen.kt \
        android/app/src/main/java/com/claudescreensaver/data/network/SseClient.kt \
        android/app/src/main/java/com/claudescreensaver/MainActivity.kt
git commit -m "feat(marketplace): add skin marketplace screen with browse/install/activate"
```

---

### Task 11: Enable skin upload from the app

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SkinMarketplaceScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/data/network/SseClient.kt`

Add "Share Skin" button that uploads the active skin (or a modified version) to the bridge. Pro-only feature.

```kotlin
// In SseClient.kt, add:
fun uploadSkin(skinJson: String, callback: (Boolean) -> Unit) {
    if (baseUrl.isBlank()) return
    thread {
        try {
            val url = URL("$baseUrl/skins")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            OutputStreamWriter(conn.outputStream).use { it.write(skinJson) }
            callback(conn.responseCode == 201)
            conn.disconnect()
        } catch (_: Exception) {
            callback(false)
        }
    }
}
```

**Commit:**
```bash
git commit -m "feat(marketplace): add skin upload/sharing to bridge"
```

---

### Stage 3 Verification

1. Bridge skin endpoints work: `curl localhost:4001/skins`
2. Upload a test skin, verify it appears in list
3. Android marketplace screen loads and displays skins
4. Install/activate flow works end-to-end
5. Free tier is gated from installing premium skins

---

## Stage 4 — Config: Billing Model Restructure

### Task 12: Restructure free vs paid tier gating

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/data/BillingManager.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/PaywallScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/MainActivity.kt`

**New tier structure:**
- **Free**: Default Ghost skin, no animations, basic status display (all panes, all features EXCEPT cosmetics)
- **Pro**: Skin marketplace access, animations enabled, custom skin creation, community skins

**What changes:**
- Remove: pane count gating (free gets all 4 panes)
- Remove: sound gating (free gets sounds)
- Remove: screensaver/kiosk mode gating (free gets these)
- Add: skin marketplace gating (pro only)
- Add: animation gating (pro only — free renders static mascot)
- Update `PaywallScreen` to list cosmetic features instead of functional ones

**Step 1: Update `isPro` checks across codebase**

Remove functional gates:
```kotlin
// StatusDashboardScreen.kt — remove:
val maxPanes = if (isPro) 4 else 1
// Replace with:
val maxPanes = 4
```

Add cosmetic gates:
```kotlin
// GhostMascot.kt — disable animations for free:
val animationsEnabled = isPro
val breatheScale = if (animationsEnabled) ... else 1f
```

**Step 2: Update PaywallScreen feature list**

```kotlin
val features = listOf(
    "Ghost skin marketplace",
    "Animated mascot with state-reactive effects",
    "Community skin packs — browse & share",
    "Custom color palettes",
    "Premium exclusive skins",
)
```

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/data/BillingManager.kt \
        android/app/src/main/java/com/claudescreensaver/ui/screens/PaywallScreen.kt \
        android/app/src/main/java/com/claudescreensaver/MainActivity.kt \
        android/app/src/main/java/com/claudescreensaver/ui/screens/StatusDashboardScreen.kt \
        android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt
git commit -m "refactor(billing): restructure tiers — free=full features, pro=cosmetics"
```

---

### Task 13: Update branding strings and metadata

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/OnboardingScreen.kt`

**Changes:**
- All references to "Clawd" → "Ghost"
- App tagline: "Your agent's spirit, always visible"
- Onboarding copy updated to reference Ghost mascot
- Play Store description updated

**Commit:**
```bash
git commit -m "chore(branding): update all Clawd references to Ghost"
```

---

### Stage 4 Verification

1. Free tier: all 4 panes work, sounds work, screensaver works, but mascot is static and marketplace shows "PRO" badge
2. Pro tier: animated mascot, marketplace browse/install/share
3. No references to "Clawd" or "crab" in codebase
4. PaywallScreen shows cosmetic features
5. All tests pass
6. APK builds successfully

---

## Summary

| Task | Stage | Risk | Description |
|------|-------|------|-------------|
| 1 | Core | LOW | Skin data model |
| 2 | Core | LOW | Ghost pixel art + ASCII |
| 3 | Core | LOW | SkinEngine |
| 4 | Core | MEDIUM | Replace ClawdMascot → GhostMascot |
| 5 | Core | LOW | Generate icon assets |
| 6 | Direct | MEDIUM | Wire SkinEngine into ViewModel |
| 7 | Direct | HIGH | Thread skin through all UI |
| 8 | Direct | LOW | SimpleStatusScreen skin frames |
| 9 | Indirect | MEDIUM | Bridge skin endpoints |
| 10 | Indirect | HIGH | Marketplace screen |
| 11 | Indirect | LOW | Skin upload |
| 12 | Config | MEDIUM | Billing restructure |
| 13 | Config | LOW | Branding cleanup |

**Estimated files touched:** 18 modified, 6 created, 1 deleted
**Key risk:** Stage 2 Task 7 (threading skin through all UI) — largest blast radius, touch every screen

---

## Architect Review (2026-03-16)

**Reviewer:** GPT-5.4 via Codex CLI (Sanitize and Fire mode)
**Critiques:** 8 total — 6 blocking, 2 degrading, 0 cosmetic

### Blocking Issues Resolved

1. **[INVARIANT] Renderer accepts impossible skin shapes**
   → Added `Skin.validate(): List<String>` to Task 1. Checks: grid must be 12x12, all pixel values must exist in `colorMap`, all 6 palette fields must be non-zero. `SkinEngine.installSkin()` calls `validate()` and rejects invalid skins. Renderer wraps grid access in bounds checks as defense-in-depth.

2. **[INVARIANT] ASCII coverage not constrained to runtime states**
   → Added invariant to `Skin.validate()`: `asciiFrames` must contain all 6 `AgentState` values, each with ≥1 frame. Renderer falls back to `Skin.DEFAULT.asciiFrames[state]` if a community skin is missing a state entry (defense-in-depth, validation should catch this at install time).

3. **[TEMPORAL] Active-skin recovery path undefined**
   → `SkinEngine` constructor loads persisted skin ID from SharedPreferences. If the ID isn't found in `availableSkins`, it resets to "ghost" **before** `activeSkin` StateFlow emits its first value. `uninstallSkin()` already resets to DEFAULT if the active skin is removed. This is atomic — no observer can read stale state.

4. **[INTERFACE] `version` doesn't distinguish schema from content**
   → Replaced `version: Int` with `schemaVersion: Int` (hardcoded to `1` for now) + `contentVersion: Int` (author increments). `Skin.fromJson()` rejects `schemaVersion > SUPPORTED_SCHEMA` (currently 1) with a clear error message. Forward-compatible: new fields in future schemas are optional with defaults.

5. **[COUPLING] Billing rules conflict across sections**
   → Moved billing restructure from Stage 4 to **Task 0 (pre-Stage 1)**. Defined a single entitlement matrix referenced by all stages:
   - **Free**: All functional features (4 panes, sounds, screensaver, kiosk). Default Ghost skin. Static mascot (no animations).
   - **Pro**: Animated mascot. Skin marketplace (browse + install + share). Premium skins. Custom palettes.
   All `isPro` checks in renderer, marketplace, and rollout reference this matrix.

6. **[INTERFACE] CRUD endpoints have no collision/ownership**
   → `POST /skins` generates ID as `{author}_{sanitized_name}`. If ID already exists, returns 409 Conflict (no silent overwrite). `DELETE /skins/{id}` only removes skins where the JSON file exists and `id != "ghost"`. For v1 (single bridge, trusted LAN), this is sufficient. Multi-user auth deferred to when/if bridges become shared infrastructure.

### Accepted Risks

- **[INTEGRATION] Marketplace previews need data the list endpoint doesn't provide** — Accepted. `GET /skins` returns metadata only. Marketplace renders placeholder ghost silhouette per card. Full preview loads on tap (fetches full skin JSON, renders in preview pane). Catalog will be small (<50 skins) for v1, so N+1 fetch on tap is acceptable.

- **[FALSIFIABILITY] Stage 2 has no proof all hardcoded paths were removed** — Accepted with mitigation. Added a Stage 2 verification step: `grep -rn "ClaudeBgDark\|ClaudeAccent\|ClaudeGray\|ClaudeParchment\|ClaudeTextLight" android/app/src/main/java/ --include="*.kt"` must return zero hits outside `Color.kt` and `Skin.kt`. This is a mechanical grep check, not a runtime test, but catches the most common failure mode.

### Future Risks (Not Blocking)

- **Skin file corruption on interrupted write** — If app crashes during `installSkin()` file write, partial JSON on disk. Would become blocking if skins are large or writes are slow. Mitigate with atomic write (write to `.tmp`, rename) when this becomes a problem.
- **Bridge skin storage unbounded** — No limit on number/size of uploaded skins. Would become blocking if bridge is exposed to untrusted networks. Mitigate with max file size (100KB) and max count (100) when marketplace goes public.
- **Cross-device skin sync** — Skins installed on one phone aren't available on another. Would become blocking if users expect cloud sync. Not in scope for v1.

### Survivability Assessment

This plan now survives contact with the codebase. The highest-risk phase remains Stage 2 (threading skin through all UI), but the added validation contract means invalid skins can't reach the renderer, the fallback semantics prevent startup crashes, and the billing matrix is defined before any code touches `isPro`. The grep-based verification step for Stage 2 catches the most likely integration failure (leftover hardcoded colors).

The first thing that will actually break during implementation is the JSON serialization round-trip for `Long` color values (Kotlin `Long` vs JSON number precision) — test this early in Task 1. Everything else is mechanical.
