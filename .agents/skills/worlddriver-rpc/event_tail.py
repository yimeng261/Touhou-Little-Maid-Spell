#!/usr/bin/env python3
"""event_tail.py — stream live worlddriver game events, one compact line per event.

WHY: the Monitor tool turns each stdout line into a chat notification, so pointing a
Monitor at this script gives real-time push of in-game events (deaths, chat/system
messages, joins/leaves) while a bot runs — the game-side complement to live-screen-watch.

HOW: it reuses the ready-made rpc.py client (its port/host resolvers + `call` helper) and
long-polls `mc.wait.event` on ONE persistent websocket, chaining the cursor. wait.event
returns the instant a matching event arrives (or times out with a fresh cursor), so there
is no polling interval to tune and no gap between calls. Do NOT hand-roll a socket.

USE (as a Monitor command):    python3 <this-dir>/event_tail.py     (persistent: true)
     standalone to eyeball it:  python3 event_tail.py

TUNE (env):
  AGENT_EVENT_TYPES  JSON array of event types. Default below. Available: entity.death,
                     chat.message, player.join, player.leave, block.break/place/fill,
                     world.restore, plus any custom type you emit (e.g. a bot.lowhp
                     watcher — see SKILL.md). Keep block.* OUT of the tail: they flood.
  AGENT_RPC_PORT / AGENT_RPC_HOST  override (else rpc.py finds fabric/run/worlddriver-rpc.port).
"""
import asyncio
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rpc  # reuse resolve_host / resolve_port / call — the one ready-made client

try:
    import websockets
except ImportError:
    sys.exit("event_tail.py needs the 'websockets' package (same one rpc.py/into_world.py use).")

TYPES = json.loads(os.environ.get(
    "AGENT_EVENT_TYPES",
    '["entity.death","chat.message","player.join","player.leave"]'))
WAIT_MS = 110_000  # long-poll window; recv timeout below must exceed it


def emit(event):
    t = event.get("type", "?")
    data = {k: v for k, v in event.items() if k not in ("seq", "type")}
    s = json.dumps(data, ensure_ascii=False)
    if len(s) > 240:
        s = s[:240] + "…"
    print(f"[{time.strftime('%H:%M:%S')}] {t} | {s}", flush=True)


async def stream():
    host, port = rpc.resolve_host(None), rpc.resolve_port(None)
    uri = f"ws://{host}:{port}/rpc"
    rid = 0
    while True:  # outer reconnect loop — a client restart just blips and reseeds
        try:
            async with websockets.connect(uri, max_size=16 * 1024 * 1024,
                                          ping_interval=None) as ws:
                rid += 1
                ok, cur = await rpc.call(ws, rid, "mc.observe.cursor", {}, 15)
                if not ok:
                    raise RuntimeError(f"cursor seed failed: {cur}")
                print(f"[event-tail] armed at cursor {cur}  types={TYPES}",
                      file=sys.stderr, flush=True)
                while True:
                    rid += 1
                    ok, res = await rpc.call(
                        ws, rid, "mc.wait.event",
                        {"cursor": cur, "types": TYPES, "timeoutMs": WAIT_MS},
                        timeout=WAIT_MS / 1000 + 15)
                    if not ok:
                        raise RuntimeError(f"wait.event failed: {res}")
                    for e in res.get("events", []):
                        emit(e)
                    cur = res.get("cursor", cur)
        except Exception as e:  # noqa: BLE001 — reconnect on any drop/error
            print(f"[event-tail] disconnected ({e}); retry in 3s",
                  file=sys.stderr, flush=True)
            await asyncio.sleep(3)


if __name__ == "__main__":
    try:
        asyncio.run(stream())
    except KeyboardInterrupt:
        pass
