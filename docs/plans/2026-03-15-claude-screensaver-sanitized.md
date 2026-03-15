# Claude ScreenSaver Implementation Plan (Sanitized — Architecture Only)

**Goal:** Build an Android app that displays real-time Claude Code agent status as a screensaver on a charging stand, connecting via a lightweight bridge server.

**Architecture:** Three components — (1) Claude Code HTTP hooks POST lifecycle events to a Python bridge server, (2) the bridge server derives status and fans out via SSE, (3) an Android app (DreamService + Activity) renders a minimalistic Compose UI with a mascot and status indicators. The phone auto-launches the screensaver when placed on a charging stand via Android's native Screen Saver system.

**Tech Stack:** Kotlin + Jetpack Compose (Android), Python + aiohttp + aiohttp-sse (bridge server), Claude Code hooks (settings.json), mDNS/NSD for discovery, OkHttp + okhttp-eventsource (Android SSE client)

---

## Project Structure

```
project/
├── bridge/                          # Python bridge server
│   ├── pyproject.toml
│   ├── bridge/__init__.py
│   ├── bridge/server.py             # aiohttp SSE server
│   ├── bridge/models.py             # Event dataclasses
│   ├── bridge/mdns.py               # mDNS announcement
│   └── tests/
│       ├── test_server.py
│       └── test_models.py
├── android/                         # Android app
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/app/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── ClaudeDreamService.kt
│   │   │   │   ├── DreamServiceCompat.kt
│   │   │   │   ├── ui/
│   │   │   │   │   ├── theme/ (Color.kt, Theme.kt, Type.kt)
│   │   │   │   │   ├── screens/StatusDashboardScreen.kt
│   │   │   │   │   └── components/ (ClawdMascot, StatusIndicator, SubAgentList, ConnectionBadge)
│   │   │   │   ├── data/
│   │   │   │   │   ├── models/AgentStatus.kt
│   │   │   │   │   ├── network/ (SseClient.kt, BridgeDiscovery.kt)
│   │   │   │   │   └── repository/StatusRepository.kt
│   │   │   │   └── viewmodel/StatusViewModel.kt
│   │   │   └── AndroidManifest.xml
│   │   └── src/test/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── hooks/
    └── settings.json.example        # Example hooks config for users
```

---

## Phase 1: Bridge Server (Python)

### Task 1: Bootstrap project
- pyproject.toml with aiohttp, aiohttp-sse, zeroconf dependencies
- Uses hatchling build backend, uv for package management

### Task 2: Event Models

Data model layer that parses Claude Code hook JSON payloads and derives display status.

**HookEvent dataclass** — maps from raw hook JSON:
- Fields: event_name, session_id, tool_name, tool_input, notification_type, message, agent_id, agent_type
- Factory: `from_dict(data)` parses the raw JSON from Claude Code HTTP hooks

**AgentState enum:** idle, thinking, tool_call, awaiting_input, error, complete

**StatusUpdate dataclass** — derived from HookEvent for the phone display:
- Fields: status (AgentState), session_id, instance_name, event, tool, tool_input_summary (truncated to 128 chars), message, requires_input, agent_id, agent_type, ts
- Factory: `from_event(event, instance_name)` applies status derivation logic
- Serializes to JSON with version field `v: 1`

**Status derivation logic:**
```
PreToolUse → TOOL_CALL
PostToolUse → THINKING
PostToolUseFailure → ERROR
Stop | SessionEnd → COMPLETE
Notification(permission_prompt|idle_prompt) → AWAITING_INPUT
Notification(other) → THINKING
PermissionRequest → AWAITING_INPUT
SubagentStart | SubagentStop → THINKING
SessionStart → IDLE
default → THINKING
```

Tests: 8 unit tests covering each event type, JSON serialization, and truncation.

### Task 3: SSE Server

**Endpoints:**
- `GET /health` → `{"status": "ok", "instance_name": "..."}`
- `POST /event` → receives Claude Code hook JSON, derives StatusUpdate, fans out to SSE clients. Returns 202. Returns 400 on invalid JSON.
- `GET /events` → SSE stream. On connect, sends current last_status immediately. Then streams new events as they arrive.
- `GET /status` → JSON snapshot of last status (polling fallback)

**Fan-out mechanism:** Each SSE client gets an `asyncio.Queue(maxsize=50)`. On POST /event, the server iterates all queues and puts the JSON. Full queues are dropped (dead clients).

**State:** `app["last_status"]` stores the most recent StatusUpdate. `app["sse_clients"]` is a set of queues.

**Server entry point:** argparse with --port (default 4001), --host (default 0.0.0.0), --name.

Tests: 4 tests — health check, POST 202, bad JSON 400, POST→status round-trip.

**IMPORTANT NOTE:** The test named `test_sse_stream_receives_posted_event` actually tests the `/status` polling endpoint, NOT the `/events` SSE endpoint. The SSE endpoint itself is never integration-tested.

### Task 4: mDNS Announcement

- `register_service(port, instance_name)` → creates `_ccrestatus._tcp.local.` service via zeroconf
- `unregister_service(zc, info)` → cleanup
- `_get_local_ip()` → connects UDP socket to 8.8.8.8:80 to determine local IP, falls back to 127.0.0.1
- Wired into aiohttp `on_startup` / `on_shutdown` lifecycle hooks
- mDNS failure is non-fatal (logged warning, server continues)

Tests: 2 tests with mocked Zeroconf.

### Task 5: Claude Code Hooks Configuration

Example `settings.json` configuring HTTP hooks for 11 event types (PreToolUse, PostToolUse, SubagentStart, SubagentStop, Notification, PermissionRequest, Stop, SessionStart, SessionEnd, TaskCompleted, TeammateIdle), all pointing to `http://localhost:4001/event` with 5-second timeout.

---

## Phase 2: Android App

### Task 6: Scaffold Android Project

Manual project creation (no Android Studio):
- Gradle 8.12 with Kotlin 2.1.10, AGP 8.8.2
- Compose BOM 2025.03.00
- Min SDK 26, target SDK 35
- Dependencies: core-ktx, lifecycle, activity-compose, Compose (ui, material3), okhttp, okhttp-eventsource, coroutines
- Test deps: junit, mockk, turbine, coroutines-test
- AndroidManifest declares:
  - MainActivity (launcher)
  - ClaudeDreamService (BIND_DREAM_SERVICE permission, DreamService intent filter, dream_info meta-data)
- Resource files: strings.xml, colors.xml (brand palette), themes.xml (Material dark), dream_info.xml

### Task 7: Data Models (Android)

**AgentState enum** — mirrors bridge server's states
**AgentStatus data class** — parsed from SSE JSON via `org.json.JSONObject`:
- Fields: state, sessionId, instanceName, event, tool, toolInputSummary, message, requiresInput, agentId, agentType, timestamp
- Companion: `fromJson(json)`, `DISCONNECTED` sentinel

Tests: 3 tests — tool_call parsing, awaiting_input parsing, unknown status defaults to THINKING.

### Task 8: SSE Client

**SseClient class** wrapping `okhttp-eventsource`:
- Exposes `StateFlow<AgentStatus>` (initialized to DISCONNECTED)
- Exposes `StateFlow<ConnectionState>` (DISCONNECTED, CONNECTING, CONNECTED, ERROR)
- `connect(url)` creates EventSource with 2s reconnect time
- `disconnect()` closes EventSource, resets flows
- EventHandler: `onOpen` → CONNECTED, `onMessage` → parse JSON to AgentStatus, `onError` → ERROR, `onClosed` → DISCONNECTED

Tests: 2 tests — initial state assertions only. No integration test with actual SSE connection.

### Task 9: ViewModel

**StatusViewModel:**
- Takes SseClient (constructor injection for testability)
- Combines `sseClient.status` and `sseClient.connectionState` into `StateFlow<UiState>` via `combine().stateIn()`
- `connect(url)` and `disconnect()` delegate to SseClient
- `onCleared()` disconnects

**UiState data class:** agentStatus + connectionState

Tests: 2 tests with mockk — initial state, connect delegation.

### Task 10: Theme & Colors (Compose)

Brand palette:
- Background: #141413 (warm near-black, NOT pure black)
- Accent: #D97757 (terra cotta orange)
- Deep accent: #BD5D3A
- Text: #FAF9F5
- Gray: #B0AEA5

Status colors (aerospace-grade Astro UX):
- Running: #56F000, Standby: #2DCCFF, Caution: #FCE83A, Warning: #FFB302, Critical: #FF3838, Disabled: #A4ABB6

Material3 darkColorScheme. Typography with displayLarge (57sp), headlineMedium (28sp), titleMedium (16sp), bodyLarge (16sp), labelSmall (11sp).

### Task 11: StatusIndicator Component

Canvas-based breathing glow orb:
- Color animated via `animateColorAsState(spring)` based on AgentState
- Glow alpha/radius via `rememberInfiniteTransition.animateFloat`
- 3 concentric circles: outer glow (30% alpha), mid glow (50% alpha), core dot
- Awaiting input: faster pulse (800ms), others: slow breathe (2000ms)

### Task 12: Clawd Mascot Component

Pixel-art crab rendered via Canvas:
- 10x8 grid of filled rectangles
- Animation states driven by AgentState: breathing scale (idle/complete), wobble (thinking), bounce (awaiting input)
- Tint color defaults to brand accent

### Task 13: Status Dashboard Screen

Main display composable:
- **Burn-in prevention:** Lissajous pixel shift via `rememberInfiniteTransition` — offsetX (60s period), offsetY (90s period), ±6px range. Different periods prevent path burn-in.
- Layout (vertical, centered): Clawd mascot → StatusIndicator orb → status label (displayLarge) → tool/task summary (headlineMedium, gray) → input-required message (titleMedium, accent) → ConnectionBadge
- ConnectionBadge: colored dot + "instanceName — status" label
- SubAgentList: placeholder component showing agent type + status pairs

### Task 14: DreamService + MainActivity

**DreamServiceCompat (abstract):**
- Extends DreamService, implements LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner
- Creates ComposeView with proper lifecycle wiring (setViewTreeLifecycleOwner, etc.)
- Lifecycle transitions: onCreate→CREATED, onDreamingStarted→STARTED, onDreamingStopped→CREATED, onDestroy→DESTROYED

**ClaudeDreamService:**
- isInteractive = false (touch exits dream)
- isFullscreen = true
- isScreenBright = false (dim for AMOLED protection)
- Creates StatusViewModel + SseClient in onAttachedToWindow
- Connects to server URL in onDreamingStarted — **currently hardcoded to localhost:4001**
- Disconnects in onDreamingStopped
- Renders ClaudeScreenSaverTheme { StatusDashboardScreen }

**MainActivity:**
- FLAG_KEEP_SCREEN_ON
- Same Compose UI as DreamService (shared StatusDashboardScreen composable)
- ViewModel via `viewModel()` — no server URL configuration UI yet

### Task 15: Bridge Discovery (mDNS/NSD)

**BridgeDiscovery class:**
- Uses Android NsdManager to discover `_ccrestatus._tcp.` services
- Exposes `StateFlow<List<BridgeServer>>` (name, host, port, sseUrl)
- DiscoveryListener: onServiceFound → resolveService, onServiceLost → remove from list
- ResolveListener: onServiceResolved → add BridgeServer to list
- startDiscovery() / stopDiscovery() lifecycle

### Task 16: End-to-End Integration Test (manual)

1. Start bridge: `uv run claude-bridge --port 4001`
2. POST test event via curl
3. Verify SSE stream via curl
4. Build APK: `./gradlew assembleDebug`
5. Install on phone, verify connection
6. Activate Screen Saver in Android Settings

---

## Phase 3: Polish & Configuration (deferred)

### Task 17: Settings Screen
- TextField for server URL
- SharedPreferences persistence
- mDNS server list (tap to select)
- Connection status indicator

### Task 18: Sub-Agent Tracking State
- Maintain active_agents dict in bridge (SubagentStart/Stop)
- Include sub_agents array in StatusUpdate JSON
- Parse and display in Android SubAgentList component

---

## Summary

| Phase | Tasks | Description |
|-------|-------|-------------|
| 1 | 1-5 | Bridge server: models, SSE server, mDNS, hooks config |
| 2 | 6-16 | Android app: scaffold, models, SSE client, ViewModel, theme, UI, DreamService, E2E test |
| 3 | 17-18 | Polish: settings screen, sub-agent tracking |

Total: 18 tasks, ~40 atomic commits
