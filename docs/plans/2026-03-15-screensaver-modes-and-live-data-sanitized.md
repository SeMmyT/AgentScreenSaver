# Screensaver Modes & Live Data — Architecture Plan

**System:** Android status display app + Python SSE bridge server. The app connects to a bridge server that relays agent lifecycle events via Server-Sent Events. Currently shows a 4-pane terminal-style grid (Advanced mode).

**Goal:** Add real-time user input display, sub-agent visibility, interrupt state, and a Simple ASCII animation mode alongside the existing Advanced 4-pane grid.

**Architecture:** Four concerns:
- (A) Bridge enrichment: forward user prompts, distinguish interrupts from completions
- (B) Android model updates: parse new SSE fields
- (C) Advanced mode polish: wire sub-agents, show interrupts and typed text in terminal cards
- (D) Simple mode: new full-screen ASCII animation composable toggled from settings

**Tech Stack:** Python aiohttp (bridge), Kotlin/Jetpack Compose (Android), SSE transport

---

## Current Data Flow

```
Agent hooks (HTTP POST) → Bridge server (Python aiohttp)
  → derives AgentState enum (idle/thinking/tool_call/awaiting_input/error/complete)
  → broadcasts StatusUpdate via SSE (/events endpoint)
  → Android app subscribes, renders in 4-pane grid
```

### Existing StatusUpdate fields:
- status (AgentState enum), session_id, instance_name, event, tool, tool_input_summary, message, requires_input, agent_id, agent_type, sub_agents (list), timestamp, version

### Existing hook events mapped:
- PreToolUse → tool_call
- PostToolUse → thinking
- PostToolUseFailure → error
- Stop/SessionEnd → complete
- Notification (permission_prompt/idle_prompt) → awaiting_input
- PermissionRequest → awaiting_input
- SubagentStart/SubagentStop → thinking
- SessionStart → idle
- TaskCompleted → complete

---

## Task 1: Bridge — Add UserPromptSubmit hook and interrupt distinction

Add new hook event type `UserPromptSubmit` (fires when user submits text).

**Model changes:**
- HookEvent: add `user_message` field (parsed from `message` when event is UserPromptSubmit)
- StatusUpdate: add `user_message` (str|None) and `interrupted` (bool)
- Map UserPromptSubmit → thinking state + set user_message
- Map Stop with stop_hook_active=True → awaiting_input + interrupted=True
- Map Stop with stop_hook_active=False → complete (unchanged)
- Serialize new fields in to_dict/to_json

**Tests:** 3 new test cases for UserPromptSubmit mapping, Stop-with-active, Stop-without-active.

## Task 2: Android — Parse new fields

**Data class changes:**
- AgentStatus: add `userMessage: String?`, `interrupted: Boolean`, `subAgents: List<SubAgentInfo>`
- New data class SubAgentInfo(agentId, agentType, status)
- Update JSON parser to extract new fields from SSE payloads
- Update demo data provider to include sample sub-agents

## Task 3: Advanced Mode — SessionCard enhancements

**UI changes to terminal-style session card:**
- Title bar: add sub-agent count badge ("N agents") in cyan
- Content: show user input as "you: <message>" in accent color (max 2 lines)
- Content: show pulsing ">>> INTERRUPTED <<<" in warning color when interrupted=true
- Bottom: compact sub-agent list showing type and running/stopped status with color coding

## Task 4: Simple Mode — Full-screen ASCII animation composable

**New composable:** Picks "hottest" session (highest priority state), displays a cycling 4-frame ASCII art animation matching the current state.

**Layout:** Centered on screen — ASCII art, state label, context line (user message or tool name), sub-agent count.

**Animation:** 600ms per frame, resets frame index on state change.

**6 state animations:** idle (twinkling stars), thinking (face with ? → !), tool_call (terminal with cursor), awaiting_input (alert triangle + "YOUR TURN"), error (X eyes face), complete (checkmark with sparkles).

**Helper functions:** stateColor, stateLabel, stateWeight (priority ordering for hottest session selection — same logic as existing SseClient).

## Task 5: Settings — Display mode toggle

Add "Display Mode" section to settings screen after existing Kiosk Mode toggle.

**UI:** Two FilterChips — "Advanced" and "Simple". Persisted to SharedPreferences as `display_mode` string.

**Description text** updates based on selection: "4-pane terminal grid" or "Full-screen ASCII animations".

## Task 6: Wire display mode into Dashboard and DreamService

**StatusDashboardScreen:** Accept `displayMode` parameter. When "simple", render SimpleStatusScreen and return early. Otherwise render existing 4-pane grid.

**MainActivity + DreamService:** Read display_mode from SharedPreferences, pass to dashboard composable.

## Task 7: End-to-end verification

Restart bridge, install app, verify:
1. Sub-agents appear in session cards when spawned
2. User input text shows in terminal pane
3. Interrupt state shows prominently
4. Simple mode renders ASCII animations
5. Mode toggle persists across app restarts

---

## Open Questions

1. Should user_message persist across subsequent events or get cleared on next tool use?
2. Should Simple mode show burn-in prevention (Lissajous shift) like Advanced mode?
3. Should the ASCII art scale with screen DPI/size or stay fixed at 14sp?
4. Sub-agents are tracked globally in bridge, not per-session — is this correct for multi-session scenarios?
