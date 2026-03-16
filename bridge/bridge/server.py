"""SSE bridge server for Claude Code hook events.

Receives hook events via POST, derives status updates, and fans out
to Server-Sent Events clients. Maintains per-session state.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import socket
from typing import Any

from aiohttp import web
from aiohttp_sse import sse_response

from bridge.mdns import register_service, unregister_service
from bridge.models import HookEvent, StatusUpdate, SubAgent

logger = logging.getLogger(__name__)


async def health_handler(request: web.Request) -> web.Response:
    """GET /health — instance health check."""
    return web.json_response({
        "status": "ok",
        "instance_name": request.app["instance_name"],
    })


async def event_handler(request: web.Request) -> web.Response:
    """POST /event — accept a Claude Code hook event JSON payload."""
    try:
        raw: dict[str, Any] = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)

    hook_event = HookEvent.from_dict(raw)

    # Capture agent descriptions from PreToolUse for Agent tool
    pending_names: dict[str, list[str]] = request.app["pending_agent_names"]
    if (hook_event.event_name == "PreToolUse"
            and hook_event.tool_name == "Agent"
            and hook_event.tool_input):
        desc = (hook_event.tool_input.get("description")
                or hook_event.tool_input.get("name")
                or "")
        if desc:
            pending_names.setdefault(hook_event.session_id, []).append(desc)

    # Track sub-agent lifecycle (per session)
    all_agents: dict[str, dict[str, SubAgent]] = request.app["active_agents"]
    session_agents = all_agents.setdefault(hook_event.session_id, {})
    if hook_event.event_name == "SubagentStart" and hook_event.agent_id:
        # Pop a pending name if available
        name = ""
        session_pending = pending_names.get(hook_event.session_id, [])
        if session_pending:
            name = session_pending.pop(0)
        session_agents[hook_event.agent_id] = SubAgent(
            agent_id=hook_event.agent_id,
            agent_type=hook_event.agent_type or "unknown",
            status="running",
            name=name,
        )
    elif hook_event.event_name == "SubagentStop" and hook_event.agent_id:
        session_agents.pop(hook_event.agent_id, None)

    # Store custom ASCII if provided
    customizations = request.app["session_customizations"]
    if hook_event.custom_frames:
        customizations.setdefault(hook_event.session_id, {})["frames"] = hook_event.custom_frames
    if hook_event.custom_label:
        customizations.setdefault(hook_event.session_id, {})["label"] = hook_event.custom_label

    update = StatusUpdate.from_event(hook_event, instance_name=request.app["instance_name"])
    update.sub_agents = list(session_agents.values())

    # Merge stored customizations
    stored = customizations.get(hook_event.session_id, {})
    if stored.get("frames") and not update.custom_frames:
        update.custom_frames = stored["frames"]
    if stored.get("label") and not update.custom_label:
        update.custom_label = stored["label"]

    # Store by session_id
    request.app["sessions"][update.session_id] = update

    # Fan out to SSE clients
    payload = update.to_json()
    dead_queues: list[asyncio.Queue[str]] = []
    for queue in request.app["sse_clients"]:
        try:
            queue.put_nowait(payload)
        except asyncio.QueueFull:
            dead_queues.append(queue)

    for q in dead_queues:
        request.app["sse_clients"].discard(q)
        logger.warning("Dropped SSE client due to full queue")

    return web.json_response({"accepted": True}, status=202)


async def sse_handler(request: web.Request) -> web.StreamResponse:
    """GET /events — Server-Sent Events stream.

    On connect, sends current sessions snapshot.  Then streams live updates.
    """
    queue: asyncio.Queue[str] = asyncio.Queue(maxsize=50)
    request.app["sse_clients"].add(queue)

    try:
        async with sse_response(request) as resp:
            # Send current sessions snapshot on connect
            sessions: dict[str, StatusUpdate] = request.app["sessions"]
            if sessions:
                snapshot = {
                    sid: s.to_dict() for sid, s in sessions.items()
                }
                await resp.send(json.dumps(snapshot), event="snapshot")

            # Stream live updates
            while True:
                try:
                    payload = await asyncio.wait_for(queue.get(), timeout=30.0)
                    if payload.startswith("INPUT:"):
                        await resp.send(payload[6:], event="input_pending")
                    else:
                        await resp.send(payload, event="update")
                except asyncio.TimeoutError:
                    # Send keepalive comment
                    await resp.send("", event="keepalive")
                except (ConnectionResetError, ConnectionAbortedError):
                    break
    finally:
        request.app["sse_clients"].discard(queue)

    return resp


async def status_handler(request: web.Request) -> web.Response:
    """GET /status — JSON snapshot of all active sessions."""
    sessions: dict[str, StatusUpdate] = request.app["sessions"]
    data = {sid: s.to_dict() for sid, s in sessions.items()}
    return web.json_response(data)


async def input_submit_handler(request: web.Request) -> web.Response:
    """POST /session/{session_id}/input — queue user input from the phone."""
    session_id = request.match_info["session_id"]
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)

    text = data.get("text", "").strip()
    if not text:
        return web.json_response({"error": "empty input"}, status=400)

    pending: dict[str, list[str]] = request.app["pending_input"]
    pending.setdefault(session_id, []).append(text)

    # Broadcast input_pending event to SSE clients
    notification = json.dumps({
        "session_id": session_id,
        "text": text,
    })
    for queue in request.app["sse_clients"]:
        try:
            queue.put_nowait(f"INPUT:{notification}")
        except asyncio.QueueFull:
            pass

    logger.info("Input queued for session %s: %s", session_id[:8], text[:50])
    return web.json_response({"accepted": True, "session_id": session_id}, status=202)


async def input_poll_handler(request: web.Request) -> web.Response:
    """GET /session/{session_id}/input — poll and consume pending input."""
    session_id = request.match_info["session_id"]
    pending: dict[str, list[str]] = request.app["pending_input"]
    messages = pending.pop(session_id, [])
    return web.json_response({"session_id": session_id, "messages": messages})


async def customize_handler(request: web.Request) -> web.Response:
    """POST /session/{session_id}/customize — set custom ASCII art for a session."""
    session_id = request.match_info["session_id"]
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)

    customizations = request.app["session_customizations"]
    customizations.setdefault(session_id, {})
    if "frames" in data:
        customizations[session_id]["frames"] = data["frames"]
    if "label" in data:
        customizations[session_id]["label"] = data["label"]

    return web.json_response({"accepted": True, "session_id": session_id}, status=200)


async def on_startup_mdns(app: web.Application) -> None:
    """Register mDNS service on startup (non-fatal if fails)."""
    try:
        zc, info = register_service(
            port=app["port"], instance_name=app["instance_name"]
        )
        app["mdns_zc"] = zc
        app["mdns_info"] = info
    except (ValueError, OSError) as exc:
        logger.warning("mDNS registration failed (non-fatal): %s", exc)
        app["mdns_zc"] = None
        app["mdns_info"] = None


async def on_shutdown_mdns(app: web.Application) -> None:
    """Unregister mDNS service on shutdown."""
    zc = app.get("mdns_zc")
    info = app.get("mdns_info")
    if zc is not None and info is not None:
        try:
            unregister_service(zc, info)
        except OSError as exc:
            logger.warning("mDNS unregistration failed: %s", exc)


def create_app(
    instance_name: str = "default",
    port: int = 4001,
    enable_mdns: bool = True,
) -> web.Application:
    """Create and configure the aiohttp application."""
    app = web.Application()
    app["instance_name"] = instance_name
    app["sessions"] = {}  # dict[str, StatusUpdate]
    app["active_agents"] = {}  # dict[str, dict[str, SubAgent]]
    app["sse_clients"] = set()  # set[asyncio.Queue[str]]
    app["session_customizations"] = {}  # dict[str, dict] — {session_id: {"frames": [...], "label": "..."}}
    app["pending_agent_names"] = {}  # dict[str, list[str]] — descriptions from PreToolUse Agent calls
    app["pending_input"] = {}  # dict[str, list[str]] — user input from phone
    app["port"] = port

    app.router.add_get("/health", health_handler)
    app.router.add_post("/event", event_handler)
    app.router.add_get("/events", sse_handler)
    app.router.add_get("/status", status_handler)
    app.router.add_post("/session/{session_id}/input", input_submit_handler)
    app.router.add_get("/session/{session_id}/input", input_poll_handler)
    app.router.add_post("/session/{session_id}/customize", customize_handler)

    if enable_mdns:
        app.on_startup.append(on_startup_mdns)
        app.on_shutdown.append(on_shutdown_mdns)

    return app


def main() -> None:
    """Entry point for the bridge server CLI."""
    parser = argparse.ArgumentParser(description="Claude ScreenSaver SSE Bridge")
    parser.add_argument("--port", type=int, default=4001, help="Port to listen on")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind to")
    parser.add_argument("--name", default=None, help="Instance name (default: hostname)")
    parser.add_argument("--no-mdns", action="store_true", help="Disable mDNS announcement")
    args = parser.parse_args()

    instance_name = args.name or socket.gethostname()
    app = create_app(
        instance_name=instance_name,
        port=args.port,
        enable_mdns=not args.no_mdns,
    )

    logging.basicConfig(level=logging.INFO)
    logger.info("Starting bridge on %s:%d as '%s'", args.host, args.port, instance_name)
    web.run_app(app, host=args.host, port=args.port, print=None)
