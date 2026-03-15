"""Event models for Claude Code hook events and derived status updates."""

from __future__ import annotations

import json
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from enum import StrEnum
from typing import Any


@dataclass
class SubAgent:
    """A sub-agent spawned by the main Claude Code session."""

    agent_id: str
    agent_type: str
    status: str  # "running" or "completed"
    name: str = ""  # human-readable description from Agent tool call

    def to_dict(self) -> dict[str, Any]:
        return {
            "agent_id": self.agent_id,
            "agent_type": self.agent_type,
            "status": self.status,
            "name": self.name,
        }


class AgentState(StrEnum):
    IDLE = "idle"
    THINKING = "thinking"
    TOOL_CALL = "tool_call"
    AWAITING_INPUT = "awaiting_input"
    ERROR = "error"
    COMPLETE = "complete"


@dataclass
class HookEvent:
    event_name: str
    session_id: str
    tool_name: str | None = None
    tool_input: dict[str, Any] | None = None
    notification_type: str | None = None
    message: str | None = None
    agent_id: str | None = None
    agent_type: str | None = None
    stop_hook_active: bool = False
    last_assistant_message: str | None = None
    custom_frames: list[str] | None = None
    custom_label: str | None = None

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> HookEvent:
        return cls(
            event_name=data.get("hook_event_name", ""),
            session_id=data.get("session_id", ""),
            tool_name=data.get("tool_name"),
            tool_input=data.get("tool_input"),
            notification_type=data.get("notification_type"),
            message=data.get("message"),
            agent_id=data.get("agent_id"),
            agent_type=data.get("agent_type"),
            stop_hook_active=data.get("stop_hook_active", False),
            last_assistant_message=data.get("last_assistant_message"),
            custom_frames=data.get("custom_frames"),
            custom_label=data.get("custom_label"),
        )


@dataclass
class StatusUpdate:
    status: AgentState
    session_id: str
    instance_name: str
    event: str
    tool: str | None = None
    tool_input_summary: str = ""
    message: str = ""
    requires_input: bool = False
    agent_id: str | None = None
    agent_type: str | None = None
    user_message: str | None = None
    interrupted: bool = False
    custom_frames: list[str] | None = None
    custom_label: str | None = None
    sub_agents: list[SubAgent] = field(default_factory=list)
    ts: str = field(default_factory=lambda: datetime.now(timezone.utc).isoformat())

    @classmethod
    def from_event(cls, event: HookEvent, instance_name: str) -> StatusUpdate:
        status = _derive_status(event)
        requires_input = status == AgentState.AWAITING_INPUT
        tool_summary = _summarize_tool_input(event.tool_input) if event.tool_input else ""

        # Prefer last_assistant_message for richer context
        message = event.last_assistant_message or event.message or ""
        if len(message) > 200:
            message = message[:200]

        # user_message only for UserPromptSubmit (non-empty)
        user_message: str | None = None
        if event.event_name == "UserPromptSubmit" and event.message:
            user_message = event.message

        interrupted = (
            event.event_name == "Stop" and event.stop_hook_active is True
        )

        return cls(
            status=status,
            session_id=event.session_id,
            instance_name=instance_name,
            event=event.event_name,
            tool=event.tool_name,
            tool_input_summary=tool_summary,
            message=message,
            requires_input=requires_input,
            agent_id=event.agent_id,
            agent_type=event.agent_type,
            user_message=user_message,
            interrupted=interrupted,
            custom_frames=event.custom_frames,
            custom_label=event.custom_label,
        )

    def to_dict(self) -> dict[str, Any]:
        d = asdict(self)
        d["v"] = 1
        d["status"] = str(self.status)
        d["sub_agents"] = [sa.to_dict() for sa in self.sub_agents]
        return d

    def to_json(self) -> str:
        return json.dumps(self.to_dict())


def _derive_status(event: HookEvent) -> AgentState:
    match event.event_name:
        case "PreToolUse":
            return AgentState.TOOL_CALL
        case "PostToolUse":
            return AgentState.THINKING
        case "PostToolUseFailure":
            return AgentState.ERROR
        case "Stop":
            if event.stop_hook_active:
                return AgentState.AWAITING_INPUT
            return AgentState.COMPLETE
        case "SessionEnd":
            return AgentState.COMPLETE
        case "UserPromptSubmit":
            return AgentState.THINKING
        case "Notification":
            if event.notification_type in ("permission_prompt", "idle_prompt"):
                return AgentState.AWAITING_INPUT
            return AgentState.THINKING
        case "PermissionRequest":
            return AgentState.AWAITING_INPUT
        case "SubagentStart" | "SubagentStop":
            return AgentState.THINKING
        case "SessionStart":
            return AgentState.IDLE
        case "TaskCompleted":
            return AgentState.COMPLETE
        case "TeammateIdle":
            return AgentState.IDLE
        case _:
            return AgentState.THINKING


def _summarize_tool_input(tool_input: dict[str, Any]) -> str:
    if "command" in tool_input:
        summary = tool_input["command"]
    elif "file_path" in tool_input:
        summary = tool_input["file_path"]
    else:
        summary = str(tool_input)
    return summary[:128]
