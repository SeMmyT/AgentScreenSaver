package com.claudescreensaver.data

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import com.claudescreensaver.data.models.AgentState

/**
 * Plays notification sounds when agent state changes.
 *
 * Sound packs: place .mp3/.ogg files in res/raw/ with these names:
 * - sound_awaiting_input.mp3  (e.g., SC Probe "awaiting command")
 * - sound_complete.mp3        (e.g., "job's done")
 * - sound_error.mp3           (e.g., "not enough minerals")
 *
 * Falls back to system notification sound if custom files are missing.
 */
class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var lastState: AgentState? = null
    private var enabled = true

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun onStateChange(newState: AgentState) {
        if (!enabled) return
        if (newState == lastState) return
        lastState = newState

        when (newState) {
            AgentState.AWAITING_INPUT -> playSound("sound_awaiting_input")
            AgentState.COMPLETE -> playSound("sound_complete")
            AgentState.ERROR -> playSound("sound_error")
            else -> {} // No sound for other states
        }
    }

    private fun playSound(rawName: String) {
        release()

        // Try custom sound from res/raw/
        val resId = context.resources.getIdentifier(rawName, "raw", context.packageName)
        if (resId != 0) {
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
            return
        }

        // Fallback: system notification sound
        try {
            val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) {
            // Silent fallback
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
