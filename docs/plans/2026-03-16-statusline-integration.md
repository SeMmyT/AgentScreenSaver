# CC Statusline → Bridge → Android Integration

> **For Claude:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task.

**Goal:** Pipe Claude Code's statusline metrics (context window, cost, model, CWD, code churn) through the bridge to the Android app, rendering context bars, cost tickers, model badges, and churn counters on each session pane.

**Architecture:** A statusline script reads CC's JSON from stdin, POSTs the metrics to a new `/session/{id}/metrics` bridge endpoint, and passes through to the existing ccstatusline for terminal display. The bridge merges metrics into StatusUpdate. Android parses the new fields and renders them in SessionCard's bottom status bar and a thin context progress bar.

**Tech Stack:** Bash+curl (statusline script), Python/aiohttp (bridge endpoint), Kotlin/Compose (Android UI)

---

## Task 1: Add metrics fields to bridge StatusUpdate model

**Files:**
- Modify: `bridge/bridge/models.py`
- Test: `bridge/tests/test_models.py`

**Step 1: Write the failing test**

```python
# In bridge/tests/test_models.py, add:
def test_status_update_includes_metrics():
    update = StatusUpdate(
        status=AgentState.THINKING,
        session_id="abc",
        instance_name="test",
        event="PreToolUse",
    )
    d = update.to_dict()
    assert "metrics" in d
    assert d["metrics"]["context_percent"] is None
    assert d["metrics"]["cost_usd"] is None
    assert d["metrics"]["model"] is None
    assert d["metrics"]["cwd"] is None
    assert d["metrics"]["lines_added"] is None
    assert d["metrics"]["lines_removed"] is None


def test_status_update_metrics_round_trip():
    update = StatusUpdate(
        status=AgentState.THINKING,
        session_id="abc",
        instance_name="test",
        event="PreToolUse",
        context_percent=45.2,
        cost_usd=0.37,
        model="Claude Opus 4.6",
        cwd="/home/user/project",
        lines_added=142,
        lines_removed=38,
        duration_ms=60000,
        api_duration_ms=45000,
    )
    d = update.to_dict()
    m = d["metrics"]
    assert m["context_percent"] == 45.2
    assert m["cost_usd"] == 0.37
    assert m["model"] == "Claude Opus 4.6"
    assert m["cwd"] == "/home/user/project"
    assert m["lines_added"] == 142
    assert m["lines_removed"] == 38
    assert m["duration_ms"] == 60000
    assert m["api_duration_ms"] == 45000
```

**Step 2: Run test to verify it fails**

Run: `cd bridge && uv run pytest tests/test_models.py -k "metrics" -v`
Expected: FAIL — `context_percent` not a field on StatusUpdate

**Step 3: Add metrics fields to StatusUpdate**

Add these fields to the `StatusUpdate` dataclass:

```python
# New fields on StatusUpdate:
context_percent: float | None = None
cost_usd: float | None = None
model: str | None = None
cwd: str | None = None
lines_added: int | None = None
lines_removed: int | None = None
duration_ms: int | None = None
api_duration_ms: int | None = None
```

Update `to_dict()` to nest them under `"metrics"`:

```python
# In to_dict(), add before return:
d["metrics"] = {
    "context_percent": self.context_percent,
    "cost_usd": self.cost_usd,
    "model": self.model,
    "cwd": self.cwd,
    "lines_added": self.lines_added,
    "lines_removed": self.lines_removed,
    "duration_ms": self.duration_ms,
    "api_duration_ms": self.api_duration_ms,
}
# Remove individual metric keys from top-level asdict output
for k in ("context_percent", "cost_usd", "model", "cwd", "lines_added", "lines_removed", "duration_ms", "api_duration_ms"):
    d.pop(k, None)
```

**Step 4: Run test to verify it passes**

Run: `cd bridge && uv run pytest tests/test_models.py -k "metrics" -v`
Expected: PASS

**Step 5: Commit**

```bash
git add bridge/bridge/models.py bridge/tests/test_models.py
git commit -m "feat(bridge): add metrics fields to StatusUpdate for statusline data"
```

---

## Task 2: Add POST /session/{id}/metrics endpoint to bridge

**Files:**
- Modify: `bridge/bridge/server.py`
- Test: `bridge/tests/test_server.py`

**Step 1: Write the failing test**

```python
# In bridge/tests/test_server.py, add:
async def test_metrics_endpoint_enriches_session(self):
    """POST /session/{id}/metrics merges metrics into existing session."""
    # First create a session via event
    event = {
        "hook_event_name": "PreToolUse",
        "session_id": "metrics-test",
        "tool_name": "Bash",
    }
    await self.client.request("POST", "/event", json=event)

    # Post metrics
    metrics = {
        "context_percent": 42.5,
        "cost_usd": 0.18,
        "model": "Claude Opus 4.6",
        "cwd": "/home/user/project",
        "lines_added": 50,
        "lines_removed": 10,
        "duration_ms": 30000,
        "api_duration_ms": 20000,
    }
    resp = await self.client.request(
        "POST", "/session/metrics-test/metrics", json=metrics
    )
    assert resp.status == 202

    # Verify metrics are in status snapshot
    resp = await self.client.request("GET", "/status")
    data = await resp.json()
    m = data["metrics-test"]["metrics"]
    assert m["context_percent"] == 42.5
    assert m["cost_usd"] == 0.18
    assert m["model"] == "Claude Opus 4.6"


async def test_metrics_without_session_creates_placeholder(self):
    """POST /session/{id}/metrics works even if session doesn't exist yet."""
    metrics = {"context_percent": 10.0, "model": "Claude Sonnet 4.6"}
    resp = await self.client.request(
        "POST", "/session/new-session/metrics", json=metrics
    )
    assert resp.status == 202
```

**Step 2: Run test to verify it fails**

Run: `cd bridge && uv run pytest tests/test_server.py -k "metrics" -v`
Expected: FAIL — 404 on /session/{id}/metrics

**Step 3: Add the metrics handler and route**

```python
# In server.py, add handler:
async def metrics_handler(request: web.Request) -> web.Response:
    """POST /session/{session_id}/metrics — update session metrics from statusline."""
    session_id = request.match_info["session_id"]
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)

    sessions: dict[str, StatusUpdate] = request.app["sessions"]
    update = sessions.get(session_id)

    if update is None:
        # Create a minimal placeholder session
        update = StatusUpdate(
            status=AgentState.THINKING,
            session_id=session_id,
            instance_name=request.app["instance_name"],
            event="statusline",
        )
        sessions[session_id] = update

    # Merge metrics
    if "context_percent" in data:
        update.context_percent = data["context_percent"]
    if "cost_usd" in data:
        update.cost_usd = data["cost_usd"]
    if "model" in data:
        update.model = data["model"]
    if "cwd" in data:
        update.cwd = data["cwd"]
    if "lines_added" in data:
        update.lines_added = data["lines_added"]
    if "lines_removed" in data:
        update.lines_removed = data["lines_removed"]
    if "duration_ms" in data:
        update.duration_ms = data["duration_ms"]
    if "api_duration_ms" in data:
        update.api_duration_ms = data["api_duration_ms"]

    # Fan out enriched update to SSE clients
    payload = update.to_json()
    for queue in request.app["sse_clients"]:
        try:
            queue.put_nowait(payload)
        except asyncio.QueueFull:
            pass

    return web.json_response({"accepted": True}, status=202)

# In create_app, add route:
app.router.add_post("/session/{session_id}/metrics", metrics_handler)
```

**Step 4: Run tests**

Run: `cd bridge && uv run pytest tests/test_server.py -k "metrics" -v`
Expected: PASS

**Step 5: Commit**

```bash
git add bridge/bridge/server.py bridge/tests/test_server.py
git commit -m "feat(bridge): add POST /session/{id}/metrics endpoint for statusline data"
```

---

## Task 3: Create the statusline-to-bridge script

**Files:**
- Create: `tools/statusline-bridge.sh`
- Modify: `~/.claude/settings.json` (manual — document the change)

This script reads CC's statusline JSON from stdin, extracts metrics, POSTs them to the bridge, and passes through to ccstatusline for terminal display.

**Step 1: Write the script**

```bash
#!/usr/bin/env bash
# statusline-bridge.sh — Pipe CC statusline metrics to the bridge server.
# Reads JSON from stdin, POSTs metrics to bridge, passes through to ccstatusline.
set -uo pipefail

BRIDGE_URL="${CCSAVER_BRIDGE_URL:-http://localhost:4001}"
INPUT=$(cat)

# Extract fields with jq (fast, single parse)
METRICS=$(echo "$INPUT" | jq -c '{
  session_id: .session_id,
  context_percent: (.context_window.used_percentage // null),
  cost_usd: (.cost.total_cost_usd // null),
  model: (.model.display_name // null),
  cwd: (.workspace.current_dir // null),
  lines_added: (.cost.total_lines_added // null),
  lines_removed: (.cost.total_lines_removed // null),
  duration_ms: (.cost.total_duration_ms // null),
  api_duration_ms: (.cost.total_api_duration_ms // null)
}' 2>/dev/null)

SESSION_ID=$(echo "$METRICS" | jq -r '.session_id // empty' 2>/dev/null)

# POST to bridge (fire-and-forget, don't block statusline rendering)
if [ -n "$SESSION_ID" ]; then
  curl -s -X POST \
    "$BRIDGE_URL/session/$SESSION_ID/metrics" \
    -H "Content-Type: application/json" \
    -d "$METRICS" \
    --connect-timeout 1 \
    --max-time 2 \
    >/dev/null 2>&1 &
fi

# Pass through to ccstatusline for terminal display
echo "$INPUT" | npx -y ccstatusline@latest
```

**Step 2: Make executable and test manually**

```bash
chmod +x tools/statusline-bridge.sh

# Test with sample JSON:
echo '{"session_id":"test123","context_window":{"used_percentage":42.5},"cost":{"total_cost_usd":0.18},"model":{"display_name":"Claude Opus 4.6"},"workspace":{"current_dir":"/home/user/project"},"cost":{"total_lines_added":50,"total_lines_removed":10,"total_duration_ms":30000,"total_api_duration_ms":20000}}' | bash tools/statusline-bridge.sh
```

Expected: ccstatusline renders to terminal AND bridge receives POST (check with `curl localhost:4001/status`)

**Step 3: Document settings.json change**

User must update `~/.claude/settings.json`:
```json
{
  "statusLine": {
    "type": "command",
    "command": "bash /home/semmy/codeprojects/CCReStatus/tools/statusline-bridge.sh",
    "padding": 0
  }
}
```

**Step 4: Commit**

```bash
git add tools/statusline-bridge.sh
git commit -m "feat(tools): add statusline-bridge.sh to pipe CC metrics to bridge"
```

---

## Task 4: Add metrics fields to Android AgentStatus model

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/data/models/AgentStatus.kt`
- Test: `android/app/src/test/java/com/claudescreensaver/data/models/AgentStatusTest.kt` (create)

**Step 1: Write the failing test**

```kotlin
// AgentStatusTest.kt
package com.claudescreensaver.data.models

import org.junit.Test
import org.junit.Assert.*

class AgentStatusTest {

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
    }

    @Test
    fun `cwd is shortened to last path component`() {
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
}
```

**Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.models.AgentStatusTest" 2>&1 | tail -20`
Expected: FAIL — `contextPercent` doesn't exist on AgentStatus

**Step 3: Add metrics fields to AgentStatus**

Add to the `AgentStatus` data class:
```kotlin
// New fields:
val contextPercent: Float? = null,
val costUsd: Float? = null,
val model: String? = null,
val cwd: String? = null,
val linesAdded: Int? = null,
val linesRemoved: Int? = null,
val durationMs: Long? = null,
val apiDurationMs: Long? = null,
```

Add a computed property:
```kotlin
val cwdShort: String?
    get() = cwd?.substringAfterLast('/')?.takeIf { it.isNotEmpty() }
```

Update `fromJson()` to parse the `metrics` object:
```kotlin
// After existing parsing, before return:
val metricsObj = obj.optJSONObject("metrics")

// In the return constructor:
contextPercent = metricsObj?.optDouble("context_percent")?.toFloat()?.takeIf { !it.isNaN() },
costUsd = metricsObj?.optDouble("cost_usd")?.toFloat()?.takeIf { !it.isNaN() },
model = metricsObj?.optString("model")?.takeIf { it.isNotEmpty() && it != "null" },
cwd = metricsObj?.optString("cwd")?.takeIf { it.isNotEmpty() && it != "null" },
linesAdded = metricsObj?.optInt("lines_added", -1)?.takeIf { it >= 0 },
linesRemoved = metricsObj?.optInt("lines_removed", -1)?.takeIf { it >= 0 },
durationMs = metricsObj?.optLong("duration_ms", -1)?.takeIf { it >= 0 },
apiDurationMs = metricsObj?.optLong("api_duration_ms", -1)?.takeIf { it >= 0 },
```

**Step 4: Run tests**

Run: `cd android && ./gradlew testDebugUnitTest --tests "com.claudescreensaver.data.models.AgentStatusTest" 2>&1 | tail -20`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/data/models/AgentStatus.kt \
        android/app/src/test/java/com/claudescreensaver/data/models/AgentStatusTest.kt
git commit -m "feat(android): parse statusline metrics in AgentStatus model"
```

---

## Task 5: Add context bar to SessionCard

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/components/SessionCard.kt`

**Step 1: Add context progress bar to SessionCard**

At the very bottom of SessionCard (after the bottom status Row, before the closing `}`), add a context bar:

```kotlin
// Context bar — thin progress indicator at bottom of pane
status.contextPercent?.let { pct ->
    Spacer(Modifier.height(4.dp))
    val barColor = when {
        pct >= 90f -> StatusCritical
        pct >= 70f -> StatusWarning
        else -> StatusRunning
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(1.5.dp))
            .background(Color(0xFF2A2A2A)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                .clip(RoundedCornerShape(1.5.dp))
                .background(barColor),
        )
    }
}
```

**Step 2: Add metrics info to title bar**

Replace the existing bottom status Row with a richer one:

```kotlin
// Bottom status line — enhanced with metrics
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    // Left: event + model
    Row {
        Text(
            text = status.event.lowercase(),
            fontFamily = mono,
            fontSize = 9.sp,
            color = ClaudeGray.copy(alpha = 0.4f),
        )
        status.model?.let { model ->
            Text(
                text = " · $model",
                fontFamily = mono,
                fontSize = 9.sp,
                color = ClaudeAccent.copy(alpha = 0.4f),
                maxLines = 1,
            )
        }
    }

    // Right: cost + churn
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        status.costUsd?.let { cost ->
            Text(
                text = "$${String.format("%.2f", cost)}",
                fontFamily = mono,
                fontSize = 9.sp,
                color = ClaudeGray.copy(alpha = 0.5f),
            )
        }
        if (status.linesAdded != null || status.linesRemoved != null) {
            Text(
                text = "+${status.linesAdded ?: 0}/-${status.linesRemoved ?: 0}",
                fontFamily = mono,
                fontSize = 9.sp,
                color = StatusRunning.copy(alpha = 0.5f),
            )
        }
        status.cwdShort?.let { dir ->
            Text(
                text = dir,
                fontFamily = mono,
                fontSize = 9.sp,
                color = ClaudeGray.copy(alpha = 0.3f),
                maxLines = 1,
            )
        }
    }
}
```

**Step 3: Verify build**

Run: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/components/SessionCard.kt
git commit -m "feat(android): add context bar, model badge, cost ticker, and churn to SessionCard"
```

---

## Task 6: Add metrics to SessionFullScreen

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SessionFullScreen.kt`

**Step 1: Add metrics header row below the title bar**

After the title bar Row, before the scrollable content, add:

```kotlin
// Metrics bar — context, cost, model, churn
if (status.contextPercent != null || status.model != null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Model + CWD
        Row {
            status.model?.let { model ->
                Text(model, fontFamily = mono, fontSize = 10.sp, color = ClaudeAccent.copy(alpha = 0.7f))
            }
            status.cwdShort?.let { dir ->
                Text(" · $dir", fontFamily = mono, fontSize = 10.sp, color = ClaudeGray.copy(alpha = 0.5f))
            }
        }

        // Cost + churn + context%
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            status.costUsd?.let { cost ->
                Text(
                    "$${String.format("%.2f", cost)}",
                    fontFamily = mono, fontSize = 10.sp,
                    color = ClaudeGray.copy(alpha = 0.6f),
                )
            }
            if (status.linesAdded != null || status.linesRemoved != null) {
                Text(
                    "+${status.linesAdded ?: 0}/-${status.linesRemoved ?: 0}",
                    fontFamily = mono, fontSize = 10.sp,
                    color = StatusRunning.copy(alpha = 0.5f),
                )
            }
            status.contextPercent?.let { pct ->
                val ctxColor = when {
                    pct >= 90f -> StatusCritical
                    pct >= 70f -> StatusWarning
                    else -> StatusRunning
                }
                Text(
                    "${pct.toInt()}%",
                    fontFamily = mono, fontSize = 10.sp,
                    color = ctxColor,
                )
            }
        }
    }

    // Context progress bar
    status.contextPercent?.let { pct ->
        val barColor = when {
            pct >= 90f -> StatusCritical
            pct >= 70f -> StatusWarning
            else -> StatusRunning
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFF2A2A2A)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (pct / 100f).coerceIn(0f, 1f))
                    .background(barColor),
            )
        }
    }
}
```

**Step 2: Verify build**

Run: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/SessionFullScreen.kt
git commit -m "feat(android): add metrics bar to SessionFullScreen (model, cost, context, churn)"
```

---

## Task 7: Add context bar to SimpleStatusScreen

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt`

**Step 1: Add metrics line to SimpleAsciiPane**

In `SimpleAsciiPane`, after the header row (state label + tool) and before the ASCII frame text, add a compact metrics line:

```kotlin
// Compact metrics line
val metrics = buildString {
    status.model?.let { append(it.take(12)) }
    status.contextPercent?.let {
        if (isNotEmpty()) append(" ")
        append("ctx:${it.toInt()}%")
    }
    status.costUsd?.let {
        if (isNotEmpty()) append(" ")
        append("$${String.format("%.2f", it)}")
    }
    if (status.linesAdded != null) {
        if (isNotEmpty()) append(" ")
        append("+${status.linesAdded}/-${status.linesRemoved ?: 0}")
    }
}
if (metrics.isNotEmpty()) {
    Text(
        text = metrics,
        fontFamily = FontFamily.Monospace,
        fontSize = if (compact) 8.sp else 10.sp,
        color = ClaudeGray.copy(alpha = 0.5f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(2.dp))
}
```

**Step 2: Verify build**

Run: `cd android && ./gradlew assembleDebug 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt
git commit -m "feat(android): add compact metrics line to SimpleStatusScreen panes"
```

---

## Verification

1. All bridge tests pass: `cd bridge && uv run pytest -v`
2. All Android tests pass: `cd android && ./gradlew testDebugUnitTest`
3. App builds: `cd android && ./gradlew assembleDebug`
4. Manual E2E: Start bridge, update statusline setting, start a CC session, verify metrics flow to phone:
   - Context bar appears at bottom of SessionCard
   - Model name shows in status line
   - Cost shows as `$0.XX`
   - Lines added/removed show as `+N/-N`
   - CWD shows shortened project name
5. Statusline still renders normally in terminal (passthrough to ccstatusline works)
