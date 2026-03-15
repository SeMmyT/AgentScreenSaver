import pytest
from bridge.models import HookEvent, AgentState, StatusUpdate


def test_hook_event_from_pre_tool_use():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Bash",
        "tool_input": {"command": "npm test"},
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    assert event.event_name == "PreToolUse"
    assert event.tool_name == "Bash"
    assert event.session_id == "sess-123"


def test_hook_event_from_notification_permission():
    raw = {
        "hook_event_name": "Notification",
        "notification_type": "permission_prompt",
        "message": "Claude needs permission to use Bash",
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    assert event.event_name == "Notification"
    assert event.notification_type == "permission_prompt"


def test_hook_event_from_subagent_start():
    raw = {
        "hook_event_name": "SubagentStart",
        "agent_id": "agent-abc",
        "agent_type": "Explore",
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    assert event.agent_id == "agent-abc"
    assert event.agent_type == "Explore"


def test_status_update_from_pre_tool_use():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Edit",
        "tool_input": {"file_path": "/tmp/foo.py", "old_string": "a", "new_string": "b"},
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev-laptop")
    assert update.status == AgentState.TOOL_CALL
    assert update.tool == "Edit"
    assert update.requires_input is False
    assert update.instance_name == "dev-laptop"


def test_status_update_from_permission_notification():
    raw = {
        "hook_event_name": "Notification",
        "notification_type": "permission_prompt",
        "message": "Permission needed for Bash",
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev-laptop")
    assert update.status == AgentState.AWAITING_INPUT
    assert update.requires_input is True
    assert update.message == "Permission needed for Bash"


def test_status_update_from_stop():
    raw = {
        "hook_event_name": "Stop",
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev-laptop")
    assert update.status == AgentState.COMPLETE


def test_status_update_serializes_to_json():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Bash",
        "tool_input": {"command": "ls"},
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev-laptop")
    data = update.to_dict()
    assert data["v"] == 1
    assert data["status"] == "tool_call"
    assert data["session_id"] == "sess-123"
    assert "ts" in data


def test_status_update_truncates_long_tool_input():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Write",
        "tool_input": {"content": "x" * 500, "file_path": "/tmp/big.py"},
        "session_id": "sess-123",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev-laptop")
    assert len(update.tool_input_summary) <= 128


# --- Additional tests for coverage of all status derivation paths ---


def test_status_update_from_post_tool_use():
    raw = {"hook_event_name": "PostToolUse", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.THINKING


def test_status_update_from_post_tool_use_failure():
    raw = {"hook_event_name": "PostToolUseFailure", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.ERROR


def test_status_update_from_session_end():
    raw = {"hook_event_name": "SessionEnd", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.COMPLETE


def test_status_update_from_notification_other():
    raw = {
        "hook_event_name": "Notification",
        "notification_type": "info",
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.THINKING


def test_status_update_from_permission_request():
    raw = {"hook_event_name": "PermissionRequest", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.AWAITING_INPUT
    assert update.requires_input is True


def test_status_update_from_subagent_start():
    raw = {
        "hook_event_name": "SubagentStart",
        "agent_id": "a-1",
        "agent_type": "Explore",
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.THINKING


def test_status_update_from_subagent_stop():
    raw = {
        "hook_event_name": "SubagentStop",
        "agent_id": "a-1",
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.THINKING


def test_status_update_from_session_start():
    raw = {"hook_event_name": "SessionStart", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.IDLE


def test_status_update_from_task_completed():
    raw = {"hook_event_name": "TaskCompleted", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.COMPLETE


def test_status_update_from_teammate_idle():
    raw = {"hook_event_name": "TeammateIdle", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.IDLE


def test_status_update_from_unknown_event():
    raw = {"hook_event_name": "SomeNewEvent", "session_id": "sess-1"}
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.THINKING


def test_status_update_to_json_is_valid_json():
    import json

    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Bash",
        "tool_input": {"command": "echo hi"},
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    parsed = json.loads(update.to_json())
    assert parsed["v"] == 1
    assert parsed["status"] == "tool_call"


def test_notification_idle_prompt_is_awaiting_input():
    raw = {
        "hook_event_name": "Notification",
        "notification_type": "idle_prompt",
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.status == AgentState.AWAITING_INPUT
    assert update.requires_input is True


def test_summarize_tool_input_command():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Bash",
        "tool_input": {"command": "npm test"},
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.tool_input_summary == "npm test"


def test_summarize_tool_input_file_path():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Read",
        "tool_input": {"file_path": "/tmp/test.py"},
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert update.tool_input_summary == "/tmp/test.py"


def test_summarize_tool_input_fallback():
    raw = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Custom",
        "tool_input": {"some_key": "some_value"},
        "session_id": "sess-1",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="dev")
    assert "some_key" in update.tool_input_summary
    assert "some_value" in update.tool_input_summary
