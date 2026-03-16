package com.claudescreensaver.data.models

import org.junit.Assert.*
import org.junit.Test

class AgentStatusTest {

    @Test
    fun `parse tool_call status from JSON`() {
        val json = """{"v":1,"status":"tool_call","session_id":"sess-1","instance_name":"dev-laptop","event":"PreToolUse","tool":"Bash","tool_input_summary":"npm test","message":"","requires_input":false,"ts":"2026-03-15T10:00:00Z"}"""
        val status = AgentStatus.fromJson(json)
        assertEquals(AgentState.TOOL_CALL, status.state)
        assertEquals("Bash", status.tool)
        assertEquals("npm test", status.toolInputSummary)
        assertFalse(status.requiresInput)
    }

    @Test
    fun `parse awaiting_input status`() {
        val json = """{"v":1,"status":"awaiting_input","session_id":"sess-1","instance_name":"dev-laptop","event":"Notification","tool":null,"tool_input_summary":"","message":"Permission needed","requires_input":true,"ts":"2026-03-15T10:00:00Z"}"""
        val status = AgentStatus.fromJson(json)
        assertEquals(AgentState.AWAITING_INPUT, status.state)
        assertTrue(status.requiresInput)
        assertEquals("Permission needed", status.message)
    }

    @Test
    fun `unknown status maps to THINKING`() {
        val json = """{"v":1,"status":"unknown_future_state","session_id":"sess-1","instance_name":"dev","event":"FutureEvent","tool":null,"tool_input_summary":"","message":"","requires_input":false,"ts":"2026-03-15T10:00:00Z"}"""
        val status = AgentStatus.fromJson(json)
        assertEquals(AgentState.THINKING, status.state)
    }

    @Test
    fun `DISCONNECTED sentinel has correct defaults`() {
        val d = AgentStatus.DISCONNECTED
        assertEquals(AgentState.IDLE, d.state)
        assertEquals("Not connected", d.message)
        assertNull(d.tool)
        assertFalse(d.requiresInput)
    }

    @Test
    fun `fromJson parses metrics fields`() {
        val json = """
        {
            "status": "thinking",
            "session_id": "test123",
            "instance_name": "dev",
            "event": "PreToolUse",
            "tool": "Bash",
            "tool_input_summary": "ls",
            "message": "",
            "requires_input": false,
            "ts": "2026-03-16T12:00:00Z",
            "sub_agents": [],
            "metrics": {
                "context_percent": 42.5,
                "cost_usd": 0.18,
                "model": "Claude Opus 4.6",
                "cwd": "/home/user/project",
                "lines_added": 50,
                "lines_removed": 10,
                "duration_ms": 30000,
                "api_duration_ms": 20000
            }
        }
        """.trimIndent()

        val status = AgentStatus.fromJson(json)
        assertEquals(42.5f, status.contextPercent!!, 0.1f)
        assertEquals(0.18f, status.costUsd!!, 0.01f)
        assertEquals("Claude Opus 4.6", status.model)
        assertEquals("/home/user/project", status.cwd)
        assertEquals(50, status.linesAdded)
        assertEquals(10, status.linesRemoved)
        assertEquals(30000L, status.durationMs)
        assertEquals(20000L, status.apiDurationMs)
    }

    @Test
    fun `fromJson handles missing metrics gracefully`() {
        val json = """
        {
            "status": "idle",
            "session_id": "test456",
            "instance_name": "dev",
            "event": "Stop",
            "tool_input_summary": "",
            "message": "",
            "requires_input": false,
            "ts": "",
            "sub_agents": []
        }
        """.trimIndent()

        val status = AgentStatus.fromJson(json)
        assertNull(status.contextPercent)
        assertNull(status.costUsd)
        assertNull(status.model)
        assertNull(status.cwd)
        assertNull(status.linesAdded)
        assertNull(status.linesRemoved)
        assertNull(status.durationMs)
        assertNull(status.apiDurationMs)
    }

    @Test
    fun `cwdShort returns last path component`() {
        val json = """
        {
            "status": "thinking",
            "session_id": "t",
            "instance_name": "d",
            "event": "e",
            "tool_input_summary": "",
            "message": "",
            "requires_input": false,
            "ts": "",
            "sub_agents": [],
            "metrics": {"cwd": "/home/user/my-project"}
        }
        """.trimIndent()

        val status = AgentStatus.fromJson(json)
        assertEquals("my-project", status.cwdShort)
    }

    @Test
    fun `cwdShort is null when cwd is null`() {
        val status = AgentStatus.DISCONNECTED
        assertNull(status.cwdShort)
    }

    @Test
    fun `cwdShort handles root path`() {
        val json = """
        {
            "status": "thinking",
            "session_id": "t",
            "instance_name": "d",
            "event": "e",
            "tool_input_summary": "",
            "message": "",
            "requires_input": false,
            "ts": "",
            "sub_agents": [],
            "metrics": {"cwd": "/"}
        }
        """.trimIndent()

        val status = AgentStatus.fromJson(json)
        // "/" substringAfterLast('/') is "" which is filtered by takeIf { isNotEmpty() }
        assertNull(status.cwdShort)
    }
}
