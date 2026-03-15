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
from bridge.models import HookEvent, StatusUpdate

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
    update = StatusUpdate.from_event(hook_event, instance_name=request.app["instance_name"])

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


def create_app(instance_name: str = "default", port: int = 4001) -> web.Application:
    """Create and configure the aiohttp application."""
    app = web.Application()
    app["instance_name"] = instance_name
    app["sessions"] = {}  # dict[str, StatusUpdate]
    app["sse_clients"] = set()  # set[asyncio.Queue[str]]
    app["port"] = port

    app.router.add_get("/health", health_handler)
    app.router.add_post("/event", event_handler)
    app.router.add_get("/events", sse_handler)
    app.router.add_get("/status", status_handler)

    app.on_startup.append(on_startup_mdns)
    app.on_shutdown.append(on_shutdown_mdns)

    return app


def main() -> None:
    """Entry point for the bridge server CLI."""
    parser = argparse.ArgumentParser(description="Claude ScreenSaver SSE Bridge")
    parser.add_argument("--port", type=int, default=4001, help="Port to listen on")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind to")
    parser.add_argument("--name", default=None, help="Instance name (default: hostname)")
    args = parser.parse_args()

    instance_name = args.name or socket.gethostname()
    app = create_app(instance_name=instance_name, port=args.port)

    logging.basicConfig(level=logging.INFO)
    logger.info("Starting bridge on %s:%d as '%s'", args.host, args.port, instance_name)
    web.run_app(app, host=args.host, port=args.port, print=None)
