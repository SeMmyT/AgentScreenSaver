# Screensaver Modes & Live Data Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use executing-plans to implement this plan task-by-task.

**Goal:** Add real-time user input display, sub-agent visibility, interrupt state, and a Simple ASCII animation mode alongside the existing Advanced 4-pane grid.

**Architecture:** Four concerns — (A) bridge enrichment: forward user prompts and distinguish interrupts from completions; (B) Android model updates: parse new fields; (C) Advanced mode polish: wire sub-agents, show interrupts and typed text prominently; (D) Simple mode: new full-screen ASCII animation composable toggled from settings.

**Tech Stack:** Python aiohttp (bridge), Kotlin/Jetpack Compose (Android), SSE transport

---

## Task 1: Bridge — Add UserPromptSubmit hook and interrupt distinction

**Files:**
- Modify: `/home/semmy/.claude/settings.json` (add UserPromptSubmit hook)
- Modify: `/home/semmy/codeprojects/CCReStatus/bridge/bridge/models.py`
- Modify: `/home/semmy/codeprojects/CCReStatus/bridge/bridge/server.py`
- Test: `/home/semmy/codeprojects/CCReStatus/bridge/tests/test_models.py`

### Step 1: Add UserPromptSubmit hook to settings.json

In `/home/semmy/.claude/settings.json`, add a new entry to the hooks array for `UserPromptSubmit`:

```json
"UserPromptSubmit": [
  {
    "type": "http",
    "url": "http://localhost:4001/event",
    "timeout": 5000
  }
]
```

This fires when the user submits a new prompt, providing `message` with the user's text.

### Step 2: Write failing tests for new event mappings

In `tests/test_models.py`, add tests:

```python
def test_user_prompt_submit_maps_to_thinking_with_message():
    raw = {
        "hook_event_name": "UserPromptSubmit",
        "session_id": "s1",
        "message": "check android",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.status == AgentState.THINKING
    assert update.message == "check android"
    assert update.user_message == "check android"

def test_stop_with_stop_hook_active_maps_to_interrupted():
    raw = {
        "hook_event_name": "Stop",
        "session_id": "s1",
        "stop_hook_active": True,
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.status == AgentState.AWAITING_INPUT
    assert update.interrupted is True

def test_stop_without_stop_hook_active_maps_to_complete():
    raw = {
        "hook_event_name": "Stop",
        "session_id": "s1",
        "stop_hook_active": False,
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.status == AgentState.COMPLETE
    assert update.interrupted is False
```

### Step 3: Run tests to verify they fail

Run: `cd /home/semmy/codeprojects/CCReStatus/bridge && uv run pytest tests/test_models.py -v -k "user_prompt_submit or stop_with_stop or stop_without_stop"`
Expected: FAIL — `user_message` and `interrupted` fields don't exist.

### Step 4: Update models.py

In `models.py`:

1. Add `user_message` field to `HookEvent`:
```python
@dataclass
class HookEvent:
    ...
    user_message: str | None = None  # from UserPromptSubmit
```

In `from_dict`, parse it:
```python
user_message=raw.get("message") if raw.get("hook_event_name") == "UserPromptSubmit" else None,
```

2. Add `user_message` and `interrupted` fields to `StatusUpdate`:
```python
@dataclass
class StatusUpdate:
    ...
    user_message: str | None = None
    interrupted: bool = False
```

3. Update `StatusUpdate.from_event()`:
- Map `UserPromptSubmit` → `AgentState.THINKING` and set `user_message = event.user_message`
- Map `Stop` with `event.stop_hook_active == True` → `AgentState.AWAITING_INPUT` with `interrupted = True`
- Map `Stop` with `event.stop_hook_active == False` → `AgentState.COMPLETE`

4. Update `to_dict()` and `to_json()` to include new fields:
```python
def to_dict(self):
    d = {
        ...
        "user_message": self.user_message,
        "interrupted": self.interrupted,
    }
    return d
```

### Step 5: Run tests to verify they pass

Run: `cd /home/semmy/codeprojects/CCReStatus/bridge && uv run pytest tests/test_models.py -v`
Expected: All PASS.

### Step 6: Commit

```bash
git add bridge/bridge/models.py bridge/tests/test_models.py
git commit -m "feat(bridge): add UserPromptSubmit support and interrupt distinction"
```

---

## Task 2: Android — Parse new fields (user_message, interrupted, sub_agents)

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/data/models/AgentStatus.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/data/DemoDataProvider.kt`

### Step 1: Add fields to AgentStatus

```kotlin
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
    // New fields:
    val userMessage: String? = null,
    val interrupted: Boolean = false,
    val subAgents: List<SubAgentInfo> = emptyList(),
)

data class SubAgentInfo(
    val agentId: String,
    val agentType: String,
    val status: String,
)
```

### Step 2: Update fromJson parsing

In `AgentStatus.fromJson()`, parse:

```kotlin
val userMessage = obj.optString("user_message", null)
val interrupted = obj.optBoolean("interrupted", false)
val subAgentsArray = obj.optJSONArray("sub_agents")
val subAgents = buildList {
    if (subAgentsArray != null) {
        for (i in 0 until subAgentsArray.length()) {
            val sa = subAgentsArray.getJSONObject(i)
            add(SubAgentInfo(
                agentId = sa.optString("agent_id", ""),
                agentType = sa.optString("agent_type", ""),
                status = sa.optString("status", "running"),
            ))
        }
    }
}
```

### Step 3: Update DemoDataProvider to include sub-agents in demo data

Add 1-2 sub-agents to the "thinking" demo session so Simple and Advanced modes can preview them:

```kotlin
subAgents = listOf(
    SubAgentInfo("demo-agent-1", "Explore", "running"),
    SubAgentInfo("demo-agent-2", "general-purpose", "running"),
)
```

### Step 4: Build and verify

Run: `JAVA_HOME=/home/linuxbrew/.linuxbrew/opt/openjdk@21 ANDROID_HOME=/home/semmy/android-sdk ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

### Step 5: Commit

```bash
git add android/app/src/main/java/com/claudescreensaver/data/models/AgentStatus.kt \
        android/app/src/main/java/com/claudescreensaver/data/DemoDataProvider.kt
git commit -m "feat(android): parse user_message, interrupted, and sub_agents from SSE"
```

---

## Task 3: Advanced Mode — Show sub-agents, user input, and interrupts in SessionCard

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/components/SessionCard.kt`

### Step 1: Add sub-agent count badge to title bar

After the state label in the title bar, if `status.subAgents` is not empty, show a badge:

```kotlin
if (status.subAgents.isNotEmpty()) {
    Text(
        text = "${status.subAgents.size} agents",
        color = StatusStandby,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
    )
}
```

### Step 2: Show user input when present

Below the tool prompt line, if `status.userMessage` is not null, show it styled as user input:

```kotlin
status.userMessage?.let { msg ->
    Text(
        text = "you: $msg",
        color = ClaudeAccent,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
```

### Step 3: Show interrupt state prominently

When `status.interrupted` is true, show a pulsing "INTERRUPTED" label in the content area:

```kotlin
if (status.interrupted) {
    Text(
        text = ">>> INTERRUPTED <<<",
        color = StatusWarning,
        fontSize = 13.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.alpha(pulseAlpha),  // reuse existing pulse
    )
}
```

### Step 4: Show sub-agent list at bottom of card (collapsed)

If sub-agents exist, show a compact list below the message:

```kotlin
if (status.subAgents.isNotEmpty()) {
    Spacer(modifier = Modifier.height(4.dp))
    status.subAgents.forEach { agent ->
        Text(
            text = "  ${if (agent.status == "running") ">" else "-"} ${agent.agentType}",
            color = if (agent.status == "running") StatusRunning else StatusDisabled,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
```

### Step 5: Build and verify

Run: `JAVA_HOME=... ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

### Step 6: Install and screenshot

```bash
bash tools/adbtool.sh install && bash tools/adbtool.sh restart
sleep 3 && bash tools/adbtool.sh screenshot /tmp/phone.png
```

### Step 7: Commit

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/components/SessionCard.kt
git commit -m "feat(android): show sub-agents, user input, and interrupts in session cards"
```

---

## Task 4: Simple Mode — ASCII animation composable

**Files:**
- Create: `android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt`

### Step 1: Create SimpleStatusScreen

Full-screen composable showing a single ASCII art animation based on the hottest session's state. The animation cycles through 4 frames at ~600ms per frame.

```kotlin
@Composable
fun SimpleStatusScreen(
    uiState: UiState,
    modifier: Modifier = Modifier,
) {
    val status = uiState.sessions.values
        .maxByOrNull { stateWeight(it.state) }
        ?: AgentStatus.DISCONNECTED

    val frames = asciiFramesFor(status.state)
    var frameIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(status.state) {
        frameIndex = 0
        while (true) {
            delay(600)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ClaudeBgDark),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // ASCII art
            Text(
                text = frames[frameIndex],
                color = stateColor(status.state),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // State label
            Text(
                text = stateLabel(status.state),
                color = ClaudeTextLight,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
            )

            // Context line (tool name, user message, etc.)
            val context = status.userMessage
                ?: status.tool
                ?: status.message.takeIf { it.isNotBlank() }
            if (context != null) {
                Text(
                    text = context,
                    color = ClaudeGray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            // Sub-agent count
            val agentCount = status.subAgents.size
            if (agentCount > 0) {
                Text(
                    text = "$agentCount agent${if (agentCount > 1) "s" else ""} running",
                    color = StatusStandby,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
```

### Step 2: Define ASCII animation frames

```kotlin
private fun asciiFramesFor(state: AgentState): List<String> = when (state) {
    AgentState.IDLE -> listOf(
        """
          .  *  .
        .    _    .
            |_|
         ___| |___
        |         |
        |_________|
        """.trimIndent(),
        """
        .    *    .
          .  _  .
            |_|
         ___| |___
        |         |
        |_________|
        """.trimIndent(),
        """
             *
        .    _    .
            |_|
         ___| |___
        |         |
        |_________|
        """.trimIndent(),
        """
          .  *  .
             _
            |_|
         ___| |___
        |         |
        |_________|
        """.trimIndent(),
    )
    AgentState.THINKING -> listOf(
        """
           ____
          / o  \
         |  __  |  ?
          \____/
          /|  |\
         / |  | \
        """.trimIndent(),
        """
           ____
          / o  \
         |  __  | ??
          \____/
          /|  |\
         / |  | \
        """.trimIndent(),
        """
           ____
          /  o \
         |  __  |???
          \____/
          /|  |\
         / |  | \
        """.trimIndent(),
        """
           ____
          / o  \
         |  __  |  !
          \____/
          /|  |\
         / |  | \
        """.trimIndent(),
    )
    AgentState.TOOL_CALL -> listOf(
        """
         _______
        |  > _  |
        |  |_|  |
        |  ___  |
        | |   | |
        |_|   |_|
           ___
          |   |
        """.trimIndent(),
        """
         _______
        |  > _  |
        |  |/|  |
        |  ___  |
        | |   | |
        |_|   |_|
           ___
          |___|
        """.trimIndent(),
        """
         _______
        |  > _  |
        |  |\|  |
        |  ___  |
        | |   | |
        |_|   |_|
          _____
         |     |
        """.trimIndent(),
        """
         _______
        |  > _  |
        |  |_|  |
        |  ___  |
        | |   | |
        |_|   |_|
          _____
         |_____|
        """.trimIndent(),
    )
    AgentState.AWAITING_INPUT -> listOf(
        """
           /\
          /  \
         / !! \
        /______\

        [  YOUR  ]
        [  TURN  ]
        """.trimIndent(),
        """
           /\
          /  \
         / !! \
        /______\
           ||
        [  YOUR  ]
        [  TURN  ]
        """.trimIndent(),
        """
           /\
          /  \
         / !! \
        /______\

        [  YOUR  ]
        [  TURN  ]
        """.trimIndent(),
        """
           /\
          /  \
         / !! \
        /______\
           ||
        [  YOUR  ]
        [  TURN  ]
        """.trimIndent(),
    )
    AgentState.ERROR -> listOf(
        """
         _______
        |       |
        |  X  X |
        |   __  |
        |  /  \ |
        |_______| !
        """.trimIndent(),
        """
         _______
        |       |
        |  x  x |
        |   __  |
        |  /  \ |
        |_______| !!
        """.trimIndent(),
        """
         _______
        |       |
        |  X  X |
        |   __  |
        |  /  \ |
        |_______| !!!
        """.trimIndent(),
        """
         _______
        |       |
        |  x  x |
        |   __  |
        |  /  \ |
        |_______| !
        """.trimIndent(),
    )
    AgentState.COMPLETE -> listOf(
        """
            __
           / /
          / /
         / /
        /_/    ___
              /   /
             /___/
        """.trimIndent(),
        """
            __
           / /
          / /
         / /  .
        /_/    ___
         .    /   /
             /___/
        """.trimIndent(),
        """
            __
           / /  .
          / /
         / /    .
        /_/    ___
              /   / .
             /___/
        """.trimIndent(),
        """
            __   .
           / /
          / /  .
         / /
        /_/    ___  .
          .   /   /
             /___/
        """.trimIndent(),
    )
}

private fun stateColor(state: AgentState): Color = when (state) {
    AgentState.IDLE -> StatusDisabled
    AgentState.THINKING -> StatusStandby
    AgentState.TOOL_CALL -> StatusRunning
    AgentState.AWAITING_INPUT -> StatusWarning
    AgentState.ERROR -> StatusCritical
    AgentState.COMPLETE -> ClaudeAccent
}

private fun stateLabel(state: AgentState): String = when (state) {
    AgentState.IDLE -> "idle"
    AgentState.THINKING -> "thinking..."
    AgentState.TOOL_CALL -> "working"
    AgentState.AWAITING_INPUT -> "your turn"
    AgentState.ERROR -> "error"
    AgentState.COMPLETE -> "done"
}

private fun stateWeight(state: AgentState): Int = when (state) {
    AgentState.AWAITING_INPUT -> 5
    AgentState.ERROR -> 4
    AgentState.TOOL_CALL -> 3
    AgentState.THINKING -> 2
    AgentState.IDLE -> 1
    AgentState.COMPLETE -> 0
}
```

### Step 3: Build and verify

Run: `JAVA_HOME=... ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

### Step 4: Commit

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/SimpleStatusScreen.kt
git commit -m "feat(android): add Simple mode with 4-frame ASCII animations per state"
```

---

## Task 5: Settings — Add display mode toggle

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/SettingsScreen.kt`

### Step 1: Add mode preference

Add a "Display Mode" section to SettingsScreen with two options: Advanced (4-pane grid) and Simple (ASCII animations).

Store as `display_mode` in SharedPreferences (`"advanced"` or `"simple"`).

```kotlin
// In SettingsScreen, after Kiosk Mode toggle:
var displayMode by remember {
    mutableStateOf(prefs.getString("display_mode", "advanced") ?: "advanced")
}

Spacer(modifier = Modifier.height(24.dp))
Text("Display Mode", color = ClaudeTextLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
Spacer(modifier = Modifier.height(8.dp))

Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    listOf("advanced" to "Advanced", "simple" to "Simple").forEach { (value, label) ->
        FilterChip(
            selected = displayMode == value,
            onClick = {
                displayMode = value
                prefs.edit().putString("display_mode", value).apply()
            },
            label = { Text(label) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ClaudeAccent,
                selectedLabelColor = ClaudeBgDark,
            ),
        )
    }
}
Text(
    text = if (displayMode == "advanced") "4-pane terminal grid"
           else "Full-screen ASCII animations",
    color = ClaudeGray,
    fontSize = 12.sp,
)
```

### Step 2: Build and verify

Run: `JAVA_HOME=... ./gradlew assembleDebug 2>&1 | tail -5`

### Step 3: Commit

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/SettingsScreen.kt
git commit -m "feat(android): add Advanced/Simple display mode toggle in settings"
```

---

## Task 6: Wire display mode into Dashboard and DreamService

**Files:**
- Modify: `android/app/src/main/java/com/claudescreensaver/ui/screens/StatusDashboardScreen.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/MainActivity.kt`
- Modify: `android/app/src/main/java/com/claudescreensaver/ClaudeDreamService.kt`

### Step 1: StatusDashboardScreen — accept and use mode parameter

Add `displayMode: String = "advanced"` parameter to `StatusDashboardScreen`. When mode is `"simple"`, render `SimpleStatusScreen` instead of the 4-pane grid:

```kotlin
@Composable
fun StatusDashboardScreen(
    uiState: UiState,
    isPro: Boolean = false,
    displayMode: String = "advanced",
) {
    if (displayMode == "simple") {
        SimpleStatusScreen(uiState = uiState)
        return
    }
    // ... existing 4-pane grid code
}
```

### Step 2: MainActivity — read preference and pass to dashboard

Read `display_mode` from SharedPreferences and pass to `StatusDashboardScreen`:

```kotlin
val displayMode = prefs.getString("display_mode", "advanced") ?: "advanced"
// ...
StatusDashboardScreen(
    uiState = uiState,
    isPro = isPro,
    displayMode = displayMode,
)
```

### Step 3: ClaudeDreamService — read preference and pass

Same pattern — read from SharedPreferences in `onDreamingStarted()` and pass to the composable.

### Step 4: Build, install, test both modes

```bash
JAVA_HOME=... ./gradlew assembleDebug 2>&1 | tail -5
bash tools/adbtool.sh install && bash tools/adbtool.sh restart
```

Manually toggle between Advanced and Simple in settings, verify both render correctly.

### Step 5: Commit

```bash
git add android/app/src/main/java/com/claudescreensaver/ui/screens/StatusDashboardScreen.kt \
        android/app/src/main/java/com/claudescreensaver/MainActivity.kt \
        android/app/src/main/java/com/claudescreensaver/ClaudeDreamService.kt
git commit -m "feat(android): wire display mode toggle into dashboard and dream service"
```

---

## Task 7: Restart bridge and end-to-end test

### Step 1: Restart bridge server to pick up model changes

```bash
# Kill existing bridge
pkill -f claude-bridge || true
# Start fresh
cd /home/semmy/codeprojects/CCReStatus/bridge
uv run claude-bridge --no-mdns &
```

### Step 2: Install app and connect

```bash
bash tools/adbtool.sh install
bash tools/adbtool.sh seturl "http://192.168.3.201:4001/events"
```

### Step 3: Verify each feature

1. **Sub-agents**: Spawn a sub-agent (Agent tool) and check it appears in session card
2. **User input**: Type a message — verify it shows as "you: ..." in the card
3. **Interrupt**: Send `[Request interrupted by user]` — verify "INTERRUPTED" shows
4. **Simple mode**: Toggle to Simple in settings, verify ASCII animation renders
5. **Advanced mode**: Toggle back, verify 4-pane grid works as before

### Step 4: Screenshot both modes

```bash
bash tools/adbtool.sh screenshot /tmp/phone_advanced.png
# Toggle to simple via settings
bash tools/adbtool.sh screenshot /tmp/phone_simple.png
```

### Step 5: Final commit

```bash
git add -A
git commit -m "feat: screensaver modes and live data — v0.2.0"
```

---

## Architect Review (2026-03-15)

> Reviewed by GPT-5.4 via Codex CLI (Sanitize and Fire mode)

### Blocking Issues Resolved

1. **Global sub-agent tracking leaks across sessions** — Fixed: key `active_agents` by `session_id` in bridge server. Change `app["active_agents"]` from `dict[agent_id, SubAgent]` to `dict[session_id, dict[agent_id, SubAgent]]`. SubagentStart/Stop events include `session_id`, so scope is available. StatusUpdate.sub_agents populated from the session's own agent dict only.

2. **`interrupted` flag has no reset rule** — Fixed: any non-Stop event clears `interrupted=False` in `StatusUpdate.from_event()`. The flag is transient: set on `Stop` with `stop_hook_active=True`, cleared by the next `PreToolUse`, `UserPromptSubmit`, `SessionStart`, or any event that isn't `Stop`. Add test: `test_interrupted_cleared_by_subsequent_event`.

3. **`user_message` retention is unresolved** — Decision: `user_message` persists on the session until the next `PreToolUse` event (when Claude starts working). Cleared by setting `user_message=None` on PreToolUse/PostToolUse/SubagentStart. Rationale: the user's prompt is context for what Claude is about to do — once a tool fires, Claude has "read" the message and it's stale. Add test: `test_user_message_cleared_on_pretooluse`.

4. **`UserPromptSubmit` payload not pinned** — Fixed: wire contract is `{"hook_event_name": "UserPromptSubmit", "session_id": "<uuid>", "message": "<raw user text>"}`. The `message` field is always the raw prompt string, never formatted/wrapped. If `message` is absent or empty, `user_message` is set to `None`. Add guard: `user_message = event.message if event.message else None`.

### Accepted Risks

- **Priority logic duplication** (degrading): `stateWeight` is duplicated between SimpleStatusScreen and SseClient's `pickHottestSession`. Both are 6-line `when` expressions. Extracting to a shared util is clean but not worth blocking on — tracked for v0.3 refactor.

### Resolved Degrading Issues

- **Display mode not reactive** — Fixed: use `SharedPreferences.OnSharedPreferenceChangeListener` or Compose `mutableStateOf` with initial read from prefs. Mode changes apply immediately without app restart. Update Task 6 accordingly.

- **Simple mode burn-in** — Fixed: apply same Lissajous pixel shift (±6px, 60/90s periods) to SimpleStatusScreen container. Font stays at 14sp — readable on 5"–7" screens, scaling deferred to v0.3.

### Future Risks (Not Blocking)

- **ASCII art readability on tablets/foldables**: 14sp monospace may be too small on 10"+ screens. Would become blocking if targeting tablet form factor.
- **SSE reconnection floods**: if bridge restarts, all Android clients reconnect simultaneously and receive full snapshot. Not a problem at current scale (1-2 clients) but would need backoff jitter at 10+.
- **UserPromptSubmit hook availability**: depends on Claude Code runtime supporting this hook type. If not available in current version, the feature degrades gracefully (user_message stays null).

### Survivability Assessment

This plan survives contact with the codebase after addressing the four blocking issues. The highest-risk phase is Task 1 (bridge model changes) because both UI modes depend on correct state semantics — if interrupted doesn't clear or sub-agents leak across sessions, the UI lies. The mitigations (per-session agent tracking, explicit reset rules, pinned wire contract) bound the failure modes. Task 4 (Simple mode) is low-risk since it's purely additive UI with no state mutation. Watch the bridge model tests closely during implementation — they're the canary.
