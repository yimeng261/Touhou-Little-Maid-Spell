---
name: worlddriver-rpc
description: >-
  Live-control the worlddriver Minecraft mod over its JSON-RPC websocket
  (port 39801) with the bundled rpc.py client and a complete method reference.
  CONSULT THIS SKILL before doing anything with the worlddriver mod's runtime
  API: any mc.bot.* / mc.action.* / mc.observe.* / mc.world.* / mc.client.* /
  mc.query / mc.events / mc.wait.* / mc.recipe.* / mc.plan.acquire /
  mc.script.eval / mc.skill call, scripting a multi-step live setup
  (give→tp→fill→setting→goto), sending a raw method+params to the websocket,
  reaching an RPC-route-only method the MCP layer doesn't expose (the
  StageWright harness verbs mc.test.run / mc.test.reset / mc.test.input.*,
  present only when that runtime is loaded), or when an mcp__worlddriver__* tool won't
  apply a newly-added param/setting (the MCP tool schemas are frozen at session
  start, so new keys must be set over RPC). Don't hand-roll a websocket client or
  guess the wire format, method names, or params — they're all in this skill.
  This is for INVOKING the live mod, NOT for editing its Java
  (BotConfig/ToolCatalog/Walker), writing gametests, or standing up your own MCP
  server.
---

# worlddriver RPC

The worlddriver mod exposes one control surface two ways: the **MCP tools**
(`mcp__worlddriver__*`, typed and ergonomic) and a **JSON-RPC websocket** (same
dispatcher, dotted method names). Prefer the MCP tools for ordinary calls. Use
the RPC websocket — via the bundled **`rpc.py`** — when the MCP layer gets in the
way:

- **A new param/setting/tool isn't in the MCP schema.** The harness caches MCP
  tool schemas at session start and re-fetching (`ToolSearch`) returns the same
  stale copy. After you rebuild + relaunch the mod with a new `mc.bot.setting`
  key (or any new param), the MCP client **strips the unknown key before it ever
  leaves the harness** (the frozen client-side schema doesn't know it) — so it's
  missing from `applied:` and unchanged in the snapshot, even though the snapshot
  now lists it (proof the new build loaded). This is a *client-side* strip, not a
  mod-layer drop: post-#280 the mod's `mc.bot.setting` schema is CLOSED and would
  reject an unknown key **loudly, all-or-nothing**, if one reached it. RPC forwards
  params verbatim — bypassing the stale schema — so it just works. This is the #1
  reason this skill exists.
- **Multi-step setup as one block.** Stage an arena (`give`→`tp`→`fill`→`setting`
  →`goto`) in a single shell invocation instead of N separate tool calls.
- **Headless client driving.** Title→world, input, screenshots — the surface
  `into_world.py` uses, outside the MCP tool set.

## The client: `rpc.py`

Bundled next to this file. Wire format is a hand-rolled envelope (NOT JSON-RPC
2.0): send `{"id":N,"method":"mc.x.y","params":{…}}`, receive
`{"id":N,"result":…}` or `{"id":N,"error":"<string>","code":<int>}` (`code` is the
JSON-RPC error code; the table is in `references/methods.md`).

**Interpreter.** Needs `websockets`. On the Linux host that's `python3 rpc.py …`;
on a Windows/Git Bash checkout there is no `python3` — use `python` (or the
workspace venv's `.venv/Scripts/python.exe`, note `Scripts/` not `bin/`). When in
doubt, `uv run --with websockets rpc.py …` works everywhere. The examples below
say `python3`; swap the name for your box. Run it from the `scripts/` directory (cwd):

```bash
# probe first — errors until the client is up, then returns uptimeMs
python3 .claude/skills/worlddriver-rpc/rpc.py mc.system.version

# one-shot with params (params = a JSON object string)
python3 .claude/skills/worlddriver-rpc/rpc.py mc.bot.setting '{"allowParkourPlace": true}'

# no-param read (omit the JSON)
python3 .claude/skills/worlddriver-rpc/rpc.py mc.observe.player
python3 .claude/skills/worlddriver-rpc/rpc.py mc.bot.status --jq lastPath

# multi-step setup: one call per line "<method> [json]", '#' comments
python3 .claude/skills/worlddriver-rpc/rpc.py --batch - <<'EOF'
mc.action.runCommand {"cmd":"give @p minecraft:stone 64"}
mc.action.runCommand {"cmd":"tp @p 20 -60 0"}
mc.action.runCommand {"cmd":"fill 16 149 -1 20 149 1 minecraft:stone"}
mc.bot.setting {"allowParkourPlace": true, "walkerDebug": true}
EOF
```

Useful flags: `--jq <dotted.path>` projects the result (`--jq blockPos.y`),
`--raw` prints the full envelope, `--compact` for one-line JSON, `--port N` /
`$AGENT_RPC_PORT` override (default resolves the live `worlddriver-rpc.port` file, else
39801), `--keep-going` to continue a batch past errors. `python3 rpc.py -h` for
the rest. The mod binds **`127.0.0.1`** by default; if it was launched with
`-Dworlddriver.rpcHost=0.0.0.0` (or an IPv6 `::`) to accept remote clients, reach it
with `--host <addr>` / `$AGENT_RPC_HOST` (IPv6 literals are auto-bracketed). For
a local bot you never need `--host` — loopback is included in a wildcard bind.

## Method surface (overview)

The core routes (the `DriverApi` route table, present wherever the mod runs) plus the
client-only `mc.debug.*` verbs, all carrying a visible `ToolSchema` — the driver
layer owns no hidden verb since `mc.test.yaml` retired with the YAML harness. The
only RPC-route-only verbs left belong to the testmod (`worlddriver.*`) and the
StageWright runtime (`mc.test.*`) and exist only while those are loaded. No total is
given here because it depends on what is loaded. Full per-method params + returns
are in **`references/methods.md`** — read it before composing an unfamiliar call.

| namespace | what's there |
|---|---|
| `mc.system.*` | `version`, `testOrigin`, `waitTicks` |
| `mc.observe.*` | `cursor`, `eventsSince`, `player`, `threats`, `boss`, `scene`, `map`, `container` (state snapshots) |
| `mc.action.*` | `runCommand`, `fill`, `placeMany` (world mutations, `returnEvents?`) |
| `mc.world.*` | `block` (read-only single-cell inspect), `snapshot`, `restore` (in-memory block-box save/restore for clean A/B trials) |
| `mc.query` | scan blocks / entities in a cube |
| `mc.events` | `emit` / `watch` / `unwatch` / `list` (server-side event channel + watchers) |
| `mc.wait.*` | `event`, `worldReady`, `condition`, `result` (long-poll, server-side blocking; `background?`) |
| `mc.recipe.*` / `mc.plan.acquire` | recipe `lookup`/`resolve`, full mine/farm/smelt/craft acquisition plan |
| `mc.client.screen.*` | `info`, `tree`, `close` (GUI introspection) |
| `mc.client.chat.*` | `send`, `history` |
| `mc.client.input.*` | `click`, `slotClick`, `mouseMove`, `typeText`, `replaceText`, `keybind`, `key`, `setHotbarSlot`, `slider` |
| `mc.client.*` | `player`, `scene`, `blocks` (client-authoritative reads), `overlays`, `screenshot` |
| `mc.bot.*` | `goto`, `mine`, `bunker`, `escape`, `craft`, `smelt`, `combat`, `equip`, `build`, `clearArea`, `farm`, `construct`, `sleep`, `follow`, `explore`, `runAway`, `lookAt`, `holdItem`, `useItem`, `attackEntity`, `elytraFly`, `playbook`, `waypoint`, `status`, `cancel`, `setting` |
| `mc.debug.*` | `pathChart`, `plan`, `replay` (client only: path-debug chart, read-only A* probe, archive replay) |
| `mc.script.eval` / `mc.skill` | JS snippet (one round-trip; **not sandboxed** unless `-Dworlddriver.sandbox=on`) + persistent skill library (`save`/`list`/`get`/`run`/`delete`) |

## Live event stream → Monitor (game-event notifications)

Want real-time push of in-game events (a bot died, a chat/system line, a mob death)
while something runs — the game-side complement to `live-screen-watch`? Don't
hand-roll a websocket poller; the ready-made pattern is **rpc.py driving
`mc.wait.event` in a loop**, seeded and chained by the cursor:

- `mc.observe.cursor` → the latest event seq (a bare integer). Seed with it once.
- `mc.wait.event {cursor, types?[], timeoutMs}` **long-polls**: it returns the moment
  a matching event arrives (`{events[], cursor, ms}`), or `{timedOut:true, cursor}` at
  the deadline. Either way you get a fresh `cursor` — feed it back in. That loop *is*
  the stream; no polling interval to tune, no missed events between calls.

**`event_tail.py`** (bundled next to `rpc.py`, pure Python — it imports rpc.py's
resolvers + `call` and holds ONE persistent socket) is that loop, ready to run. It prints
one compact line per event to stdout, so point a **Monitor** at it and each event becomes
a chat notification:

```
Monitor:  python3 <this-dir>/event_tail.py     (persistent: true)
```

Default types are `entity.death, chat.message, player.join, player.leave` — the
meaningful ones. Override with `AGENT_EVENT_TYPES` (a JSON array). **Keep `block.*` out**
of the tail — every mined/placed block fires one and floods the channel. A player death
surfaces two ways: an `entity.death` for the player entity *and* the vanilla death line
as a `chat.message` (`"X was slain by …"`) — the latter is usually the clearest signal.

Two related building blocks for conditions the raw event types don't cover:
- **Health-drop / low-HP alarm** (no such event type exists): set a server-side watcher
  with `mc.events {op:"watch", invoke:"mc.observe.player", field:"health", below:<hp>,
  emitAs:"bot.lowhp", everyMs:500}` — it emits `bot.lowhp` into the same stream when
  health crosses the threshold, so add that to `AGENT_EVENT_TYPES` and it rides the same
  Monitor.
- **One-shot "wait until X"** (not a stream): `mc.wait.condition` / `mc.wait.event` with
  `background:true` returns a `{waitId}` immediately; fetch later with `mc.wait.result`.

## Gotchas (learned live)

- **`completed:true` ≠ success.** Async bot methods (`goto`/`mine`/… with
  `awaitMs`) report `completed` when the process slot goes idle — a *no-path
  failure* also reports completed. Confirm via `mc.bot.status`:
  `status.<slot>.lastError` and `status.lastPath.{goalReached,finalCost}`, or
  re-`mc.observe.player`. (`finalCost` even tells you *which* move was used.)
- **Don't poll readiness with long curl/sleep loops** — it's slow and annoying.
  After a relaunch, probe `mc.system.version` once; it errors until up (~30–60s)
  then returns a fresh `uptimeMs`. If you must wait on the port, one loop is fine:
  `until ss -ltnp | grep -q ":$PORT"; do sleep 3; done` (Linux) — on Windows there
  is no `ss`, use `netstat -ano | grep -q ":$PORT.*LISTENING"` instead. Take `$PORT`
  from `<rundir>/worlddriver-rpc.port`, which the client writes with the port it
  actually got; 39801 is only the default and launchers do move it.
- **A port that never opens may be a client that never started.** The mod opens its
  ports at client init, so a client that hangs *before* mod loading looks exactly
  like a firewall or a wrong port. Tell them apart in the client's own log:
  zero `Found mod file` lines means the hang is upstream of every mod, and a thread
  dump of that JVM names the frame (a window that never maps is the usual one —
  the loader's early-display window is created before any mod is discovered). No
  amount of retrying the port helps, and nothing the mod can do reaches that far up.
- **Find the game window by PID, never by title.** A modpack can rename it — one
  ships as `Mium 麦吉克服务器 1.21.1`, with no "Minecraft" anywhere in it, so
  `xdotool search --name Minecraft` comes back empty and reads as "there is no
  window". The PID is not something a pack can change, and the RPC port names it —
  but take the port from the file the client itself writes, not from 39801: that
  default is overridable and one launcher here runs on 39811 to stay clear of a
  single-player instance. `ss -tlnp | grep ":$(cat <rundir>/worlddriver-rpc.port)"`
  → that PID → `xdotool search --pid <pid>`. (Usually `_NET_CLIENT_LIST` holds one
  window anyway, so enumerating is cheap.)
- **`mc.client.*` / `mc.bot.*` need a client.** On a dedicated server they error
  with "not available (client only …)". `mc.system/action/observe/query/wait`
  work server-side.
- **`mc.script.eval` runs on a worker thread**, neither the server nor the client
  thread. Every `Driver.invoke` hops onto the thread its route needs, so chaining
  *API calls* (observe→decide→act, `mc.bot.setting`, `mc.client.*`) is safe; reaching
  game objects directly through Java touches them off-thread, so don't. It is **not
  sandboxed** by default (full JVM access; `-Dworlddriver.sandbox=on` opts into a class
  filter) — never pass it source you would not run in a shell.
- **Clear leftover placed blocks between pathfinder trials.** A successful
  place/bridge/parkour leaves a *real* block in the saved world; a follow-up
  "negative" test (feature off → expect no path) is contaminated if it's still
  there. `setblock <cell> air` first.
- **Relaunch the client by PORT OWNER, not by name-pattern kill** (a `pkill -f`
  pattern matches its own shell → exit 144). Port-owner form: `ss -ltnp` → the PID
  in the last column → `kill <pid>` (Linux); on Windows `netstat -ano | grep
  ':39801.*LISTENING'` → trailing PID → `taskkill //PID <pid> //F` (Git Bash needs
  the doubled slashes). See the
  `reference_client_relaunch` memory and `into_world.py` for the title→world drive.

## Relationship to the other scripts here

`rpc.py` is the general-purpose, reusable client (one-shot / batch / `--jq`).
`into_world.py` and `react_smoke.py` (repo `scripts/`) use the same RPC socket
for the specific title→in-world flow; lean on them for client bring-up.
