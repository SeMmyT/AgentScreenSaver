package com.claudescreensaver.data.models

import org.json.JSONObject

enum class AgentState(val value: String) {
    IDLE("idle"),
    THINKING("thinking"),
    TOOL_CALL("tool_call"),
    AWAITING_INPUT("awaiting_input"),
    ERROR("error"),
    COMPLETE("complete");

    companion object {
        fun fromString(s: String): AgentState =
            entries.firstOrNull { it.value == s } ?: THINKING
    }
}

data class SubAgentInfo(
    val agentId: String,
    val agentType: String,
    val status: String,
    val name: String = "",
)

data class AgentStatus(
    val state: AgentState,
    val sessionId: String,
    val instanceName: String,
    val event: String,
    val tool: String?,
    val toolInputSummary: String,
    val message: String,
    val requiresInput: Boolean,
    val agentId: String? = null,
    val agentType: String? = null,
    val timestamp: String = "",
    val userMessage: String? = null,
    val interrupted: Boolean = false,
    val subAgents: List<SubAgentInfo> = emptyList(),
    val customFrames: List<String>? = null,
    val customLabel: String? = null,
    val contextPercent: Float? = null,
    val costUsd: Float? = null,
    val model: String? = null,
    val cwd: String? = null,
    val linesAdded: Int? = null,
    val linesRemoved: Int? = null,
    val durationMs: Long? = null,
    val apiDurationMs: Long? = null,
) {
    val cwdShort: String?
        get() = cwd?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }

    companion object {
        fun fromJson(json: String): AgentStatus {
            val obj = JSONObject(json)
            val metricsObj = obj.optJSONObject("metrics")
            return AgentStatus(
                state = AgentState.fromString(obj.optString("status", "thinking")),
                sessionId = obj.optString("session_id", ""),
                instanceName = obj.optString("instance_name", ""),
                event = obj.optString("event", ""),
                tool = obj.optString("tool").takeIf { it.isNotEmpty() && it != "null" },
                toolInputSummary = obj.optString("tool_input_summary", ""),
                message = obj.optString("message", ""),
                requiresInput = obj.optBoolean("requires_input", false),
                agentId = obj.optString("agent_id").takeIf { it.isNotEmpty() && it != "null" },
                agentType = obj.optString("agent_type").takeIf { it.isNotEmpty() && it != "null" },
                timestamp = obj.optString("ts", ""),
                userMessage = obj.optString("user_message").takeIf { it.isNotEmpty() && it != "null" },
                interrupted = obj.optBoolean("interrupted", false),
                subAgents = buildList {
                    val subAgentsArray = obj.optJSONArray("sub_agents")
                    if (subAgentsArray != null) {
                        for (i in 0 until subAgentsArray.length()) {
                            val sa = subAgentsArray.getJSONObject(i)
                            add(SubAgentInfo(
                                agentId = sa.optString("agent_id", ""),
                                agentType = sa.optString("agent_type", ""),
                                status = sa.optString("status", "running"),
                                name = sa.optString("name", ""),
                            ))
                        }
                    }
                },
                customFrames = run {
                    val customFramesArray = obj.optJSONArray("custom_frames")
                    if (customFramesArray != null) {
                        buildList {
                            for (i in 0 until customFramesArray.length()) {
                                add(customFramesArray.getString(i))
                            }
                        }
                    } else null
                },
                customLabel = obj.optString("custom_label").takeIf { it.isNotEmpty() && it != "null" },
                contextPercent = metricsObj?.optDouble("context_percent")?.toFloat()?.takeIf { !it.isNaN() },
                costUsd = metricsObj?.optDouble("cost_usd")?.toFloat()?.takeIf { !it.isNaN() },
                model = metricsObj?.optString("model")?.takeIf { it.isNotEmpty() && it != "null" },
                cwd = metricsObj?.optString("cwd")?.takeIf { it.isNotEmpty() && it != "null" },
                linesAdded = metricsObj?.optInt("lines_added", -1)?.takeIf { it >= 0 },
                linesRemoved = metricsObj?.optInt("lines_removed", -1)?.takeIf { it >= 0 },
                durationMs = metricsObj?.optLong("duration_ms", -1)?.takeIf { it >= 0 },
                apiDurationMs = metricsObj?.optLong("api_duration_ms", -1)?.takeIf { it >= 0 },
            )
        }

        val DISCONNECTED = AgentStatus(
            state = AgentState.IDLE,
            sessionId = "",
            instanceName = "",
            event = "",
            tool = null,
            toolInputSummary = "",
            message = "Not connected",
            requiresInput = false,
        )
    }
}
