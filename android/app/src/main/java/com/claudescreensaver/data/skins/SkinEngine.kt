package com.claudescreensaver.data.skins

import android.content.Context
import android.util.Log
import com.claudescreensaver.data.models.Skin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SkinEngine {

    private val _availableSkins = MutableStateFlow(listOf(Skin.DEFAULT))
    val availableSkins: StateFlow<List<Skin>> = _availableSkins.asStateFlow()

    private val _activeSkin = MutableStateFlow(Skin.DEFAULT)
    val activeSkin: StateFlow<Skin> = _activeSkin.asStateFlow()

    /** Load built-in skins from assets/ directory. */
    fun loadBuiltInSkins(context: Context) {
        try {
            val json = context.assets.open("dithered-ghost.json")
                .bufferedReader().use { it.readText() }
            val skin = Skin.fromJson(json)
            val errors = skin.validate()
            if (errors.isEmpty()) {
                installSkin(skin)
                setActiveSkin(skin.id)
                Log.d("SkinEngine", "Loaded dithered-ghost skin (${skin.mascot.grid.size}x${skin.mascot.grid.size})")
            } else {
                Log.w("SkinEngine", "Dithered ghost skin validation failed: $errors")
            }
        } catch (e: Exception) {
            Log.w("SkinEngine", "Failed to load dithered-ghost skin: ${e.message}")
        }
    }

    fun setActiveSkin(skinId: String) {
        val skin = _availableSkins.value.find { it.id == skinId } ?: return
        _activeSkin.value = skin
    }

    fun installSkin(skin: Skin) {
        val errors = skin.validate()
        if (errors.isNotEmpty()) return
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
