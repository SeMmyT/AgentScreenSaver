package com.claudescreensaver.data

import com.claudescreensaver.data.models.AgentState
import com.claudescreensaver.data.models.AgentStatus
import com.claudescreensaver.data.models.SubAgentInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object DemoDataProvider {

    private val demoSessions = listOf(
        AgentStatus(
            state = AgentState.TOOL_CALL,
            sessionId = "demo-sess-1",
            instanceName = "dev-machine",
            event = "PreToolUse",
            tool = "Edit",
            toolInputSummary = "src/components/App.tsx",
            message = "",
            requiresInput = false,
            agentType = "general-purpose",
        ),
        AgentStatus(
            state = AgentState.THINKING,
            sessionId = "demo-sess-2",
            instanceName = "dev-machine",
            event = "PostToolUse",
            tool = "Bash",
            toolInputSummary = "npm test -- --watch",
            message = "",
            requiresInput = false,
            agentType = "gsd-executor",
            subAgents = listOf(
                SubAgentInfo("demo-agent-1", "Explore", "running"),
                SubAgentInfo("demo-agent-2", "general-purpose", "running"),
            ),
        ),
        AgentStatus(
            state = AgentState.AWAITING_INPUT,
            sessionId = "demo-sess-3",
            instanceName = "dev-machine",
            event = "Notification",
            tool = null,
            toolInputSummary = "",
            message = "Claude is waiting for your input",
            requiresInput = true,
            userMessage = "fix the login bug",
        ),
        AgentStatus(
            state = AgentState.TOOL_CALL,
            sessionId = "demo-sess-4",
            instanceName = "dev-machine",
            event = "PreToolUse",
            tool = "Read",
            toolInputSummary = "docs/architecture.md",
            message = "",
            requiresInput = false,
            agentType = "Explore",
        ),
    )

    /**
     * Cycles through different state combinations every few seconds,
     * simulating what the app looks like with live agent data.
     */
    fun demoFlow(): Flow<Map<String, AgentStatus>> = flow {
        val states = listOf(
            AgentState.TOOL_CALL, AgentState.THINKING, AgentState.AWAITING_INPUT,
            AgentState.COMPLETE, AgentState.IDLE, AgentState.TOOL_CALL,
        )
        val tools = listOf("Edit", "Bash", "Read", "Write", "Grep", "Glob")
        val files = listOf(
            "src/main/App.kt", "tests/test_server.py", "package.json",
            "README.md", "bridge/server.py", "build.gradle.kts",
        )
        var tick = 0

        while (true) {
            val sessions = demoSessions.mapIndexed { i, base ->
                val stateIdx = (tick + i) % states.size
                val newState = states[stateIdx]
                base.copy(
                    state = newState,
                    tool = if (newState == AgentState.TOOL_CALL) tools[(tick + i) % tools.size] else base.tool,
                    toolInputSummary = if (newState == AgentState.TOOL_CALL) files[(tick + i) % files.size] else base.toolInputSummary,
                    requiresInput = newState == AgentState.AWAITING_INPUT,
                    message = if (newState == AgentState.AWAITING_INPUT) "Claude is waiting for your input" else "",
                    timestamp = System.currentTimeMillis().toString(),
                )
            }.associateBy { it.sessionId }

            emit(sessions)
            tick++
            delay(3000) // Update every 3 seconds
        }
    }
}
