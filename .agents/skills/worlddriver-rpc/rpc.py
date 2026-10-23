#!/usr/bin/env python3
"""Call the worlddriver JSON-RPC websocket directly.

Why this exists
---------------
The worlddriver mod exposes the SAME control surface two ways: an MCP server
(HTTP, the `mcp__worlddriver__*` tools) and a JSON-RPC websocket. They share one
dispatcher, so every MCP tool has an identical RPC method (`mc_bot_goto` ⇄
`mc.bot.goto`). Prefer the MCP tools for normal use — they're typed and ergonomic.

Reach for THIS script when the MCP tools can't express what you need:
  - A newly-added param/setting/tool is missing from the MCP tool schema. The
    harness freezes the MCP tool schemas at session start; a rebuilt+relaunched
    mod accepts the new key but the harness strips it before sending. RPC has no
    such freeze — it forwards your params verbatim.
  - You want to script a multi-step setup (give → tp → fill → set → goto) as one
    shell block instead of N separate tool calls.
  - You're driving the client headless (title→world, input, screenshots) the way
    into_world.py does, outside the MCP tool set.

Wire format (NOT JSON-RPC 2.0 — a hand-rolled envelope):
    send {"id": N, "method": "mc.x.y", "params": {...}}
    recv {"id": N,    "result": <any>}                    on success
    recv {"id": N,    "error": "<msg>", "code": -326xx}   on failure
    recv {"id": null, "error": "<msg>", "code": -32700}   too malformed to echo an id
    recv {"method": "notifications/message", ...}         an event push, no "id" key
The "id" KEY is on every response and absent from every notification — that, not
the id's value, is how the two are told apart.

Usage
-----
    # One-shot call with params (params = a JSON object string)
    python3 rpc.py mc.bot.setting '{"allowParkourPlace": true}'
    python3 rpc.py mc.bot.goto '{"pos":{"x":0,"y":-60,"z":0}, "awaitMs":8000}'

    # No-param read (omit the JSON arg)
    python3 rpc.py mc.observe.player
    python3 rpc.py mc.bot.status
    python3 rpc.py mc.system.version

    # Batch: many calls in sequence, one per line "<method> [json]", from a file
    # or stdin (lines starting with # are comments; blank lines skipped). Stops
    # on the first error unless --keep-going.
    python3 rpc.py --batch setup.rpc
    printf 'mc.action.runCommand {"cmd":"tp @p 0 -60 0"}\nmc.observe.player\n' | python3 rpc.py --batch -

Options
-------
    --port N        RPC port (default: $AGENT_RPC_PORT, else worlddriver-rpc.port file, else 39801)
    --host H        RPC host (default: $AGENT_RPC_HOST, else 127.0.0.1; the mod binds
                    127.0.0.1 unless launched with -Dworlddriver.rpcHost). IPv6 literals OK.
    --raw           print the full {id,result|error} envelope, not just result
    --compact       single-line JSON (default is indent=2)
    --jq EXPR       project the result with a dotted path, e.g. --jq blockPos.y
    --keep-going    in --batch mode, continue after an errored call
    --timeout S     per-call recv timeout seconds (default 35)

Exit code is non-zero if any call returns an error or the connection fails.
"""
import argparse
import asyncio
import json
import os
import sys
from pathlib import Path

try:
    import websockets
except ImportError:
    sys.exit("rpc.py needs the 'websockets' package — install it, or run without a "
             "venv via: uv run --with websockets rpc.py …")


def resolve_host(explicit):
    """--host > $AGENT_RPC_HOST > 127.0.0.1. The mod binds 127.0.0.1 by default
    (override with -Dworlddriver.rpcHost); only set this to reach a non-loopback bind."""
    host = explicit or os.environ.get("AGENT_RPC_HOST") or "127.0.0.1"
    # Bracket a bare IPv6 literal so it's valid in a ws:// URL ('::1' → '[::1]').
    if ":" in host and not host.startswith("["):
        host = f"[{host}]"
    return host


def resolve_port(explicit):
    """--port > $AGENT_RPC_PORT > nearest worlddriver-rpc.port file > 39801."""
    if explicit:
        return explicit
    env = os.environ.get("AGENT_RPC_PORT")
    if env:
        return int(env)
    # The mod writes the live port to fabric/run/worlddriver-rpc.port — search upward
    # from cwd so this works whether run from scripts/, the repo root, or the run dir.
    here = Path.cwd()
    for base in [here, *here.parents]:
        for rel in ("worlddriver-rpc.port", "fabric/run/worlddriver-rpc.port", "run/worlddriver-rpc.port"):
            f = base / rel
            if f.is_file():
                try:
                    return int(f.read_text().strip())
                except ValueError:
                    pass
    return 39801


def dig(value, path):
    """Walk a dotted path (list indices allowed) into a result, JS-truthy-ish."""
    cur = value
    for part in path.split("."):
        if isinstance(cur, list):
            cur = cur[int(part)]
        elif isinstance(cur, dict):
            cur = cur.get(part)
        else:
            return None
    return cur


def render(result, args):
    if args.jq is not None:
        result = dig(result, args.jq)
    if isinstance(result, (dict, list)):
        return json.dumps(result, separators=(",", ":")) if args.compact \
            else json.dumps(result, indent=2, ensure_ascii=False)
    return json.dumps(result, ensure_ascii=False)


async def call(ws, rid, method, params, timeout):
    """Send one request, return (ok, payload). payload is result or error string."""
    await ws.send(json.dumps({"id": rid, "method": method, "params": params or {}}))
    while True:
        msg = json.loads(await asyncio.wait_for(ws.recv(), timeout=timeout))
        if "id" not in msg:
            continue  # a notification (mc.events.subscribe push), never a reply
        # id=null means the server could not echo an id back (-32700 parse /
        # -32600 invalid request). Exactly one request is ever in flight here,
        # so it is unambiguously ours; skipping it would block until --timeout
        # and report a timeout instead of the reason the server just sent.
        if msg["id"] is not None and msg["id"] != rid:
            continue  # not our reply (shouldn't interleave, but be safe)
        if "error" in msg:
            code = msg.get("code")
            return False, f"{msg['error']} (code {code})" if code else msg["error"]
        return True, msg.get("result")


def parse_call_line(line):
    """'mc.x.y {json}' -> (method, params). Bare method -> empty params."""
    line = line.strip()
    parts = line.split(None, 1)
    method = parts[0]
    params = json.loads(parts[1]) if len(parts) > 1 and parts[1].strip() else {}
    return method, params


async def run(args):
    port = resolve_port(args.port)
    host = resolve_host(args.host)
    uri = f"ws://{host}:{port}/rpc"

    # Build the call list: batch (file/stdin) or a single CLI call.
    calls = []
    if args.batch is not None:
        text = sys.stdin.read() if args.batch == "-" else Path(args.batch).read_text()
        for line in text.splitlines():
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            calls.append(parse_call_line(s))
    else:
        params = json.loads(args.params) if args.params else {}
        calls.append((args.method, params))

    try:
        ws = await websockets.connect(uri, max_size=16 * 1024 * 1024, ping_interval=None)
    except Exception as e:  # noqa: BLE001 — surface a clear, actionable message
        sys.exit(f"could not connect to {uri}: {e}\n"
                 f"  • is the client/server up? probe with: python3 rpc.py mc.system.version\n"
                 f"  • wrong port? pass --port or set AGENT_RPC_PORT (default 39801)\n"
                 f"  • bound to a non-loopback host? pass --host or set AGENT_RPC_HOST")

    failed = False
    async with ws:
        for i, (method, params) in enumerate(calls, start=1):
            ok, payload = await call(ws, i, method, params, args.timeout)
            if ok:
                if len(calls) > 1:
                    print(f"# {method}")
                print(render(payload, args) if not args.raw
                      else json.dumps({"id": i, "result": payload}, indent=2, ensure_ascii=False))
            else:
                failed = True
                msg = render(payload, args) if args.raw else f"ERROR {method}: {payload}"
                print(msg, file=sys.stderr)
                if not args.keep_going:
                    break
    if failed:
        sys.exit(1)


def main():
    ap = argparse.ArgumentParser(description="Call the worlddriver RPC websocket.")
    ap.add_argument("method", nargs="?", help="dotted RPC method, e.g. mc.bot.status")
    ap.add_argument("params", nargs="?", help="JSON object of params (omit for none)")
    ap.add_argument("--batch", metavar="FILE", help="run '<method> [json]' lines from FILE ('-' = stdin)")
    ap.add_argument("--port", type=int, help="RPC port (default: env/port-file/39801)")
    ap.add_argument("--host", help="RPC host (default: $AGENT_RPC_HOST, else 127.0.0.1)")
    ap.add_argument("--raw", action="store_true", help="print the full {id,result|error} envelope")
    ap.add_argument("--compact", action="store_true", help="single-line JSON output")
    ap.add_argument("--jq", metavar="PATH", help="project result with a dotted path, e.g. blockPos.y")
    ap.add_argument("--keep-going", action="store_true", help="in --batch, continue past errors")
    ap.add_argument("--timeout", type=float, default=35, help="per-call recv timeout seconds")
    args = ap.parse_args()
    if args.batch is None and not args.method:
        ap.error("provide a METHOD (or --batch FILE)")
    asyncio.run(run(args))


if __name__ == "__main__":
    main()
