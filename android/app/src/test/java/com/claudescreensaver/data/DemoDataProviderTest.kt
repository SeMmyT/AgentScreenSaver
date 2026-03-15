package com.claudescreensaver.data

import com.claudescreensaver.data.models.AgentState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DemoDataProviderTest {

    @Test
    fun `demoFlow emits four sessions`() = runTest {
        val sessions = DemoDataProvider.demoFlow().first()
        assertEquals(4, sessions.size)
    }

    @Test
    fun `all demo sessions have demo- prefix session IDs`() = runTest {
        val sessions = DemoDataProvider.demoFlow().first()
        sessions.keys.forEach { sessionId ->
            assertTrue(
                "Session ID '$sessionId' should start with 'demo-'",
                sessionId.startsWith("demo-"),
            )
        }
    }

    @Test
    fun `demo sessions have instance name`() = runTest {
        val sessions = DemoDataProvider.demoFlow().first()
        sessions.values.forEach { status ->
            assertEquals("dev-machine", status.instanceName)
        }
    }

    @Test
    fun `demo sessions have timestamps`() = runTest {
        val sessions = DemoDataProvider.demoFlow().first()
        sessions.values.forEach { status ->
            assertTrue(
                "Timestamp should not be empty",
                status.timestamp.isNotEmpty(),
            )
        }
    }

    @Test
    fun `demoFlow cycles states between emissions`() = runTest {
        val emissions = DemoDataProvider.demoFlow().take(2).toList()
        val first = emissions[0]
        val second = emissions[1]

        // At least one session should have changed state between ticks
        val anyStateChanged = first.keys.any { key ->
            first[key]?.state != second[key]?.state
        }
        assertTrue("States should cycle between emissions", anyStateChanged)
    }

    @Test
    fun `tool_call sessions have tool and file info`() = runTest {
        val sessions = DemoDataProvider.demoFlow().first()
        val toolCallSessions = sessions.values.filter { it.state == AgentState.TOOL_CALL }
        toolCallSessions.forEach { status ->
            assertNotNull("Tool should not be null for TOOL_CALL state", status.tool)
            assertTrue(
                "Tool input summary should not be empty for TOOL_CALL state",
                status.toolInputSummary.isNotEmpty(),
            )
        }
    }

    @Test
    fun `awaiting_input sessions have requiresInput true and message`() = runTest {
        val sessions = DemoDataProvider.demoFlow().first()
        val awaitingSessions = sessions.values.filter { it.state == AgentState.AWAITING_INPUT }
        awaitingSessions.forEach { status ->
            assertTrue("requiresInput should be true", status.requiresInput)
            assertTrue("message should not be empty", status.message.isNotEmpty())
        }
    }
}
