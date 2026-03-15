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


def test_user_prompt_submit_empty_message():
    raw = {
        "hook_event_name": "UserPromptSubmit",
        "session_id": "s1",
        "message": "",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.status == AgentState.THINKING
    assert update.user_message is None


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
    assert update.requires_input is True


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


def test_interrupted_cleared_by_subsequent_event():
    """After an interrupt, the next non-Stop event clears interrupted."""
    stop_raw = {
        "hook_event_name": "Stop",
        "session_id": "s1",
        "stop_hook_active": True,
    }
    event = HookEvent.from_dict(stop_raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.interrupted is True

    # Next event should not be interrupted
    next_raw = {
        "hook_event_name": "PreToolUse",
        "session_id": "s1",
        "tool_name": "Bash",
        "tool_input": {"command": "ls"},
    }
    event2 = HookEvent.from_dict(next_raw)
    update2 = StatusUpdate.from_event(event2, instance_name="test")
    assert update2.interrupted is False
    assert update2.user_message is None


def test_user_message_cleared_on_pretooluse():
    """user_message only set for UserPromptSubmit, not other events."""
    prompt_raw = {
        "hook_event_name": "UserPromptSubmit",
        "session_id": "s1",
        "message": "fix the bug",
    }
    event = HookEvent.from_dict(prompt_raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.user_message == "fix the bug"

    # Next PreToolUse should not carry user_message
    tool_raw = {
        "hook_event_name": "PreToolUse",
        "session_id": "s1",
        "tool_name": "Edit",
        "tool_input": {"file_path": "/tmp/x.py"},
    }
    event2 = HookEvent.from_dict(tool_raw)
    update2 = StatusUpdate.from_event(event2, instance_name="test")
    assert update2.user_message is None


def test_serialization_includes_new_fields():
    raw = {
        "hook_event_name": "UserPromptSubmit",
        "session_id": "s1",
        "message": "hello",
    }
    event = HookEvent.from_dict(raw)
    update = StatusUpdate.from_event(event, instance_name="test")
    d = update.to_dict()
    assert "user_message" in d
    assert d["user_message"] == "hello"
    assert "interrupted" in d
    assert d["interrupted"] is False

    import json
    parsed = json.loads(update.to_json())
    assert parsed["user_message"] == "hello"
    assert parsed["interrupted"] is False


def test_custom_frames_passed_through():
    raw = {
        "hook_event_name": "PreToolUse",
        "session_id": "s1",
        "tool_name": "Bash",
        "tool_input": {"command": "ls"},
        "custom_frames": ["frame1", "frame2", "frame3", "frame4"],
        "custom_label": "Discombobulating...",
    }
    event = HookEvent.from_dict(raw)
    assert event.custom_frames == ["frame1", "frame2", "frame3", "frame4"]
    assert event.custom_label == "Discombobulating..."

    update = StatusUpdate.from_event(event, instance_name="test")
    assert update.custom_frames == ["frame1", "frame2", "frame3", "frame4"]
    assert update.custom_label == "Discombobulating..."

    d = update.to_dict()
    assert d["custom_frames"] == ["frame1", "frame2", "frame3", "frame4"]
    assert d["custom_label"] == "Discombobulating..."
