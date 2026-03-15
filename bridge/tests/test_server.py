"""Tests for the SSE bridge server."""

from __future__ import annotations

import asyncio
import json

import pytest
from aiohttp import web
from aiohttp.test_utils import AioHTTPTestCase, TestClient

from bridge.server import create_app


SAMPLE_HOOK_EVENT = {
    "hook_event_name": "PreToolUse",
    "tool_name": "Bash",
    "tool_input": {"command": "npm test"},
    "session_id": "sess-abc",
}


@pytest.fixture
def app() -> web.Application:
    return create_app(instance_name="test-instance")


@pytest.fixture
async def client(aiohttp_client, app: web.Application) -> TestClient:
    return await aiohttp_client(app)


@pytest.mark.asyncio
async def test_health_check(client: TestClient) -> None:
    """GET /health returns 200 with status=ok and instance_name."""
    resp = await client.get("/health")
    assert resp.status == 200
    data = await resp.json()
    assert data["status"] == "ok"
    assert data["instance_name"] == "test-instance"


@pytest.mark.asyncio
async def test_post_event_returns_202(client: TestClient) -> None:
    """POST /event with valid hook JSON returns 202."""
    resp = await client.post("/event", json=SAMPLE_HOOK_EVENT)
    assert resp.status == 202
    data = await resp.json()
    assert data["accepted"] is True


@pytest.mark.asyncio
async def test_post_event_bad_json_returns_400(client: TestClient) -> None:
    """POST /event with invalid JSON returns 400."""
    resp = await client.post(
        "/event",
        data=b"not valid json",
        headers={"Content-Type": "application/json"},
    )
    assert resp.status == 400
    data = await resp.json()
    assert "error" in data


@pytest.mark.asyncio
async def test_status_returns_last_event(client: TestClient) -> None:
    """POST event then GET /status, verify round-trip."""
    await client.post("/event", json=SAMPLE_HOOK_EVENT)

    resp = await client.get("/status")
    assert resp.status == 200
    data = await resp.json()

    assert "sess-abc" in data
    session = data["sess-abc"]
    assert session["status"] == "tool_call"
    assert session["tool"] == "Bash"
    assert session["session_id"] == "sess-abc"
    assert session["instance_name"] == "test-instance"
    assert session["v"] == 1


@pytest.mark.asyncio
async def test_sse_stream_receives_posted_event(client: TestClient) -> None:
    """CRITICAL: Real SSE integration test.

    Connect an async reader to GET /events, POST an event to /event,
    assert the SSE data line arrives with correct JSON.
    Uses readline() to parse the SSE text/event-stream protocol.
    """
    received_events: list[dict] = []

    async def sse_reader() -> None:
        resp = await client.get("/events")
        assert resp.status == 200
        assert "text/event-stream" in resp.headers.get("Content-Type", "")

        # Read SSE lines until we get an "update" event
        current_event = ""
        current_data = ""
        while True:
            line = await asyncio.wait_for(
                resp.content.readline(), timeout=2.0
            )
            decoded = line.decode().rstrip("\r\n")

            if decoded.startswith("event:"):
                current_event = decoded[len("event:"):].strip()
            elif decoded.startswith("data:"):
                current_data = decoded[len("data:"):].strip()
            elif decoded == "":
                # Empty line = end of SSE block
                if current_event == "update" and current_data:
                    received_events.append(json.loads(current_data))
                    return
                # Reset for next block (skip snapshot, keepalive, etc.)
                current_event = ""
                current_data = ""

    reader_task = asyncio.create_task(sse_reader())

    # Give the SSE connection time to establish
    await asyncio.sleep(0.1)

    # POST an event
    resp = await client.post("/event", json=SAMPLE_HOOK_EVENT)
    assert resp.status == 202

    # Wait for the SSE reader to receive the event
    await asyncio.wait_for(reader_task, timeout=2.0)

    assert len(received_events) == 1
    event_data = received_events[0]
    assert event_data["status"] == "tool_call"
    assert event_data["tool"] == "Bash"
    assert event_data["session_id"] == "sess-abc"
    assert event_data["instance_name"] == "test-instance"


@pytest.mark.asyncio
async def test_multi_session_state(client: TestClient) -> None:
    """POST events from two different session_ids, verify /status returns both."""
    event_1 = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Edit",
        "tool_input": {"file_path": "/tmp/a.py"},
        "session_id": "sess-111",
    }
    event_2 = {
        "hook_event_name": "Stop",
        "session_id": "sess-222",
    }

    await client.post("/event", json=event_1)
    await client.post("/event", json=event_2)

    resp = await client.get("/status")
    assert resp.status == 200
    data = await resp.json()

    assert "sess-111" in data
    assert "sess-222" in data
    assert data["sess-111"]["status"] == "tool_call"
    assert data["sess-111"]["tool"] == "Edit"
    assert data["sess-222"]["status"] == "complete"


@pytest.mark.asyncio
async def test_status_empty_on_start(client: TestClient) -> None:
    """GET /status returns empty dict when no events have been posted."""
    resp = await client.get("/status")
    assert resp.status == 200
    data = await resp.json()
    assert data == {}


@pytest.mark.asyncio
async def test_session_state_overwritten_by_latest_event(client: TestClient) -> None:
    """Posting a second event for the same session_id overwrites the first."""
    event_1 = {
        "hook_event_name": "PreToolUse",
        "tool_name": "Bash",
        "tool_input": {"command": "test"},
        "session_id": "sess-overwrite",
    }
    event_2 = {
        "hook_event_name": "Stop",
        "session_id": "sess-overwrite",
    }

    await client.post("/event", json=event_1)
    await client.post("/event", json=event_2)

    resp = await client.get("/status")
    data = await resp.json()
    assert data["sess-overwrite"]["status"] == "complete"
