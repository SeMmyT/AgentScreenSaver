**[INTEGRATION] Global sub-agent tracking will leak across sessions**
- **Failure mode:** In a real multi-session run, a sub-agent spawned under one `session_id` can appear in another session’s card or influence Simple mode’s “hottest” display because the plan only says sub-agents are tracked globally.
- **Where in plan:** `Open Questions` #4, plus `Task 3: Advanced Mode — SessionCard enhancements` bullet `Bottom: compact sub-agent list...`
- **Severity:** blocking
- **Suggested resolution:** Define whether sub-agents are keyed by `session_id` and validate that rule before exposing them in per-session UI.

**[INVARIANT] `interrupted` has no reset rule**
- **Failure mode:** Once a `Stop` event sets `interrupted=true`, later `thinking` or `tool_call` updates can keep showing `>>> INTERRUPTED <<<` forever because no transition clears the flag.
- **Where in plan:** `Task 1: Bridge — Add UserPromptSubmit hook and interrupt distinction` bullets `StatusUpdate: add ... interrupted (bool)` and `Map Stop with stop_hook_active=True → awaiting_input + interrupted=True`
- **Severity:** blocking
- **Suggested resolution:** Specify the exact next event or state transition that must force `interrupted=false`.

**[FALSIFIABILITY] `user_message` retention is unresolved, so success is untestable**
- **Failure mode:** One implementation may clear the typed text on the next event while another keeps stale text for minutes, and both would appear to satisfy the plan because the retention rule is explicitly undecided.
- **Where in plan:** `Open Questions` #1, `Task 3` bullet `Content: show user input as "you: <message>"`, and `Task 4` bullet `context line (user message or tool name)`
- **Severity:** blocking
- **Suggested resolution:** Decide and document how long `user_message` survives across subsequent events.

**[INTERFACE] `UserPromptSubmit` payload semantics are not pinned down**
- **Failure mode:** If the hook’s `message` field contains wrapper text, formatted content, or is absent for some emitters, the bridge will parse the wrong string into `user_message` and the UI will display misleading `you:` content.
- **Where in plan:** `Task 1` bullet `HookEvent: add user_message field (parsed from message when event is UserPromptSubmit)`
- **Severity:** blocking
- **Suggested resolution:** Freeze the `UserPromptSubmit` wire contract with a concrete payload example and required field meanings.

**[COUPLING] Priority logic is duplicated between Simple mode and existing client code**
- **Failure mode:** A future change to session priority in the existing `SseClient` can leave Simple mode choosing a different “hottest” session than the rest of the app because `stateWeight` is redefined separately.
- **Where in plan:** `Task 4: Simple Mode — Full-screen ASCII animation composable` bullet `stateWeight (priority ordering ... same logic as existing SseClient)`
- **Severity:** degrading
- **Suggested resolution:** Name one source of truth for state priority and make both code paths depend on it.

**[TEMPORAL] Display mode changes are only specified at read time**
- **Failure mode:** A user switches from Advanced to Simple in Settings while `MainActivity` or `DreamService` is already active, but the screen never updates until restart because the plan only says to read from `SharedPreferences` and pass the value down.
- **Where in plan:** `Task 5: Settings — Display mode toggle` bullet `Persisted to SharedPreferences as display_mode string` and `Task 6: Wire display mode into Dashboard and DreamService`
- **Severity:** degrading
- **Suggested resolution:** State whether running screens must observe preference changes live or only after an explicit restart.

**[RESOURCE] Long-running screensaver constraints are left open**
- **Failure mode:** On kiosk or DreamService hardware, a centered fixed-size ASCII screen can cause burn-in or become unreadable on very small/large displays because burn-in shift and text scaling are still unresolved.
- **Where in plan:** `Task 4` bullets `Layout: Centered on screen` and `Animation: 600ms per frame`, plus `Open Questions` #2 and #3
- **Severity:** degrading
- **Suggested resolution:** Lock down the burn-in mitigation and scaling policy before implementation so long-run behavior is bounded.

### Survivability Assessment
- **Total critiques:** 7 (4 blocking, 3 degrading, 0 cosmetic)
- **Highest-risk area:** `Task 1: Bridge — Add UserPromptSubmit hook and interrupt distinction` because it defines new state semantics that both UI modes depend on, but the payload contract and flag lifecycle are still ambiguous.
- **Top 3 risks** that would make you nervous during implementation
  - Cross-session sub-agent contamination from global tracking
  - Stale `interrupted` state that never clears after recovery
  - Undefined `user_message` retention causing inconsistent UI and unverifiable tests
- **Verdict:** This plan is close to implementable, but it does not yet survive contact with a real codebase because the event/state contract is still under-specified in the exact places the UI will trust most. The first thing likely to break is state correctness: either the interrupt banner sticks after recovery, or user/sub-agent data shows up on the wrong session because lifecycle and ownership rules are not yet bounded.