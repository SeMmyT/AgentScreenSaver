package com.claudescreensaver.data.models

import org.junit.Test
import org.junit.Assert.*

class SkinTest {

    // --- Metadata ---

    @Test
    fun `default ghost skin has correct metadata`() {
        val skin = Skin.DEFAULT
        assertEquals("ghost", skin.id)
        assertEquals("Ghost", skin.name)
        assertEquals("Default ghost mascot", skin.description)
        assertFalse(skin.isPremium)
    }

    @Test
    fun `default skin uses schemaVersion and contentVersion`() {
        val skin = Skin.DEFAULT
        assertEquals(Skin.SUPPORTED_SCHEMA, skin.schemaVersion)
        assertEquals(1, skin.contentVersion)
    }

    // --- Mascot grid ---

    @Test
    fun `skin has mascot pixel grid`() {
        val skin = Skin.DEFAULT
        assertTrue(skin.mascot.grid.isNotEmpty())
        assertEquals(12, skin.mascot.grid.size)
        assertTrue(skin.mascot.grid.all { it.size == 12 })
    }

    // --- Palette ---

    @Test
    fun `skin has color palette`() {
        val skin = Skin.DEFAULT
        assertNotEquals(0L, skin.palette.accent)
        assertNotEquals(0L, skin.palette.background)
        assertNotEquals(0L, skin.palette.textPrimary)
    }

    // --- ASCII frames ---

    @Test
    fun `skin has ASCII frames for each agent state`() {
        val skin = Skin.DEFAULT
        AgentState.entries.forEach { state ->
            val frames = skin.asciiFrames[state]
            assertNotNull("Missing frames for $state", frames)
            assertTrue("Empty frames for $state", frames!!.isNotEmpty())
        }
    }

    // --- Validation ---

    @Test
    fun `default skin passes validation`() {
        val errors = Skin.DEFAULT.validate()
        assertTrue("Default skin should be valid but got: $errors", errors.isEmpty())
    }

    @Test
    fun `validate catches wrong grid size`() {
        val badSkin = Skin.DEFAULT.copy(
            mascot = Skin.DEFAULT.mascot.copy(
                grid = List(10) { List(12) { 0 } }
            )
        )
        val errors = badSkin.validate()
        assertTrue(errors.any { "12 rows" in it })
    }

    @Test
    fun `validate catches wrong column count`() {
        val grid = List(12) { if (it == 5) List(8) { 0 } else List(12) { 0 } }
        val badSkin = Skin.DEFAULT.copy(
            mascot = Skin.DEFAULT.mascot.copy(grid = grid)
        )
        val errors = badSkin.validate()
        assertTrue(errors.any { "row 5" in it && "12 columns" in it })
    }

    @Test
    fun `validate catches pixel values missing from colorMap`() {
        val grid = List(12) { List(12) { 99 } } // pixel value 99 not in colorMap
        val badSkin = Skin.DEFAULT.copy(
            mascot = Skin.DEFAULT.mascot.copy(grid = grid)
        )
        val errors = badSkin.validate()
        assertTrue(errors.any { "colorMap" in it && "99" in it })
    }

    @Test
    fun `validate catches zero palette fields`() {
        val badSkin = Skin.DEFAULT.copy(
            palette = SkinPalette(
                accent = 0L,
                accentDeep = 0L,
                background = 0L,
                textPrimary = 0L,
                textSecondary = 0L,
                textTertiary = 0L,
            )
        )
        val errors = badSkin.validate()
        assertEquals(6, errors.count { "non-zero" in it })
    }

    @Test
    fun `validate catches missing ASCII frames`() {
        val badSkin = Skin.DEFAULT.copy(
            asciiFrames = emptyMap()
        )
        val errors = badSkin.validate()
        assertEquals(AgentState.entries.size, errors.count { "asciiFrames" in it })
    }

    @Test
    fun `validate catches empty frame lists`() {
        val badSkin = Skin.DEFAULT.copy(
            asciiFrames = AgentState.entries.associateWith { emptyList() }
        )
        val errors = badSkin.validate()
        assertTrue(errors.all { "missing or empty" in it })
        assertEquals(AgentState.entries.size, errors.size)
    }

    // --- JSON serialization ---

    @Test
    fun `skin serializes to and from JSON`() {
        val original = Skin.DEFAULT
        val json = original.toJson()
        val restored = Skin.fromJson(json)
        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.description, restored.description)
        assertEquals(original.author, restored.author)
        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(original.contentVersion, restored.contentVersion)
        assertEquals(original.isPremium, restored.isPremium)
        assertEquals(original.mascot.grid.size, restored.mascot.grid.size)
        assertEquals(original.mascot.grid, restored.mascot.grid)
        assertEquals(original.mascot.colorMap, restored.mascot.colorMap)
        assertEquals(original.palette, restored.palette)
    }

    @Test
    fun `JSON round-trip preserves Long color values`() {
        val original = Skin.DEFAULT
        val json = original.toJson()
        val restored = Skin.fromJson(json)
        assertEquals(original.palette.accent, restored.palette.accent)
        assertEquals(original.palette.background, restored.palette.background)
        // Verify high-bit ARGB values survive (these are > Int.MAX_VALUE)
        assertTrue(restored.palette.accent > Int.MAX_VALUE.toLong())
    }

    @Test
    fun `JSON round-trip preserves ASCII frames for all states`() {
        val original = Skin.DEFAULT
        val json = original.toJson()
        val restored = Skin.fromJson(json)
        AgentState.entries.forEach { state ->
            assertEquals(
                "Frames mismatch for $state",
                original.asciiFrames[state],
                restored.asciiFrames[state]
            )
        }
    }

    @Test
    fun `restored skin passes validation`() {
        val json = Skin.DEFAULT.toJson()
        val restored = Skin.fromJson(json)
        val errors = restored.validate()
        assertTrue("Restored skin should be valid but got: $errors", errors.isEmpty())
    }

    // --- Schema version rejection ---

    @Test(expected = IllegalArgumentException::class)
    fun `fromJson rejects unsupported schema version`() {
        val json = Skin.DEFAULT.toJson()
        // Patch the schema_version to a future value
        val patched = json.replace(
            "\"schema_version\": ${Skin.SUPPORTED_SCHEMA}",
            "\"schema_version\": 999"
        )
        Skin.fromJson(patched)
    }

    @Test
    fun `fromJson accepts current schema version`() {
        val json = Skin.DEFAULT.toJson()
        // Should not throw
        val skin = Skin.fromJson(json)
        assertEquals(Skin.SUPPORTED_SCHEMA, skin.schemaVersion)
    }
}
