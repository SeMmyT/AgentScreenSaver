**[INTERFACE] Wire schema is not actually specified**
- **Failure mode:** The Android client silently misparses or drops updates when the bridge emits snake_case fields like `session_id` / `instance_name` and `status`, while `AgentStatus.fromJson` is described with camelCase fields and `state`, and the `v: 1` version field has no consumer behavior.
- **Where in plan:** `Task 2: Event Models — StatusUpdate dataclass` and `Task 7: Data Models (Android) — AgentStatus data class`
- **Severity:** blocking
- **Suggested resolution:** Define one explicit wire JSON contract with exact field names, types, optionality, and version-handling rules, and make both sides implement that contract.

**[INTEGRATION] The DreamService points at the wrong machine**
- **Failure mode:** On a real phone, the screensaver never connects because `http://localhost:4001` resolves to the phone itself, not the bridge server running on the workstation or another LAN host.
- **Where in plan:** `Task 14: DreamService + MainActivity — Connects to server URL in onDreamingStarted — currently hardcoded to localhost:4001`
- **Severity:** blocking
- **Suggested resolution:** Make a non-`localhost` endpoint selection path a prerequisite for any device run, even if it is temporary and manual.

**[INVARIANT] A single global status cannot represent interleaved agents**
- **Failure mode:** As soon as two sessions or subagents emit events near each other, one agent’s `Stop`/`SessionEnd` can overwrite another agent’s active work, causing the display to show `COMPLETE` while something is still running.
- **Where in plan:** `Task 3: SSE Server — State: app["last_status"] stores the most recent StatusUpdate` and `Task 2: Event Models — Fields include session_id, agent_id, agent_type`
- **Severity:** blocking
- **Suggested resolution:** State the concurrency invariant explicitly and either reject unsupported overlaps or partition state by session/agent before deriving the displayed status.

**[FALSIFIABILITY] The core transport path is never proven end to end**
- **Failure mode:** The project can “pass tests” while `/events` is incorrectly framed, never flushes, or produces payloads the Android `SseClient` cannot parse, so the app stays disconnected or stale in actual use.
- **Where in plan:** `Task 3: SSE Server — IMPORTANT NOTE` and `Task 8: SSE Client — Tests: 2 tests — initial state assertions only`
- **Severity:** blocking
- **Suggested resolution:** Add one automated bridge-to-client SSE test that posts an event to `/event` and verifies a client receives and parses the corresponding `/events` message.

**[TEMPORAL] Queue overflow turns live clients into silent zombies**
- **Failure mode:** During event bursts, a slow but still-connected SSE consumer can fill its `Queue(maxsize=50)`, get dropped as “dead,” and keep rendering old state because neither the server nor client makes the disconnect explicit.
- **Where in plan:** `Task 3: SSE Server — Fan-out mechanism: Each SSE client gets an asyncio.Queue(maxsize=50)... Full queues are dropped`
- **Severity:** degrading
- **Suggested resolution:** Define an explicit stale/disconnected transition when a client queue is dropped so old status is not presented as live data.

**[COUPLING] Hook configuration and derivation logic are out of sync**
- **Failure mode:** `TaskCompleted` and `TeammateIdle` are configured to POST into the bridge, but they are absent from the derivation table and therefore fall into `default → THINKING`, misreporting idle/completed work as active work.
- **Where in plan:** `Task 5: Claude Code Hooks Configuration — 11 event types` and `Task 2: Event Models — Status derivation logic`
- **Severity:** degrading
- **Suggested resolution:** Enumerate handling for every configured hook event type, even if several intentionally map to the same display state.

**[RESOURCE] mDNS discovery can advertise an unusable endpoint**
- **Failure mode:** On IPv6-only, firewalled, or offline LANs, `_get_local_ip()` can choose the wrong interface or fall back to `127.0.0.1`, so the phone discovers a service name that resolves to an unreachable bridge.
- **Where in plan:** `Task 4: mDNS Announcement — _get_local_ip() connects UDP socket to 8.8.8.8:80... falls back to 127.0.0.1` and `Task 15: Bridge Discovery (mDNS/NSD)`
- **Severity:** blocking
- **Suggested resolution:** Validate that the advertised host address is reachable from peers on the target network before treating discovery as usable.

### Survivability Assessment
- **Total critiques:** 7 (5 blocking, 2 degrading, 0 cosmetic)
- **Highest-risk area:** `Task 14: DreamService + MainActivity`, because the runtime connection path to a real device is currently wrong by default and depends on discovery/config behavior that is either deferred or fragile.
- **Top 3 risks** that would make you nervous during implementation
- The phone never reaches the bridge because `localhost` and mDNS resolution both produce unusable endpoints.
- The bridge and Android app disagree on the event JSON shape, and nothing automated catches it.
- Interleaved session/subagent events corrupt the displayed state because only one global `last_status` exists.
- **Verdict:** This plan does not yet survive contact with a real codebase. The first thing that breaks is real-device connectivity: the screensaver will try to connect to itself or to a bad discovered address and show no live data. If that gets patched manually, the next break is the unpinned bridge↔Android contract, followed by incorrect status under concurrent agent activity.