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
        engine.setActiveSkin("ghost")
        assertEquals("ghost", engine.activeSkin.value.id)
    }

    @Test
    fun `switching to unknown skin keeps current`() {
        val engine = SkinEngine()
        engine.setActiveSkin("nonexistent")
        assertEquals("ghost", engine.activeSkin.value.id)
    }

    @Test
    fun `install and activate custom skin`() {
        val engine = SkinEngine()
        val custom = Skin.DEFAULT.copy(id = "custom", name = "Custom")
        engine.installSkin(custom)
        engine.setActiveSkin("custom")
        assertEquals("custom", engine.activeSkin.value.id)
    }

    @Test
    fun `uninstall reverts to default if active`() {
        val engine = SkinEngine()
        val custom = Skin.DEFAULT.copy(id = "custom", name = "Custom")
        engine.installSkin(custom)
        engine.setActiveSkin("custom")
        engine.uninstallSkin("custom")
        assertEquals("ghost", engine.activeSkin.value.id)
    }

    @Test
    fun `cannot uninstall default ghost skin`() {
        val engine = SkinEngine()
        engine.uninstallSkin("ghost")
        assertTrue(engine.availableSkins.value.any { it.id == "ghost" })
    }

    @Test
    fun `invalid skin is rejected on install`() {
        val engine = SkinEngine()
        val invalid = Skin.DEFAULT.copy(
            id = "bad",
            mascot = Skin.DEFAULT.mascot.copy(grid = emptyList())
        )
        engine.installSkin(invalid)
        assertFalse(engine.availableSkins.value.any { it.id == "bad" })
    }
}
