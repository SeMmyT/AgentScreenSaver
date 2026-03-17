"""SSE bridge server for Claude Code hook events.

Receives hook events via POST, derives status updates, and fans out
to Server-Sent Events clients. Maintains per-session state.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import os
import socket
from typing import Any

from aiohttp import web
from aiohttp_sse import sse_response

from bridge.mdns import register_service, unregister_service
from bridge.models import AgentState, HookEvent, METRIC_FIELDS, StatusUpdate, SubAgent

logger = logging.getLogger(__name__)


def _broadcast(app: web.Application, payload: str) -> None:
    """Fan out a payload to all SSE clients, pruning dead queues."""
    dead: list[asyncio.Queue[str]] = []
    for queue in app["sse_clients"]:
        try:
            queue.put_nowait(payload)
        except asyncio.QueueFull:
            dead.append(queue)
    for q in dead:
        app["sse_clients"].discard(q)
        logger.warning("Dropped SSE client due to full queue")


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
    _broadcast(request.app, update.to_json())

    return web.json_response({"accepted": True}, status=202)


async def metrics_handler(request: web.Request) -> web.Response:
    """POST /session/{session_id}/metrics — update session metrics from statusline."""
    session_id = request.match_info["session_id"]
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)

    sessions: dict[str, StatusUpdate] = request.app["sessions"]
    update = sessions.get(session_id)

    if update is None:
        # Create a minimal placeholder session
        update = StatusUpdate(
            status=AgentState.THINKING,
            session_id=session_id,
            instance_name=request.app["instance_name"],
            event="statusline",
        )
        sessions[session_id] = update

    # Merge metrics with type validation and dirty check
    changed = False
    for field_name, expected_types in METRIC_FIELDS.items():
        if field_name not in data:
            continue
        val = data[field_name]
        if val is not None and not isinstance(val, expected_types):
            continue  # skip mistyped values silently
        if getattr(update, field_name) != val:
            setattr(update, field_name, val)
            changed = True

    if changed:
        _broadcast(request.app, update.to_json())

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


async def broadcast_input_handler(request: web.Request) -> web.Response:
    """POST /broadcast — send input to all active sessions at once."""
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)

    text = data.get("text", "").strip()
    if not text:
        return web.json_response({"error": "empty input"}, status=400)

    sessions: dict[str, StatusUpdate] = request.app["sessions"]
    pending: dict[str, list[str]] = request.app["pending_input"]
    sent_to = []
    for session_id in sessions:
        pending.setdefault(session_id, []).append(text)
        sent_to.append(session_id)
        notification = json.dumps({"session_id": session_id, "text": text})
        _broadcast(request.app, f"INPUT:{notification}")

    logger.info("Broadcast to %d sessions: %s", len(sent_to), text[:50])
    return web.json_response({"accepted": True, "sent_to": sent_to}, status=202)


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
    _broadcast(request.app, f"INPUT:{notification}")

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


async def spawn_handler(request: web.Request) -> web.Response:
    """POST /spawn — spawn a new Claude Code session in a tmux window."""
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        data = {}

    cwd = data.get("cwd", os.path.expanduser("~/codeprojects"))
    prompt = data.get("prompt", "")

    import subprocess
    import uuid

    session_name = "cc-orch"
    tab_id = f"cc-{uuid.uuid4().hex[:8]}"

    # Ensure tmux session exists
    check = subprocess.run(["tmux", "has-session", "-t", session_name],
                           capture_output=True)
    if check.returncode != 0:
        subprocess.run(["tmux", "new-session", "-d", "-s", session_name,
                        "-c", cwd], check=True)
        window_name = tab_id
        # Rename the first window
        subprocess.run(["tmux", "rename-window", "-t", f"{session_name}:0",
                        window_name])
    else:
        window_name = tab_id
        subprocess.run(["tmux", "new-window", "-t", session_name,
                        "-n", window_name, "-c", cwd], check=True)

    # Launch claude in the new window
    cmd = "claude --dangerously-skip-permissions --no-chrome"
    if prompt:
        cmd += f" -p {json.dumps(prompt)}"
    subprocess.run(["tmux", "send-keys", "-t",
                    f"{session_name}:{window_name}", cmd, "Enter"])

    logger.info("Spawned %s in %s (cwd: %s)", tab_id, session_name, cwd)
    return web.json_response({"accepted": True, "tab_id": tab_id,
                              "tmux_target": f"{session_name}:{window_name}"},
                             status=201)


DASHBOARD_HTML = """\
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Ghost Dashboard</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}
:root{
  --bg:#0a0a0f;--card:#14141f;--border:#1e1e2e;--text:#c8c8d8;
  --dim:#666680;--accent:#7c5cbf;--green:#2dd4a0;--yellow:#e8b931;
  --red:#e85555;--blue:#5599e8;--orange:#e89040;
}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
  background:var(--bg);color:var(--text);font-size:14px;
  -webkit-tap-highlight-color:transparent;padding-bottom:env(safe-area-inset-bottom)}
.topbar{position:sticky;top:0;z-index:10;background:var(--bg);
  border-bottom:1px solid var(--border);padding:8px 12px}
.topbar h1{font-size:15px;font-weight:600;color:var(--dim);letter-spacing:.5px;margin-bottom:6px}
.broadcast{display:flex;gap:6px}
.broadcast input{flex:1;background:var(--card);border:1px solid var(--border);
  color:var(--text);padding:8px 10px;border-radius:8px;font-size:14px;outline:none}
.broadcast input:focus{border-color:var(--accent)}
.broadcast button{background:var(--accent);color:#fff;border:none;
  padding:8px 14px;border-radius:8px;font-size:13px;font-weight:600;cursor:pointer}
.broadcast button:active{opacity:.7}
.conn{display:inline-block;width:7px;height:7px;border-radius:50%;
  margin-left:6px;vertical-align:middle}
.conn.ok{background:var(--green)}.conn.err{background:var(--red)}
#cards{padding:8px}
.card{background:var(--card);border:1px solid var(--border);border-radius:10px;
  margin-bottom:8px;overflow:hidden;transition:border-color .15s}
.card.awaiting_input{border-color:var(--yellow)}
.card.error{border-color:var(--red)}
.card-head{padding:10px 12px;cursor:pointer;display:flex;flex-wrap:wrap;
  align-items:center;gap:6px}
.badge{display:inline-block;padding:2px 7px;border-radius:4px;
  font-size:11px;font-weight:600;text-transform:uppercase;letter-spacing:.3px}
.badge.idle{background:#1a1a2e;color:var(--dim)}
.badge.thinking{background:#1a1a3a;color:var(--blue)}
.badge.tool_call{background:#1a2a1a;color:var(--green)}
.badge.awaiting_input{background:#2a2a1a;color:var(--yellow)}
.badge.error{background:#2a1a1a;color:var(--red)}
.badge.complete{background:#1a1a1a;color:var(--dim)}
.sname{font-weight:600;font-size:13px;flex:1;min-width:0;
  white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.ctx{font-size:11px;color:var(--dim);white-space:nowrap}
.ctx .pct{color:var(--text);font-weight:600}
.ctx .cost{color:var(--green)}
.meta{padding:0 12px 6px;font-size:12px;color:var(--dim);
  display:flex;flex-wrap:wrap;gap:4px 12px}
.meta .tool{color:var(--green)}.meta .cwd{color:var(--dim);font-family:monospace;font-size:11px}
.meta .msg{color:var(--text);width:100%;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.detail{display:none;border-top:1px solid var(--border);padding:8px 12px}
.card.open .detail{display:block}
.detail pre{font-size:11px;color:var(--dim);white-space:pre-wrap;word-break:break-all;
  max-height:140px;overflow-y:auto;margin-bottom:8px;line-height:1.4}
.detail .input-row{display:flex;gap:6px}
.detail .input-row input{flex:1;background:var(--bg);border:1px solid var(--border);
  color:var(--text);padding:6px 8px;border-radius:6px;font-size:13px;outline:none}
.detail .input-row input:focus{border-color:var(--accent)}
.detail .input-row button{background:var(--accent);color:#fff;border:none;
  padding:6px 12px;border-radius:6px;font-size:12px;cursor:pointer}
.subagents{padding:2px 0 4px;display:flex;flex-wrap:wrap;gap:4px}
.sa{font-size:10px;background:#1a1a2e;color:var(--blue);padding:2px 6px;border-radius:3px}
.empty{text-align:center;color:var(--dim);padding:40px 20px;font-size:15px}
.ago{font-size:10px;color:var(--dim)}
@media(min-width:600px){
  #cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(340px,1fr));gap:8px}
  .card{margin-bottom:0}
}
</style>
</head>
<body>
<div class="topbar">
  <h1>GHOST ORCHESTRATOR <span id="conn" class="conn err"></span>
    <span id="count" class="ctx" style="float:right"></span></h1>
  <div class="broadcast">
    <input id="bc-input" placeholder="broadcast to all sessions..." autocomplete="off">
    <button id="bc-btn">Send</button>
  </div>
</div>
<div id="cards"></div>
<script>
const sessions={};
const container=document.getElementById('cards');
const connDot=document.getElementById('conn');
const countEl=document.getElementById('count');
let evtSource=null;

function statusColor(s){
  return{idle:'idle',thinking:'thinking',tool_call:'tool_call',
    awaiting_input:'awaiting_input',error:'error',complete:'complete'}[s]||'idle';
}

function ago(ts){
  if(!ts)return'';
  const d=Date.now()-new Date(ts).getTime();
  if(d<60000)return Math.floor(d/1000)+'s ago';
  if(d<3600000)return Math.floor(d/60000)+'m ago';
  return Math.floor(d/3600000)+'h ago';
}

function shortCwd(p){
  if(!p)return'';
  const h=p.replace(/^\\/home\\/[^/]+/,'~');
  const parts=h.split('/');
  return parts.length>3?'.../'+ parts.slice(-2).join('/'):h;
}

function shortId(id){return id?id.slice(0,8):'???'}

function renderCard(s){
  const id=s.session_id;
  let el=document.getElementById('s-'+id);
  const isNew=!el;
  if(isNew){
    el=document.createElement('div');
    el.id='s-'+id;
    el.className='card';
    el.innerHTML=`
      <div class="card-head" data-id="${id}">
        <span class="badge"></span>
        <span class="sname"></span>
        <span class="ctx"></span>
      </div>
      <div class="meta"></div>
      <div class="detail">
        <pre class="output"></pre>
        <div class="input-row">
          <input placeholder="send to this session..." data-sid="${id}" autocomplete="off">
          <button data-sid="${id}">Send</button>
        </div>
      </div>`;
    el.querySelector('.card-head').addEventListener('click',()=>el.classList.toggle('open'));
    el.querySelector('.detail button').addEventListener('click',function(){
      const inp=el.querySelector('.detail input');
      sendInput(id,inp.value);inp.value='';
    });
    el.querySelector('.detail input').addEventListener('keydown',function(e){
      if(e.key==='Enter'){sendInput(id,this.value);this.value='';}
    });
    container.appendChild(el);
  }

  const st=s.status||'idle';
  el.className='card'+(el.classList.contains('open')?' open':'');
  if(st==='awaiting_input')el.classList.add('awaiting_input');
  if(st==='error')el.classList.add('error');

  el.querySelector('.badge').className='badge '+statusColor(st);
  el.querySelector('.badge').textContent=st.replace('_',' ');

  const label=s.custom_label||shortId(s.session_id);
  el.querySelector('.sname').textContent=label;

  const m=s.metrics||{};
  let ctxHtml='';
  if(m.context_percent!=null)ctxHtml+=`<span class="pct">${Math.round(m.context_percent)}%</span> ctx `;
  if(m.cost_usd!=null)ctxHtml+=`<span class="cost">$${m.cost_usd.toFixed(2)}</span>`;
  el.querySelector('.ctx').innerHTML=ctxHtml;

  let metaHtml='';
  if(s.tool)metaHtml+=`<span class="tool">${s.tool}</span>`;
  if(m.cwd)metaHtml+=`<span class="cwd">${shortCwd(m.cwd)}</span>`;
  if(s.ts)metaHtml+=`<span class="ago">${ago(s.ts)}</span>`;
  if(s.message)metaHtml+=`<span class="msg">${esc(s.message)}</span>`;
  if(s.sub_agents&&s.sub_agents.length){
    metaHtml+='<div class="subagents">'+s.sub_agents.map(a=>
      `<span class="sa">${esc(a.name||a.agent_type)}</span>`).join('')+'</div>';
  }
  el.querySelector('.meta').innerHTML=metaHtml;

  // Append to output log (last 5 lines)
  const pre=el.querySelector('.output');
  const line=`[${new Date(s.ts).toLocaleTimeString()}] ${st} ${s.tool||''} ${s.tool_input_summary||''}`;
  const lines=(pre.textContent?pre.textContent.split('\\n'):[]).concat(line);
  pre.textContent=lines.slice(-5).join('\\n');

  // Pulse awaiting_input cards
  if(st==='awaiting_input'&&!el.classList.contains('open')){
    el.classList.add('open');
  }
}

function esc(s){
  const d=document.createElement('div');d.textContent=s;return d.innerHTML;
}

function updateCount(){
  const n=Object.keys(sessions).length;
  const active=Object.values(sessions).filter(s=>s.status!=='complete').length;
  countEl.textContent=`${active}/${n} active`;
}

function sendInput(sid,text){
  if(!text||!text.trim())return;
  fetch(`/session/${sid}/input`,{
    method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({text:text.trim()})
  });
}

function sendBroadcast(){
  const inp=document.getElementById('bc-input');
  const text=inp.value.trim();
  if(!text)return;
  fetch('/broadcast',{
    method:'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({text})
  });
  inp.value='';
}

document.getElementById('bc-btn').addEventListener('click',sendBroadcast);
document.getElementById('bc-input').addEventListener('keydown',e=>{
  if(e.key==='Enter')sendBroadcast();
});

function connectSSE(){
  if(evtSource)evtSource.close();
  evtSource=new EventSource('/events');

  evtSource.addEventListener('snapshot',e=>{
    const snap=JSON.parse(e.data);
    for(const[sid,data]of Object.entries(snap)){
      sessions[sid]=data;
      renderCard(data);
    }
    updateCount();
  });

  evtSource.addEventListener('update',e=>{
    const data=JSON.parse(e.data);
    sessions[data.session_id]=data;
    renderCard(data);
    updateCount();
  });

  evtSource.addEventListener('input_pending',e=>{
    const data=JSON.parse(e.data);
    const el=document.getElementById('s-'+data.session_id);
    if(el){
      const pre=el.querySelector('.output');
      pre.textContent+='\\n>> PENDING INPUT: '+data.text;
    }
  });

  evtSource.addEventListener('keepalive',()=>{});

  evtSource.onopen=()=>{connDot.className='conn ok';};
  evtSource.onerror=()=>{
    connDot.className='conn err';
    evtSource.close();
    setTimeout(connectSSE,3000);
  };
}

connectSSE();

// Refresh relative timestamps every 15s
setInterval(()=>{
  for(const s of Object.values(sessions))renderCard(s);
},15000);

// Show empty state
if(!container.children.length){
  container.innerHTML='<div class="empty">No sessions yet — waiting for hooks...</div>';
}

// Clear empty state when first card arrives
const obs=new MutationObserver(()=>{
  const empty=container.querySelector('.empty');
  if(empty&&container.children.length>1)empty.remove();
});
obs.observe(container,{childList:true});
</script>
</body>
</html>
"""


async def dashboard_handler(request: web.Request) -> web.Response:
    """GET /dashboard — mobile-first Ghost orchestrator dashboard."""
    return web.Response(text=DASHBOARD_HTML, content_type="text/html")


async def skins_list_handler(request: web.Request) -> web.Response:
    """GET /skins — list available community skins."""
    from bridge.skins import list_skins
    return web.json_response(list_skins())


async def skin_get_handler(request: web.Request) -> web.Response:
    """GET /skins/{skin_id} — get full skin data."""
    from bridge.skins import get_skin
    skin_id = request.match_info["skin_id"]
    data = get_skin(skin_id)
    if data is None:
        return web.json_response({"error": "not found"}, status=404)
    return web.json_response(data)


async def skin_upload_handler(request: web.Request) -> web.Response:
    """POST /skins — upload a community skin."""
    from bridge.skins import save_skin
    try:
        data = await request.json()
    except (json.JSONDecodeError, Exception):
        return web.json_response({"error": "invalid JSON"}, status=400)
    if "id" not in data or "name" not in data:
        return web.json_response({"error": "missing id or name"}, status=400)
    skin_id = save_skin(data)
    return web.json_response({"accepted": True, "id": skin_id}, status=201)


async def skin_delete_handler(request: web.Request) -> web.Response:
    """DELETE /skins/{skin_id} — remove a community skin."""
    from bridge.skins import delete_skin
    skin_id = request.match_info["skin_id"]
    if delete_skin(skin_id):
        return web.json_response({"deleted": True})
    return web.json_response({"error": "not found or protected"}, status=404)


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
    app.router.add_post("/session/{session_id}/metrics", metrics_handler)
    app.router.add_post("/broadcast", broadcast_input_handler)
    app.router.add_post("/session/{session_id}/input", input_submit_handler)
    app.router.add_get("/session/{session_id}/input", input_poll_handler)
    app.router.add_post("/session/{session_id}/customize", customize_handler)
    app.router.add_post("/spawn", spawn_handler)
    app.router.add_get("/dashboard", dashboard_handler)
    app.router.add_get("/skins", skins_list_handler)
    app.router.add_get("/skins/{skin_id}", skin_get_handler)
    app.router.add_post("/skins", skin_upload_handler)
    app.router.add_delete("/skins/{skin_id}", skin_delete_handler)

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
