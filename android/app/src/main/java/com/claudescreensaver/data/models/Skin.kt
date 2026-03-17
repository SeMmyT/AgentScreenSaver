package com.claudescreensaver.data.models

import com.claudescreensaver.data.skins.createGhostSkin
import org.json.JSONArray
import org.json.JSONObject

data class SkinPalette(
    val accent: Long,        // e.g. 0xFFD97757
    val accentDeep: Long,    // e.g. 0xFFBD5D3A
    val background: Long,    // e.g. 0xFF141413
    val textPrimary: Long,   // e.g. 0xFFFAF9F5
    val textSecondary: Long, // e.g. 0xFFB0AEA5
    val textTertiary: Long,  // e.g. 0xFFE8E6DC
)

data class MascotAnimation(
    val breatheScale: Pair<Float, Float> = 0.96f to 1.04f,
    val wobbleOffset: Float = 2f,
    val bounceHeight: Float = 6f,
    val blinkIntervalMs: Int = 3000,
)

data class MascotDef(
    val grid: List<List<Int>>,   // NxN base grid, values index into colorMap (0=transparent)
    val colorMap: Map<Int, Long>, // pixel value -> ARGB color
    val animation: MascotAnimation = MascotAnimation(),
    /** Per-state animation frames. Each state maps to a list of grids (frame sequence). */
    val stateFrames: Map<AgentState, List<List<List<Int>>>> = emptyMap(),
)

data class Skin(
    val id: String,
    val name: String,
    val description: String,
    val author: String = "built-in",
    val schemaVersion: Int = SUPPORTED_SCHEMA,
    val contentVersion: Int = 1,
    val isPremium: Boolean = false,
    val mascot: MascotDef,
    val palette: SkinPalette,
    val asciiFrames: Map<AgentState, List<String>>,
) {
    /**
     * Validates this skin and returns a list of error messages.
     * An empty list means the skin is valid.
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        // Grid must be square (NxN)
        val gridSize = mascot.grid.size
        if (gridSize == 0) {
            errors.add("Grid must not be empty")
        } else {
            mascot.grid.forEachIndexed { i, row ->
                if (row.size != gridSize) {
                    errors.add("Grid row $i must have $gridSize columns, has ${row.size}")
                }
            }
        }

        // All pixel values in grid (and state frames) must exist in colorMap
        val allPixelValues = mascot.grid.flatten().toMutableSet()
        mascot.stateFrames.values.forEach { frames ->
            frames.forEach { frame -> allPixelValues.addAll(frame.flatten()) }
        }
        allPixelValues.remove(0) // 0 = transparent, not in colorMap
        val missingColors = allPixelValues - mascot.colorMap.keys
        if (missingColors.isNotEmpty()) {
            errors.add("Grid contains pixel values not in colorMap: $missingColors")
        }

        // All 6 palette fields must be non-zero
        if (palette.accent == 0L) errors.add("palette.accent must be non-zero")
        if (palette.accentDeep == 0L) errors.add("palette.accentDeep must be non-zero")
        if (palette.background == 0L) errors.add("palette.background must be non-zero")
        if (palette.textPrimary == 0L) errors.add("palette.textPrimary must be non-zero")
        if (palette.textSecondary == 0L) errors.add("palette.textSecondary must be non-zero")
        if (palette.textTertiary == 0L) errors.add("palette.textTertiary must be non-zero")

        // asciiFrames must contain all 6 AgentState values, each with >= 1 frame
        AgentState.entries.forEach { state ->
            val frames = asciiFrames[state]
            if (frames == null || frames.isEmpty()) {
                errors.add("asciiFrames missing or empty for state ${state.value}")
            }
        }

        return errors
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("description", description)
        obj.put("author", author)
        obj.put("schema_version", schemaVersion)
        obj.put("content_version", contentVersion)
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
        // Use String representation for Long color values to avoid JSON number precision issues
        val colorMapObj = JSONObject()
        mascot.colorMap.forEach { (k, v) -> colorMapObj.put(k.toString(), v.toString()) }
        mascotObj.put("color_map", colorMapObj)
        if (mascot.stateFrames.isNotEmpty()) {
            val sfObj = JSONObject()
            mascot.stateFrames.forEach { (state, frames) ->
                val framesArr = JSONArray()
                frames.forEach { frame ->
                    val gridArr2 = JSONArray()
                    frame.forEach { row ->
                        val rowArr2 = JSONArray()
                        row.forEach { rowArr2.put(it) }
                        gridArr2.put(rowArr2)
                    }
                    framesArr.put(gridArr2)
                }
                sfObj.put(state.value, framesArr)
            }
            mascotObj.put("state_frames", sfObj)
        }
        obj.put("mascot", mascotObj)

        // Palette — store as String to preserve Long precision
        val palObj = JSONObject()
        palObj.put("accent", palette.accent.toString())
        palObj.put("accent_deep", palette.accentDeep.toString())
        palObj.put("background", palette.background.toString())
        palObj.put("text_primary", palette.textPrimary.toString())
        palObj.put("text_secondary", palette.textSecondary.toString())
        palObj.put("text_tertiary", palette.textTertiary.toString())
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
        /** The highest schema version this app can parse. */
        const val SUPPORTED_SCHEMA = 1

        val DEFAULT: Skin by lazy { createGhostSkin() }

        fun fromJson(json: String): Skin {
            val obj = JSONObject(json)

            val schema = obj.optInt("schema_version", 1)
            if (schema > SUPPORTED_SCHEMA) {
                throw IllegalArgumentException(
                    "Skin schema version $schema is not supported (max $SUPPORTED_SCHEMA). Update the app to load this skin."
                )
            }

            val mascotObj = obj.getJSONObject("mascot")
            val gridArr = mascotObj.getJSONArray("grid")
            val grid = (0 until gridArr.length()).map { r ->
                val rowArr = gridArr.getJSONArray(r)
                (0 until rowArr.length()).map { rowArr.getInt(it) }
            }
            val colorMapObj = mascotObj.getJSONObject("color_map")
            val colorMap = colorMapObj.keys().asSequence().associate {
                it.toInt() to colorMapObj.getString(it).toLong()
            }

            // Parse per-state frame grids (optional)
            val stateFrames = mutableMapOf<AgentState, List<List<List<Int>>>>()
            val framesGridObj = mascotObj.optJSONObject("state_frames")
            if (framesGridObj != null) {
                AgentState.entries.forEach { state ->
                    val stateArr = framesGridObj.optJSONArray(state.value) ?: return@forEach
                    val frames = (0 until stateArr.length()).map { fi ->
                        val frameGrid = stateArr.getJSONArray(fi)
                        (0 until frameGrid.length()).map { r ->
                            val rowArr = frameGrid.getJSONArray(r)
                            (0 until rowArr.length()).map { rowArr.getInt(it) }
                        }
                    }
                    if (frames.isNotEmpty()) stateFrames[state] = frames
                }
            }

            val palObj = obj.getJSONObject("palette")
            val palette = SkinPalette(
                accent = palObj.getString("accent").toLong(),
                accentDeep = palObj.getString("accent_deep").toLong(),
                background = palObj.getString("background").toLong(),
                textPrimary = palObj.getString("text_primary").toLong(),
                textSecondary = palObj.getString("text_secondary").toLong(),
                textTertiary = palObj.getString("text_tertiary").toLong(),
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
                schemaVersion = schema,
                contentVersion = obj.optInt("content_version", 1),
                isPremium = obj.optBoolean("is_premium", false),
                mascot = MascotDef(grid = grid, colorMap = colorMap, stateFrames = stateFrames),
                palette = palette,
                asciiFrames = asciiFrames,
            )
        }

        /**
         * Placeholder Ghost skin that satisfies validation.
         * The real Ghost pixel art and ASCII frames come in Task 2 (GhostSkin.kt).
         */
        private fun createPlaceholderGhostSkin(): Skin {
            // Minimal 12x12 grid using values 0 (transparent) and 1 (body)
            val grid = listOf(
                listOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0),
                listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0),
                listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
                listOf(0, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 0),
                listOf(0, 1, 1, 2, 1, 1, 1, 1, 2, 1, 1, 0),
                listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
                listOf(0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
                listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                listOf(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
                listOf(1, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0, 1),
                listOf(0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0),
            )

            val colorMap = mapOf(
                0 to 0x00000000L, // transparent
                1 to 0xFFE8E6DCL, // ghost body
                2 to 0xFFD97757L, // accent (eyes)
            )

            val palette = SkinPalette(
                accent = 0xFFD97757L,
                accentDeep = 0xFFBD5D3AL,
                background = 0xFF141413L,
                textPrimary = 0xFFFAF9F5L,
                textSecondary = 0xFFB0AEA5L,
                textTertiary = 0xFFE8E6DCL,
            )

            val placeholderFrame = """
   .-.
  (o o)
  | O |
  /| |\
 (_| |_)
            """.trimIndent()

            val asciiFrames = AgentState.entries.associateWith { listOf(placeholderFrame) }

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
    }
}
