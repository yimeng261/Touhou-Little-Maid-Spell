# worlddriver RPC — complete method reference

Every method the JSON-RPC websocket (`ws://127.0.0.1:39801/rpc`) accepts. Most
are also MCP tools (`mcp__worlddriver__*`) with `.`→`_` names (`mc.bot.goto` ⇄
`mc_bot_goto`); both go through one dispatcher (`DriverApi.route`), so behaviour is
identical. Which methods exist depends on what is loaded, so this page gives no
total to go stale: the core set is every `routes.put` / `putBodyVerb` in the
`DriverApi` constructor and is there wherever the mod runs; a game client adds the
three `mc.debug.*` path-debug verbs (`bot/debug/PathDebugBootstrap`); the testmod adds
`worlddriver.*`; the StageWright runtime adds `mc.test.*`. The core set and
`mc.debug.*` are all visible MCP tools: the driver layer owns no hidden verb any more
(`mc.test.yaml`, the last one, retired with the YAML harness). Every route is asserted
at boot to carry a schema (`DriverApi.requireSchemasFor`), so "route with no schema"
can't drift in. Hidden (RPC-route-only) verbs still exist as a mechanism —
`ToolCatalog.HIDDEN_TOOLS` for driver-owned ones, `registerVerb(..., .asHidden())` for
granted namespaces — and the live ones are the testmod's `worlddriver.*` scene verbs
and the StageWright harness's `mc.test.run` / `mc.test.reset` / `mc.test.input.*`,
present only when those are loaded. Over RPC a hidden verb works like any other
method — one of the reasons this skill exists.

Source of truth: `common/.../api/DriverApi.java` (the route table — the canonical
list of *which* methods exist), `common/.../mcp/catalog/*Tools.java` (visible MCP
schemas + inline param docs) and `common/.../mcp/ToolCatalog.java` (`HIDDEN_TOOLS`,
the RPC-only verbs), `common/.../bot/SettingsRegistry.java` (the canonical ordered
`mc.bot.setting` key list + its single-source schema) backed by
`common/.../bot/BotConfig.java` (the fields + their ranges),
`.../rpc/RpcServer.java` (envelope; binds `127.0.0.1` on port 39801, WS path
`/rpc`). When in doubt, grep `DriverApi.java` for the route then the matching
`*Tools.java` for its param list.

## Contents
- [Envelope & errors](#envelope--errors)
- [Availability (client vs server)](#availability-client-vs-server)
- [Async & `awaitMs`](#async--awaitms)
- [`mc.system.*`](#mcsystem) — version, testOrigin, waitTicks
- [`mc.observe.*`](#mcobserve) — cursor, eventsSince, player, threats, boss, scene, map, container
- [`mc.action.*`](#mcaction) — runCommand, fill, placeMany
- [`mc.world.*`](#mcworld) — block, snapshot, restore
- [`mc.query`](#mcquery) — block/entity scan
- [`mc.events`](#mcevents) — emit / watch / unwatch / list
- [`mc.wait.*`](#mcwait) — event, worldReady, condition, result
- [`mc.recipe.*` / `mc.plan.acquire`](#mcrecipe--mcplan) — recipe lookup/resolve, acquisition plan
- [`mc.client.screen.*`](#mcclientscreen) — info, tree, close
- [`mc.client.chat.*`](#mcclientchat) — send, history
- [`mc.client.input.*`](#mcclientinput) — click, slotClick, mouseMove, typeText, replaceText, keybind, key, setHotbarSlot, slider
- [`mc.client.*`](#mcclient-misc) — player, scene, blocks, overlays, screenshot
- [`mc.bot.*`](#mcbot) — goto, mine, bunker, escape, craft, smelt, combat, equip, build, clearArea, farm, construct, sleep, follow, explore, runAway, lookAt, holdItem, useItem, attackEntity, elytraFly, playbook, waypoint, status, cancel, setting
- [`mc.script.eval` / `mc.skill`](#mcscripteval--mcskill)
- [`mc.debug.*`](#mcdebug) — pathChart, plan, replay (client only)
- [`worlddriver.*`](#worlddriver-testmod-only--hand-built-scenes) — testmod scene verbs

---

## Envelope & errors
Request: `{"id": N, "method": "mc.x.y", "params": {…}}` (omit/`{}` params for parameterless).
Success: `{"id": N, "result": <any>}`. Error: `{"id": N, "error": "<string>", "code": <int>}` —
`error` is a plain string, **not** a JSON-RPC 2.0 error object, and there is no `jsonrpc`
version field; `code` beside it is the JSON-RPC 2.0 classification, the same the MCP
transport reports for the same failure. `id` is echoed, and is `null` only when the request
carried none or was too malformed to read. Many handlers don't throw — they return
`{ok:false, error:…}` in the result instead, so check `ok`, not just transport success.

The code table and the rules behind it are in
[`docs/guide/transports.md` § Error codes](../../../../docs/guide/transports.md#error-codes);
in short: `-32700` bad JSON (`parse: …`) · `-32600` not a usable request (not an object,
`method` missing or not a string) · `-32601` `unknown method: <name>` · `-32602` params
rejected by the schema or the route · `-32603` anything else the route threw · `-32001` the
server tick never started the task in time (`worlddriver.serverThreadTimeoutMs`, 8 s): withdrawn,
retry freely · `-32002` it started and may still apply: observe before retrying · `-32005`
refused without running (16 requests in flight on this connection, 64 RPC workers busy, or 32
background waits running): retry once an earlier call returns. The client bot's `mc.bot.*` verbs
hop onto the client thread instead (`worlddriver.clientThreadTimeoutMs`, 8 s) and time out with
the same `-32001`/`-32002`. MCP reports these three codes on `tools/call` as JSON-RPC error
envelopes, not as tool errors.

**Bot preconditions.** Every `mc.bot.*` verb that drives the player (all but `status`,
`cancel`, `setting`, `waypoint`, `playbook`) first checks that the bot can act, on every
transport, and answers `{ok:false, error, reason}` when it cannot — nothing is started, so
never treat a missing `started` as "in progress". `reason` is one of, in the order checked:
`no_player` (no world open; `error` names the screen the client is on), `loading` (the
level-loading screen is up), `dead` (dead or on the death screen — respawn first),
`paused` (singleplayer pause menu — `mc.client.screen.close`), `sleeping` (in a bed),
`chunk_unloaded` (the client has no chunk under the player yet). `error` always says what
to do about it. `mc.client.input.key` with no screen and no player answers the same shape
with `reason:"no_player"`.

## Availability (client vs server)
- `mc.client.*` and `mc.bot.*` require a **client** (a running game client). On a
  dedicated server they raise `… not available (client only …)`. `mc.debug.*` is
  registered only by a client, so on a dedicated server it is an unknown method.
- `mc.observe.threats` / `mc.observe.boss` are client-backed: they return
  empty/`{present:false}` on a dedicated server.
- `mc.system.*`, `mc.action.*`, `mc.world.*`, `mc.observe.scene` / `map` / `container`,
  `mc.query`, `mc.events`, `mc.wait.*`, `mc.recipe.*`, `mc.plan.acquire`
  work server-side; several also have a client-MCP fallback that reads
  LocalPlayer/ClientLevel when no server is attached (e.g. `mc.observe.player`
  then also returns `inventory`, `effects`, `time`, `hit`).

## Async & `awaitMs`
These return immediately with `{started:true}` and run as a bot process, and
accept `awaitMs`: `mc.bot.goto`, `mine`, `bunker`, `craft`, `smelt`, `combat`,
`build`, `clearArea`, `follow`, `explore`, `runAway`, `farm`, `sleep`, `construct`,
`elytraFly`. Pass `awaitMs:N` (1–600000) to block until the process slot goes idle
(it polls `mc.bot.status`), folding the final status in:
`{ok, started, awaited:true, completed:bool, ms, status:{…}}`. **`completed:true`
only means the slot went idle — a no-path *failure* also reports completed.**
Confirm real success via `mc.bot.status` `lastProcessEnd` (`{kind, error}`; `error` null
only when the task did what it was asked — a slot's `lastError` also holds success summaries
such as `"done (placed=…)"`; the auto-backfill never overwrites it; a `body:` status carries its
own) and `status.lastPath` (`goalReached`, `finalCost`), or
re-observe the player. **`mc.bot.escape` is also a
bot process but takes NO `awaitMs`** (its schema has only `targetY`) — fire it and
poll `mc.bot.status`/`mc.observe.player`. `mc.bot.equip` is **synchronous** (returns
its result directly, no `awaitMs`); `mc.bot.playbook` runs on a **background thread**
(poll `op:"status"`).

---

## mc.system.*
| method | params | returns / notes |
|---|---|---|
| `mc.system.version` | — | `{modid, version, uptimeMs, loadedFrom, loadedKind, builtAt?, builtMs?, sizeBytes?}`. Probe first; errors with "Unable to connect" until up. `version` is pinned and identical across builds — use `loadedFrom`/`builtAt` to tell whether the game got the build you just made. |
| `mc.system.testOrigin` | — | `{x,y,z}` canonical arena origin (default 0,200,0); the default search center for observe/query. |
| `mc.system.waitTicks` | `ticks` (req) | block ~ticks×50ms → `{waited, interrupted?}`. Refuses to run on the server thread. |

## mc.observe.*
| method | params | returns / notes |
|---|---|---|
| `mc.observe.cursor` | — | latest event seq `<integer>`; save and feed to `eventsSince`/`wait.event`. The seq never rewinds, not even across a world reload, so a saved cursor stays valid. |
| `mc.observe.eventsSince` | `cursor` (req), `types?[]`, `limit?` | events with `seq>cursor`; types: block.break/place/fill, entity.death, player.join/leave, chat.message, and the client's route events route.blocked / route.detour / route.exposed (see `mc.bot.goto`). limit default 256, max 4096. |
| `mc.observe.player` | `name?` | `{present, name, uuid, dimension, pos, blockPos, look, onGround, health, maxHealth, food, xpLevel, effects[], time, gameMode, mainHand, offHand, hotbar[], selectedSlot, armor}`; client fallback adds `inventory, saturation, hit`. |
| `mc.observe.threats` | `radius?` (1–64, dflt 24) | `{threats:[{id,type,pos,distance,hostile,canSeeMe,facingMe,charging,creeperSwell,threat}], incomingProjectiles:[{id,type,pos,vel,willHit,ticksToImpact}]}`. Client-backed (empty on dedicated server). `threat` is a 0–1 priority score. |
| `mc.observe.boss` | `radius?` (1–256, dflt 64) | `{present, type?:"ender_dragon"\|"wither", health, maxHealth, healthPct, pos, distance, phase, crystals:[{id,pos,distance,caged}], …}`. Client-backed → `{present:false, crystals:[]}` on a dedicated server. |
| `mc.observe.scene` | `center?`, `radius?` (1–32, dflt 12), `render?:"summary"\|"map"`, `overlays?:["height"\|"sight"\|"mobDensity"]`, `route?:{sight?:{of,range?,eye?}, mobs?:{types?,cluster?:{count,radius}}}` (the goto keys; default ranged hostiles, cluster radius 6) | `{present, center, radius, authority:"server", hazardSummary:{lethalCount, cornered, safeFleeStep?}, truncated?, rows?:[string], legend?:{}, centerY?/minY?/maxY?, sight?:{observers:[{id,type,name?}], rows:[string], exposedCells, budgetExhausted?, snapshotTruncated?}, mobDensity?:{mobs, radius, count, rows:[string], maxDensity, clusteredCells, snapshotTruncated?}}`. The two overlay grids align with the map rows: one glyph per cell, `0-9`/`+` counts, `.` no footing, `?` past the ray budget — computed by the route planner's own `SightExposure` / `MobCluster` over a fresh entity scan. Server-side; works headless in GameTest. |
| `mc.observe.map` | `center?`, `radius?` (1–24, dflt 12), `plane?:"xz"\|"xy"\|"zy"` (dflt xz), `height?` (1–24, dflt 7) | `{present, plane, center, radius, width, height, threats, legend, map:"<newline-joined grid>"}`. Server-side compact ASCII hazard/mob overlay. |
| `mc.observe.container` | `pos?` | with `pos`: BlockEntity slots; without: open menu. `{present, type?, slots?:[{id,count}|null]}`. Furnace slots 0=input,1=fuel,2=output. |

## mc.action.*
All accept `returnEvents?:bool` → response also carries an `events[]` array (saves a cursor/eventsSince round-trip).
| method | params | returns / notes |
|---|---|---|
| `mc.action.runCommand` | `cmd` (req) | run a vanilla command at operator level → `{ok, via:"fast-path"\\|"brigadier", success, value, feedback[], error?}`. `success`/`value` = Brigadier result callback (`execute if entity` → match count); `feedback` = collected chat output (`data get` → NBT text); `ok:true,success:false` = dispatched but command failed. `setblock`+absolute int coords gets a fast-path emitting block.place/break. |
| `mc.action.fill` | `from,to,type` (req) | fill an AABB in one tick → `{ok, placed, error?}`. Volume cap 32768 (32³). |
| `mc.action.placeMany` | `blocks:[{pos,type}]` (req) | place ≤4096 cells in one tick → `{ok, placed, skipped}`. |

## mc.world.*
In-memory block-box save/restore — the clean way to A/B a pathfinder/build trial without contaminating the saved world. Cap 32768 (32³) volume; up to 64 snapshots retained.
| method | params | returns / notes |
|---|---|---|
| `mc.world.block` | `pos` (req), `nbt?` (dflt false) | read-only single-cell inspection → `{pos, type, state?, light:{block,sky}, blockEntity?}`. `state` = blockstate property map (omitted when property-less); `nbt:true` adds block-entity NBT as SNBT (null when none). |
| `mc.world.snapshot` | `from,to` (req), `id?`, `blockEntities?` (dflt true) | capture a box into the in-memory store → `{ok, id, from, to, blocks, nonAir, blockEntities}`. Auto-generates `id` if omitted. |
| `mc.world.restore` | `id` (req), `discard?` (dflt false), `returnEvents?` | restore a snapshot verbatim → `{ok, id, restored, blockEntities}`. Emits a `world.restore` event; frees the snapshot if `discard:true`. |

## mc.query
| method | params | returns / notes |
|---|---|---|
| `mc.query` | `q:"blocks"\|"entities"` (req), `center?`, `filter?:{in_radius?, type?, is_hostile?, is_living?}`, `select?:[…]` | scan a cube (Chebyshev `in_radius`; blocks default 0, the centre cell alone, so pass it for anything wider; default 16 for entities). Blocks: `in_radius` max 15 (31³ cells, inside the 32768-cell budget of `mc.action.fill`; larger → error), and only loaded chunks are read — a cube touching an unloaded chunk is an error naming it, never a chunk load. `filter.type` = one exact id for both blocks (`#tag` ok) and entities (bare path → `minecraft:`). Blocks → `[{pos,type,state?}]` (`state` = blockstate property map, omitted when property-less); entities → `[{pos,type,uuid,id,health?,effects?}]` (`effects` = `[{id,amplifier,durationTicks}]`, living only; `is_living` filters item/orb rows). `select` projects fields; unknown select keys are rejected with an error. Client fallback (no server attached) returns the same flat array. Its entity rows honour the same `filter`/`select` and add `{hostile,maxHealth,distance}`, which `select` may also name. Its block scan reads `ClientLevel` (centred on the player when `center` is omitted) under the server's rules: `in_radius` defaults to 0 and above 15 is rejected with the same error, `select` takes the same keys, and a cube touching a chunk the client has not loaded is refused with the same unloaded-chunk error. |

## mc.events
Server-side event channel: emit your own events and set up server-side **watchers** that poll an arbitrary method on a rising-edge predicate and emit when it fires (a building block for `wait.condition`-style automation without a client long-poll).
| method | params | returns / notes |
|---|---|---|
| `mc.events` | `op:"emit"\|"watch"\|"unwatch"\|"list"` (req); emit: `type`,`data?`,`pos?`; watch: `invoke`,`params?`,`field?`,`emitAs?`,`everyMs?`,`once?`,`value?`/`above?`/`below?`; unwatch: `id` | `emit`→`{ok,seq,type}`; `watch`→`{ok,watching,id,emitAs,everyMs}` (an `invoke` that is not a registered method is a `-32602` error at watch time); `unwatch`→`{ok,removed}`; `list`→`{watchers:[…],count}`. |

## mc.wait.*
Long-poll primitives (block server-side; respect `timeoutMs`, default 5000/30000, max 120000; `pollMs`). Pass `background:true` to return a `{waitId}` immediately and fetch the result later with `mc.wait.result`. At most 32 background waits run at once; one more is refused with code `-32005` (message `busy: …`), not queued.
| method | params | returns / notes |
|---|---|---|
| `mc.wait.event` | `cursor` (req), `types?[]`, `limit?`, `timeoutMs?`, `pollMs?`, `background?` | returns as soon as ≥1 matching event arrives, else `{timedOut:true}`. `{events[], timedOut, cursor, ms}`; chain `cursor`. |
| `mc.wait.worldReady` | `timeoutMs?`, `pollMs?`, `background?` | block until client has player+world → `{ready, ms, info:{hasScreen,worldOpen,hasPlayer,…}}`. No server needed. `background:true` returns a `{waitId}` at once (fetch via `mc.wait.result`). |
| `mc.wait.condition` | `invoke` (req), `params?`, `field?`, `value?`, `timeoutMs?`, `pollMs?`, `background?` | call `invoke(params)` every `pollMs`, walk dotted `field` (e.g. `slots.2.count`) into the result, succeed when truthy (or deep-equals `value`) → `{satisfied, value, ms}`. |
| `mc.wait.result` | `waitId` (req), `consume?` (dflt true) | fetch the result of a `background:true` wait → `{pending:true}` while still running, else the full original result (`satisfied`/`timedOut`/`value`/`events`/`ms`/…). `consume:false` leaves it readable again. An unknown id (never issued, already consumed, or evicted past the 64 newest unread results) is a `-32602` error, not pending. |

## mc.recipe.* / mc.plan
Crafting/acquisition planning off the live recipe table — `resolve` expands a craft tree to leaf items; `plan.acquire` goes further and routes each missing leaf to mine/farm/smelt/craft. Server-side.
| method | params | returns / notes |
|---|---|---|
| `mc.recipe.lookup` | `result?`, `ingredient?`, `limit?` (1–200, dflt 20) | search recipes by result and/or ingredient → `{ok, count, recipes:[{id,type,station,result:{id,count}, ingredients:[{slot,accepts:[id…],tag?}], width?,height?,pattern?}]}`. |
| `mc.recipe.resolve` | `target` (req), `count?` (1–4096, dflt 1), `have?` ({id→count}) | expand the recipe tree into an ordered craft plan → `{ok, target, count, steps:[{craft,count,recipe,station,from}], missing:[{item,count}], stations_needed:[…]}`. |
| `mc.plan.acquire` | `target` (req), `count?` (1–4096, dflt 1), `have?` ({id→count}) | full acquisition plan: route every missing leaf to an action → `{ok, target, count, feasible, steps:[{action:"mine"\|"farm"\|"smelt"\|"craft", item, count, blocks?,input?,station?,from?}], unobtainable:[{item,count}]}`. |

## mc.client.screen.*
| method | params | returns / notes |
|---|---|---|
| `mc.client.screen.info` | — | cheap probe → `{hasScreen, worldOpen, hasPlayer, overlayActive, windowActive, mouseGrabbed, type?, title?, width?, height?, causeOfDeath?}`. Call before other `mc.client.*`. `causeOfDeath` set on a DeathScreen. `windowActive`/`mouseGrabbed` false = a mod's key handler will refuse the keystroke however you send it; on a shared desktop the game window loses focus to whatever the human is doing. |
| `mc.client.screen.tree` | — | widget tree → `{type,width,height,children:[{type,x,y,width,height,visible,active,focused,message?,key?,selected?,children?}]}`; `message` is the label in the player's language, `key` its translation key when it has one (`deathScreen.respawn`), the same in every language; pick click targets from this — the coordinates are right for modded, self-drawn screens too. A widget that throws (modded screens do) leaves `error` on its own node and `slotsError` on the root, never a blank answer. ⚠️ **Structure, not paint**: `selected` is only what an `AbstractSelectionList` reports. A widget that draws its own selection (a highlighted card) leaves this tree byte-identical before and after the click that chose it — read that with a screenshot, not from here. |
| `mc.client.screen.close` | — | `setScreen(null)` → `{ok}`; always succeeds. |

## mc.client.chat.*
| method | params | returns / notes |
|---|---|---|
| `mc.client.chat.send` | `text` (req), `awaitReplyMs?` | send chat / command (leading `/`) via the connection. `awaitReplyMs>0` blocks for lines arriving AFTER the send (+~150ms settle window for multi-line feedback) → `{ok, kind, length, reply?:{seq,kind:"system"\|"player",text,self}, replyExtra?[], replyTimeout?, replyMs}`. The server's echo of your own chat is marked `self` and is never returned as a reply, though it stays in the history. Busy servers can interleave unrelated lines — match on text/kind (command feedback = `system`). Boundary: replies the client renders locally (client-command feedback, chat validation errors, a mod writing straight to the chat HUD) never cross the packet layer and will `replyTimeout`. To read back a server-side command, prefer `mc.action.runCommand`, which returns `feedback[]` directly. |
| `mc.client.chat.history` | `limit?`, `sinceSeq?` | chat and system lines captured at the packet layer (action-bar excluded; lines another mod cancelled are still captured), monotonic seq, 512-line buffer → `{ok, count, nextSeq, messages:[{seq,kind,text,ageTicks,self}]}` newest-first. `kind:"player"` = chat carrying a real sender profile (`self:true` = your own echo); disguised chat (console `/say`, a command block) = `system`, identically on both loaders. limit default 50, max 256. To poll, remember `nextSeq` and pass it as `sinceSeq` next time to get only the new lines (`seq>=sinceSeq`). Boundary: client-local lines that never cross the packet layer (client-command feedback, validation errors, a direct `ChatComponent.addMessage` call) are visible on screen but invisible here. |

## mc.client.input.*
Logical screen coords (post-GUI-scale). Reflection-based, work under Xvfb.
| method | params | returns / notes |
|---|---|---|
| `mc.client.input.click` | `x,y` (req), `button?` | 0=L,1=R,2=M → `{ok, handled}` (handled=false on empty space). |
| `mc.client.input.slotClick` | `slot` (req), `button?`, `type?` | container click via real packet; type: pickup/quickMove/throw/swap/clone/pickupAll/quickCraft → `{ok, slot, button, type}`. |
| `mc.client.input.mouseMove` | `x,y` (req) | move cursor + fire hover → `{ok, scale, refl, wx, wy}`. |
| `mc.client.input.typeText` | `text` (req) | charTyped per codepoint into focused widget → `{ok, typed, length}`. Appends; use `replaceText` to overwrite. |
| `mc.client.input.replaceText` | `text` (req), `match?` | atomically replace a text box's whole contents → `{ok, value, previous}`. Targets the focused box, else the box whose text/hint matches `match`, else the sole box. |
| `mc.client.input.keybind` | `name?`, `action?` | drive a key mapping BY NAME — the only way to reach one bound with a modifier (`ALT+Y`), since that match reads the real keyboard and a synthesized ALT is not there. `name` = mapping id (`key.inventory`) or a substring of the id or title; omit it to list every mapping → `{ok, count, keybinds:[{name,title,key,boundTo,category,down}]}`. action press\|release\|click(default, released next tick) → `{ok, name, title, boundTo, action, clicks, down, released, rawEvent, windowActive, mouseGrabbed, screenAfter}`. Drives the mapping AND sends a raw key event (mods split into pollers and event subscribers); for the latter the binding's modifier is cleared for that event and restored after, since their test reads the physical keyboard. `windowActive`/`mouseGrabbed` false = the mod may refuse it, not that it was lost — but they are not a precondition for vanilla's own bindings. |
| `mc.client.input.key` | `key` (req), `action?`, `route?`, `modifiers?` | synth key (ENTER/ESCAPE/TAB/BACKSPACE/DELETE/arrows/F1..F25/A..Z/0..9); action press\|release\|click(default — release lands next client tick, re-routed from what the press left open). route auto(default: open screen, else keybinds)\|keybind(reaches keybinds under a screen)\|screen(refuses with none open) → `{ok, key, code, action, route, via, pressed, released, screenAfter}`, plus `pressHandled`/`releaseHandled` on the screen route and `releaseVia` when the press changed the screen under it. **`released`/`pressed` say the half went out; `*Handled` says the screen consumed it** — screens almost never consume a release, so `releaseHandled:false` is normal. A screen that stays open after ESC usually means something inside it ate the key: with the command-suggestion popup up, `SuggestionsList` takes ESC to close itself and `ChatScreen` never sees it (vanilla does this to a human too — press ESC twice, or use `mc.client.screen.close`). |
| `mc.client.input.setHotbarSlot` | `slot` (req, 0–8) | select hotbar slot (sends carried-item packet) → `{ok, slot, previous}`. |
| `mc.client.input.slider` | `match?`, `index?`, `fraction?` (0–1) | omit `fraction` to READ → `{ok, mode:"read", sliders:[{index,label,value}]}`; pass it to SET a slider (by `match`/`index`) → `{ok, mode:"set", label, value, previousLabel, previousValue}`. |

<a id="mcclient-misc"></a>
## mc.client.* (player / scene / blocks / overlays / screenshot)
Client-authoritative reads — diff against the server-side `mc.observe.*` to spot client/server desync (the reason these exist). All require a client.
| method | params | returns / notes |
|---|---|---|
| `mc.client.player` | — | client-authoritative player snapshot from LocalPlayer → `{present, name, uuid, dimension, pos, look, onGround, eyePos, pose, inWall, inWater, underWater, crouching, eyeBlock, feetBlock, health, maxHealth, food, saturation, xpLevel, effects:[{id,amplifier,durationTicks}], time, gameMode, selectedSlot, mainHand, inventory:[{slot,id?,count?}], hit}`. |
| `mc.client.scene` | — | client hazard/threat blackboard → `{present, pos, health, food, dayPhase:"DAY"\|"DUSK"\|"NIGHT"\|"DAWN", skyExposed, exposedAtNight, cornered, lethalCount, rows?}`. |
| `mc.client.blocks` | `center?`, `filter?:{in_radius? (0–16, dflt 4), type?}` | client-authoritative block scan (Chebyshev radius around player/center; air omitted) → `{blocks:[{pos,type,state?}], center, radius, unloaded?}` (`state` = blockstate property map, omitted when property-less; `unloaded` = `[{x,z}]` chunks the client has not loaded, whose cells read as air, omitted when there are none). `type` accepts `#tag` selectors. `in_radius:0` scans the centre cell alone; omit it for the default 4. |
| `mc.client.overlays` | `tutorial?`, `toasts?` | both default true (`{}` clears all): kill tutorial toasts + toast queue → `{ok, tutorial?, toasts?}`. |
| `mc.client.screenshot` | `maxWidth?`, `maxHeight?`, `format?:"png"\|"jpeg"`, `quality?` | framebuffer capture (aspect-preserving downscale). Over RPC → `{format, width, height, frame, frameWaited, windowActive, fps, base64}`. The capture waits (≤1s) for a frame drawn **after** the request, so what you just did is in it; `frameWaited:false` means none was drawn and you got the leftover. ⚠️ `fps>0` only rules out a stopped renderer — at 15 fps a frame is up to 66 ms old, several round-trips. Compare `frame` to tell two captures apart: same number = same image, whatever the picture shows. |

## mc.bot.*
Movement/automation processes. The async ones take `awaitMs?` — see [Async](#async--awaitms).
All but the reads refuse with `{ok:false, error, reason}` when the bot cannot act — see
[Bot preconditions](#envelope--errors).

`body?` names the bot a verb drives: `self` (default, this client's player) or an id from
`mc.bot.status` `bodies`. It is on `goto`, `cancel`, `status`, the verbs that start a process,
`lookAt`, `holdItem`, `useItem` and `attackEntity`; `equip`, `setting`, `waypoint` and `playbook`
reject it. Another bot runs one process on the server tick with no reflexes, and its replies carry
`body`. An unregistered id is `unknown_body`, a bot outside loaded chunks `chunk_unloaded`, a dead
one `dead`; an NPC answers the hand verbs with `no_hands`, and a process that needs hands ends on its
first tick with `no_hands` in the slot. On another bot the hand verbs refuse out of reach where the
client's click is silently ignored, `useItem` on an entity answers `menu` instead of `screen`, and
`combat`'s `force` lifts nothing.

| method | params | returns / notes |
|---|---|---|
| `mc.bot.goto` | **one goal**: `pos?`/`xz?`/`y?`/`block?`(+`radius?` scan 1–64)/`entity?`/`entityId?`/`direction?`(+`distance?`)/`waypoint?`/`axis?`; **goal mods**: `near?`, `goalMode?:"in"\|"two"\|"adjacent"`, `strict?`, `invert?`; **`route?`** (one object, every key optional): `via:[[x,y,z],…]` waypoints in order; `mode:["walk"\|"swim"\|"dive"\|"fly"]` (default walk+swim; no swim/dive → never enters water; `fly` alone → delegated to `mc.bot.elytraFly`, reply `slot:"elytra"`, or `slot:"goto"` when no usable elytra makes it walk); `break:"never"\|"allow"\|"prefer"`; `place:"never"\|"allow"`; `parkour:bool`; `risk:"safe"\|"normal"\|"bold"` (safe = mob berth + 3-mob cluster forbidden + ranged line-of-sight priced; normal = mob berth only if the `avoidMobs` setting; bold = terrain danger only); `yRange:{min?,max?,hard?,weight?}`; `hug:{what:"shore",weight?}`; `leash:{center:[x,y,z]\|[x,z],entity?,radius,hard?,weight?,axis?:"xz"}`; `regions:[{shape?:"box"\|"sphere",min,max\|center,radius,mode?:"forbid"\|"avoid",penalty?}]`; `mobs:{radius?,rangedRadius?,penalty?,cluster?:{count,radius,mode,penalty},types?}`; `sight:{of:"ranged"\|"hostile"\|"players"\|[ids],range?,mode?,penalty?,eye?}`; `corridor:{points:[[x,y,z],…],radius?,mode?,penalty?}`; `requireTool:id`; **look before walking**: `plan?:true` (client only) previews the route without walking → `{ok, started, slot:"plan", planId, goal}` and the result lands in the `plan` slot of `mc.bot.status` (`awaitMs` waits on it): `{planId, ok, reached, cells, cost, legs, segments:[{leg?,from,to,risk:{exposedTo:[{id,type,cells}],nearestMob?,regions}}], detourRatio, bestEffort, sightBudgetExhausted, snapshotTruncated, expanded, ms, path?}`; `includePath?:bool` adds the cells; `plan:"score"` prices the caller's own `route.corridor.points` line without a search (synchronous, same segment shape); `planId?` walks a previewed route (kept 60 s, last 8) → `{…, adopted, adoptReason?}` — a preview that was bestEffort, expired, or whose start the bot left (more than 4 cells) is not adopted and the goto searches normally, saying why; `awaitMs?`; `body?` (dflt `self`, else an id from `mc.bot.status` `bodies`) | pathfind+walk. `{ok, started, goal, via?, body?, awaited?, completed?, ms?, status?}` or `{ok:false, error}` (a bad route field reads `route.<field>: …`). Another bot walks on the server tick with one process and no reflexes: it refuses `waypoint`, `plan` and `planId`, and `route.requireTool` unless it is a player; an unregistered id is `{ok:false, error, reason:"unknown_body"}`, a bot outside loaded chunks `chunk_unloaded`, a dead one `dead`; `awaitMs` polls that bot's slots. A goto with route conditions also pushes `route.blocked` (`{reason:"constraint:<Name>"\|"budget"\|"terrain", bestEffort, cells, expanded, leg}` when a search is best-effort), `route.detour` (`{ratio, cells, tax?, taxCost?, leg}` past the `detourAlarmRatio` setting) and, with `route.sight`, `route.exposed` (`{observer:{id,type,name?}, cells, firstCell, routeCells, leg}` for an observer the previous plan was not seen by); debounced per (event, culprit) by `routeEventCooldownTicks`. `leash{center:[x,z],radius:1,hard:true,axis:"xz"}`+vertical goal = reliable-ascent pillar / dig-to-Y; `hug`+`mode:["walk"]` = hug the shoreline; `mode:["dive"]` = planned surface dive to an underwater goal. The old top-level `avoid/preferY/leash/hugShore/forbidParkour/capability/yFloor/yCeil/leashHard/column/forbidWater/forbidDig/requireTool/dive` are gone (hard cut). |
| `mc.bot.mine` | `blocks:[id]` (req), `quantity?` (1–256), `radius?` (1–64), `awaitMs?`, `body?` | mine matching blocks then collect drops. `broken` counts breaks, not inventory. `blocks` accept `#tag` selectors. |
| `mc.bot.bunker` | `depth?` (1–5, dflt 2), `awaitMs?`, `body?` | dig straight down and seal the roof for a panic shelter; needs hand-mineable blocks below → `{ok, started, depth}`. |
| `mc.bot.escape` | `targetY?` (-64–320, dflt current Y+32), `body?` | carve a staircase up the driest dry wall out of a pit/shaft (inverse of `bunker`; sidesteps A*). **No `awaitMs`** — needs `allowBreak` ON + a solid non-falling wall → `{ok, started, targetY}`; poll status. |
| `mc.bot.craft` | `item` (req), `count?` (1–256, dflt 1), `awaitMs?`, `body?` | resolve the recipe tree and craft (auto-uses/needs a crafting table for 3×3) → `{ok, started, item, count}`; watch `status.craft`. |
| `mc.bot.smelt` | `item` (req), `count?` (1–256, dflt 1), `fuel?`, `awaitMs?`, `body?` | smelt in a furnace; auto-finds fuel or uses `fuel` → `{ok, started, item, count, fuel}`; watch `status.smelt`. |
| `mc.bot.combat` | `mode?:"engage"\|"defend"\|"kill"` (dflt engage), `target?:{id}\|{type}`, `force?`, `awaitMs?`, `body?` | close to range and land cooldown-gated hits → `{ok, started, mode, targetId?, targetType?}`; watch `status.combat` (`{active,swings,wellTimed,crits,kills,lastError?}`). Refuses to enter at HP≤`combatFrailThreshold` (dflt 6) → `lastError:"frail-abort"`; `force:true` fights anyway. |
| `mc.bot.equip` | `profile?:"best"\|"combat"\|"armor"` (dflt best), `armorOnly?` (dflt false) | **synchronous**: score armor tier+enchants, swap via inventory clicks → `{ok, profile, equipped:[ids], loadout:{head,chest,legs,feet,mainHand}, lowDurability:[ids], missing:[slots]}`. |
| `mc.bot.build` | `origin` (req), `schematic?:{w,h,d,palette[],data[[dx,dy,dz,idx]]}` or `schematicBase64?` (Sponge .schem), `awaitMs?`, `body?` | place a schematic bottom-up; cap 4096; failures skip+count. |
| `mc.bot.clearArea` | `from,to` (req), `fill?:id` or `replace?:{from,to}`, `awaitMs?`, `body?` | clear/fill/replace an AABB (cap 4096); needs a block in inventory for fill/replace. |
| `mc.bot.farm` | `from,to` (req), `crops?:[id]`, `replant?`, `awaitMs?`, `body?` | harvest+replant wheat/carrot/potato/beetroot over a field → `{ok, started, from, to, area, volume, crops, replant}`; volume (x·y·z) capped at 4096, and a bigger box or a Y outside the world is a `-32602` argument error. |
| `mc.bot.construct` | `mode:"tower"\|"bridge"` (req); tower: `height?` or `targetY?`; bridge: `direction?`,`distance?`; `block?`, `awaitMs?`, `body?` | pillar up / sneak-bridge forward. |
| `mc.bot.sleep` | `pos?`, `radius?`, `awaitMs?`, `body?` | find+enter nearest bed (vanilla night/safety gates). |
| `mc.bot.follow` | `entityType?` or `name?` (≥1 req), `radius?` (1–16), `maxIdleTicks?`, `giveUpTicks?` (0–100000, default 600; 0 = never), `awaitMs?`, `body?`; `route?` = the same object as `mc.bot.goto` minus `via`, and with `mode:["fly"]` / `leash.entity` rejected (a follow already tracks its entity) | follow an entity; re-aims whenever it changes block, ends `unreachable` after 5 failed replans in a row, or after `giveUpTicks` in which it neither reaches the standoff nor gets a block closer than its closest approach (a quarry in a pen it cannot enter, or one that outruns it). |
| `mc.bot.explore` | `centerX,centerZ` (req), `maxChunks?` (1–64), `awaitMs?`, `body?` | spiral to unvisited chunk centers. |
| `mc.bot.runAway` | `from?`, `minDist?` (4–64), `awaitMs?`, `body?` | flee to a point ≥minDist from `from`/player (hazard-aware). |
| `mc.bot.lookAt` | `pos?` or (`yaw`+`pitch`), `body?` | aim view; instant, or a 'look' process if `smoothLook` is on → `{ok, yaw, pitch}`. |
| `mc.bot.holdItem` | `item` (req; bare name gets `minecraft:`), `body?` | **synchronous**: put that item in the main hand — selects its hotbar slot, or swaps it up from anywhere in the 36-slot inventory → `{ok, held}` (`held` = id actually in hand afterwards), or `{ok:false, error:"not in inventory: <id>", held}`. The prelude to `useItem` (hold the bucket / flint and steel / chosen food first). |
| `mc.bot.useItem` | `pos?`, `entityId?`, `face?`, `hand?:"main"\|"off"`, `lookAt?`, `sneak?`, `body?` | right-click held item, **three modes**: no `pos`/`entityId` = use in air (eat/throw/draw bow); +`pos` = use on a block face (place/bucket/bonemeal/shears); +`entityId` = use ON an entity (mount with EMPTY hand, trade, shear/milk/feed, leash — `entityId` wins over `pos`). `sneak` = entity-mode shift-interact. Synchronous → `{ok, hand, result, consumed}` (+`pos,face` in pos-mode; +`entityId,type,distance,riding,screen` in entity-mode). Out-of-reach rejected server-side (check `distance`). |
| `mc.bot.attackEntity` | `entityId` (req), `body?` | one left-click attack via the game mode (server applies damage/cooldown). Out-of-reach silently ignored. |
| `mc.bot.elytraFly` | `pos?`, `yaw?`, `pitch?`, `reactive?`, `fireworks?`, `fireworkEveryTicks?` (5–400), `ticks?` (1–20000), `stopXZDist?`, `groundFallback?`, `near?` (0–64), `awaitMs?`, `body?` | elytra glide to a target (needs to already be airborne). With `pos` and no `pitch` → reactive sim-lookahead flight + firework boosts; `pitch` pins a fixed-heading glide; no `pos` → glide on the current heading. No usable elytra + `groundFallback:true` falls back to the pathfinder. → `{ok, started, mode:"reactive"\|"goal"\|"glide"\|"groundFallback", pitch?, fireworks?}`. |
| `mc.bot.playbook` | `name?:"dragon"\|"wither"` (any hot-reloadable `[a-z][a-z0-9_]*` playbook body), `op?:"start"\|"status"\|"cancel"` (dflt start), `summon?` (wither), `maxRounds?`, `budgetMs?` (min 1, dflt 600000) | **background thread**: run a boss-fight Rhino playbook. `start`→`{ok, started, name}`; `status`→`{ok, active, name?, aborting, lastResult?, lastError?}`; `cancel`→stop. One at a time. |
| `mc.bot.waypoint` | `op:save\|get\|list\|delete\|clear` (req), `name?`, `pos?` | in-memory named positions (no disk); use names in `goto{waypoint}`. |
| `mc.bot.status` | `body?` | every process slot + `lastPath:{expanded,ms,goalReached,finalCost,pathLen}` + `bodies:[{id, kind, entityId?, pos?, busy}]`, the other bots `body` can name (`player:<name>` from `/worlddriver server spawn <name>`, `npc:<name>` from the testmod). The primary "why isn't it moving" probe. On a dedicated server, with no client bot, only `{bodies}`. With `body`: that bot's `{id, busy, activeProcess?}` and its slots, or `unknown_body`. |
| `mc.bot.cancel` | `process?` — one of `all, goto, mine, craft, smelt, combat, builder, follow, explore, runAway, look, elytra, escape, bunker, sleep, replay, retreat, duskSecure`; `body?` | stop processes, release keys, leave `lastError='user-cancel'`. Default `all`. Besides process kinds, a reflex chain's own name (`retreat`/`duskSecure`/`bunker`/`combat`) targets that chain's internal episode, and a process KIND also reaches a process held inside a reflex chain. → `{ok, cancelled}` naming what was cancelled, or `{ok:false, reason:"no-active-target", requested}`. Another bot holds one process and no chains, so a named cancel matches its kind or nothing; an unregistered id is `unknown_body`. |
| `mc.bot.setting` | many keys (empty=read all) | read/write tuning + reflex/Baritone toggles → `{ok, settings:{…}, applied?, rejected?, inert?}`. See below. |

### `mc.bot.setting` keys
Single source: **`SettingsRegistry.java`** — the ordered `HAND` list (canonical key
names, some with a `dotted.name` ⇄ `botConfigField` remap like
`walker.repathEveryTicks`→`walkerRepathEveryTicks`) **plus a reflective pass that
auto-includes every `public static volatile` primitive/String field of
`BotConfig`.** So the *field is the schema*: adding a settable `BotConfig` field
makes it a valid key with no second edit. Ranges live in **`SettingsDocs.java`**: a
key's row opens with `[min,max]`, that row is the key's schema description, and the
write path rejects any number outside it (an aliased field's own name, e.g.
`walkerRepathEveryTicks`, is held to its alias's range).
The set is large — **on the order of 200 keys** (currently ~120 boolean, ~70
numeric, plus a handful of list/string keys); the great majority are `walker*` /
`pathfinder*` movement-research toggles.

**Read the live full set with `mc.bot.setting {}`** (empty params ⇒ read-all → every
current key and its value) — that is the authoritative, never-stale list. The tables
below are a **curated highlight** of the commonly-used keys, not the whole surface;
grep `SettingsRegistry.HAND` for the canonical ordered names.

Out-of-range numeric keys land in `rejected`, applied ones in `applied`; an
**unknown** key is **rejected loudly, all-or-nothing** (post-#280: the schema is
CLOSED and validated at `route()` from `SettingsRegistry`, so a call carrying ANY key
not in the registry errors and applies NOTHING — never a silent drop). The MCP-client
stale-schema caveat still holds (see the SKILL.md note — the harness's frozen MCP tool
schema strips a brand-new key *client-side* before it reaches the mod, which is the #1
reason to drive a just-added setting over RPC).

**Booleans** — reflexes & toggles: `paused, autoEat, autoRespawn, autoRetreat,
autoBunker, autoFight, autoDodge, autoShield, autoHeal, autoTotem, autoEquip,
combatCrit, autoSwim, antiSuffocate, autoTool, autoBackfill, autoSecureAtDusk,
avoidDanger, avoidMobs, smoothLook`. Pathfinder/walker move toggles: `allowParkour4,
allowBreak, allowPlace, allowParkourPlace, allowSwimEscapeBreak, allowSwimEscapePlace,
allowWaterBucketFall, waterBucketScoop, collisionAwarePathing, pathfinderCacheEnabled,
pathfinderGoalField, pathfinderFrontierCommit`. Debug: `walkerDebug, elytraDebug,
pathDebug, pathArchive, pathChartAutoDump`.

Beyond those, `SettingsRegistry.HAND` carries a **large family of per-move walker/
pathfinder research flags** (all boolean) — e.g. `walkerVerticalResync,
walkerLevelRiserJump, walkerParkourAscendHold, walkerSteepDescentLatch,
walkerDescentFlipHold, walkerStepUpCrestReach, walkerWaterWalkReach, walkerTangentAim,
walkerArcLengthWedge, walkerArcProgressWedge, walkerFellBelowAlign,
walkerFutileBankDigRelease, walkerBankDigForwardExit, walkerFloatingBankFollow,
walkerFasterChurnRepath, walkerDeepWaterFloatBeeline, walkerVineFreeHangClimb,
walkerVineDescentDrop, walkerAscendMovement, walkerEdgeBrakeVelocityProbe,
craftReclaimTable` and parkour/water forbid-gates `pathfinderForbidParkourIntoDeepWater,
pathfinderForbidParkourFromFloatingWater, pathfinderForbidParkourOverWaterGap,
pathfinderParkourAscendNeedRunway, pathfinderFloatingSurfaceCross,
pathfinderVineOverWaterTax, pathfinderPadOverWaterTax, pathfinderPadClusterTax`. This
list is illustrative — read live for the current complete set and defaults.

**Numbers (integer, [min,max])** — survival/combat: `autoEatFoodThreshold[0,20],
bunkerMinHostiles[1,10], bunkerDepth[1,5], bunkerTriggerRadius[1,16],
hazardGridRadius[4,32], hazardGridDecimateTicks[1,20]`. Pathfinder/walker:
`deepWaterMax[1,64], swimBankClimbMaxHeight[0,64], sceneQueryMaxRadius[4,48],
rangedAvoidRadius[4,48], autoBackfillRadius[1,16], maxWaterBucketFall[4,256],
walker.repathEveryTicks[20,10000], walker.totalTickBudget[200,36000],
mine.searchVerticalRadius[1,32], breakTimeoutTicks[20,2000],
pathfinder.maxNodes[1000,1000000], pathfinder.maxMs[100,30000],
pathfinder.sliceMs[1,50], pathfinder.idleSliceMs[1,50], pathfinder.ledgeDangerMinDrop[1,64],
pathfinder.axisHeight[-64,320], goalFieldCellSize[1,16], goalFieldRadius[8,192],
goalFieldVerticalRadius[4,128], pathfinderDepthSlack[0,64], pathfinderHorizonBlocks[0,512],
pathfinderMaxDryFall[3,5], pathfinderSoftCommitNodes[0,1000000], pathfinderQuickNodes[0,10000],
pathDebugMaxNodes[100,200000], pathDebugMaxSamples[100,200000]`.

**Numbers (double, [min,max])** — survival/combat: `retreatHpThreshold[0,20],
bunkerHpThreshold[0,20], healHpThreshold[0,20], autoFightThreatThreshold[0,1],
combatReach[1,6], kiteDistance[3,32], creeperKeepDistance[1,16],
projectileDodgeRadius[1,32], equipDurabilityThreshold[0,1], fleeDangerBoost[1,20]`.
Pathfinder costs/penalties: `pathfinder.dangerPenalty[0,1000],
pathfinder.lavaDangerPenalty[0,5000], pathfinder.contactDangerPenalty[0,1000],
pathfinder.ledgeDangerPenalty[0,1000], pathfinder.waterDangerPenalty[0,1000],
pathfinder.mobAvoidRadius[0,64], pathfinder.mobAvoidPenalty[0,1000],
pathfinder.avoidZonePenalty[0,5000], pathfinder.heuristicWeight[1.0,3.0],
pathfinderDepthPenalty[0,100], pathfinderDescendCost[0,200],
pathfinderWaterCellCost, pathfinderWaterClimbOutCost, pathfinderSubmergedWaterCost,
pathfinderBridgeCost[0,1000], pathfinderPillarCost, pathfinderThinObstacleHeight[0,1],
smoothLookDegPerTick[1,180], walker.yawHysteresisDeg[0,30]`.

**Other** — `autoBackfillBlock:id`, `blocksToAvoid:[id]`, `buildBlockWhitelist:[id]`,
`mutedEvents:[type]`, `avoidPoints:[{x,y,z,radius?}]`; `debugFly:bool` (apply-only
creative-flight test toggle, no snapshot field).

## mc.script.eval / mc.skill
| method | params | returns / notes |
|---|---|---|
| `mc.script.eval` | `source` (req, ≤64 KiB), `timeoutMs?` (dflt 3000, max 30000) | run a JS snippet (Rhino, fresh scope per call) against the in-process API. Inside: `Driver.invoke(method, params)` (any route by name), the `Driver.system/observe/action/query/plan/skill/events/wait/client/bot` helpers, `console.log(x)`/`console.error(x)`. Last expression is the result → `{result, error, log:[…], ms}`. **Best when a task needs ≥3 chained calls** (observe→decide→act) — one round-trip instead of N. **Not sandboxed by default**: scripts are a first-party capability with full JVM access; the class filter that denies file/socket/process/reflection access applies only when the game runs with `-Dworlddriver.sandbox=on`. Treat the endpoint as a shell (`docs/guide/transports.md` § Security). Runs on a worker thread, off both the server and the client thread; each `Driver.invoke` hops onto the thread its route needs, so `mc.client.*` / `mc.bot.*` calls from a script work. |
| `mc.skill` | `op?:"save"\|"list"\|"get"\|"run"\|"delete"` (dflt list), `name?` (`[a-z][a-z0-9_]*`), `source?` (save), `args?` (run), `timeoutMs?` (1–30000, dflt 3000) | persistent skill library (scripts saved under `config/worlddriver/scripts/skills/`, or `skills/` under `-Dworlddriver.scriptsDir`). Runs through the same evaluator as `mc.script.eval`, so the same trust model applies. `save`→`{ok,saved,name,bytes}`; `list`→`{ok,skills:[{name,bytes}],count}`; `get`→`{ok,name,source}`; `run`→`{ok,result,error,log,ms,skill}`; `delete`→`{ok,deleted,name}`. |

<a id="mcdebug"></a>
## mc.debug.* (client only — path debugging)
Registered by `PathDebugBootstrap` at client init, so absent (`-32601`) on a dedicated server. Visible MCP tools; schemas in `bot/debug/DebugTools.java`.
| method | params | returns / notes |
|---|---|---|
| `mc.debug.pathChart` | `view?:"dashboard"\|"threeview"` (dflt dashboard), `width?` (256–4096, dflt 1280), `height?` (256–4096, dflt 960), `includeCandidates?` (dflt true), `save?` (dflt true), `name?` (file name, no extension) | render the current goto session's debug chart → `{ok, view, outcome, plans, candidates, samples}` plus `{path, width, height, bytes}` when saved (PNG under `config/worlddriver/debug/`) or `{width, height}` with `save:false`. Captures nothing unless `mc.bot.setting{pathDebug:true}` was on **before** the goto. |
| `mc.debug.plan` | `goal:{x,z}` (req), `from?:{x,y,z}` (dflt the player's block), `chain?` (dflt false), `maxSegments?` (1–200, dflt 40) | read-only single A* search to an XZ goal: no walking, no world edits → `{ok, start, goal, goalReached, pathLen, end, expanded, computeMs, wallMs, finalCost, hStart, hEnd, hDelta, forward, maxStepDrop, yProfile}` (`hDelta<0` = forward progress). `chain:true` feeds each committed endpoint back in → `{ok, chain, start, goal, reached, segments, backwardSegments, maxRegression, totalExpanded, hStart, hFinal, trail[]}`. Pin `pathfinder.maxNodes` for repeatable numbers. |
| `mc.debug.replay` | `file?` (under `config/worlddriver/replays/`, dflt newest plan archive), `restoreBlocks?` (dflt true), `replan?` (dflt true), `fromStep?` (accepted, ignored) | **mutates the world and moves the bot**: restores the archive's block envelope, teleports to the recorded start and runs again. `replan:true` re-issues the recorded goal with normal planning → `{ok, file, goal, restoredBlocks, mode:"replan", envelopeCells, blockStateFidelity}`; `replan:false` walks the stored plan with no re-planning → `{ok, file, plannedNodes, restoredBlocks, replayRun, segments, fromStepRequested, fromStepHonored:false, blockStateFidelity}`. The run is written as `replay-run-*.json`. Needs `pathArchive` on during the original goto. |

## worlddriver.* (testmod only — hand-built scenes)
Registered by the testmod (`stagewright*` runs and holds, never the published jar), hidden from the MCP tool list, callable on every transport. Positions in a fixture are origin-relative; `pos`/`around` on the wire are absolute `{x,y,z}`. Files live under `config/worlddriver/scenes/` of the run directory. Manual: `docs/guide/human-verification.md`.
| method | params | returns / notes |
|---|---|---|
| `worlddriver.mark` | `role` (req: origin\|corner\|start\|goal\|via\|pass\|forbid\|stand\|watch), `pos` (req), `label?`, `args?` (e.g. `{yaw:-90}` on start) | place a marker block; `{placed, role, pos, label?}`. Command form `/worlddriver mark <role> [label]` uses the crosshair. |
| `worlddriver.scene.save` | `name` (req), `verb?` (goto\|mine\|escape\|elytra, dflt goto), `budget?` (ticks per task, dflt 1200), `around?` (scan centre, dflt first player), `author?` | terrain between the two corner markers → `<name>.nbt`, the fixture → `<name>.json`; markers stay. `{saved, json, nbt, fixture}`. |
| `worlddriver.scene.list` | — | `{dir, scenes:[{file,name,size,chunkRadius,legs,hasNbt,lastVerdict?:{when,who,human,note}}]}` |
| `worlddriver.scene.place` | `name` (req), `pos?` (origin cell, dflt first player's feet) | terrain + markers back into the world for editing. `{placed,name,origin,min,size}` |
| `worlddriver.scene.run` | `name` (req: a saved fixture, or `here` = the markers already in the world), `body?` (server\|self; dflt by topology), `watch?`, `pos?` (origin for a fixture), `around?` (scan centre for `here`), `verb?`/`budget?` (for `here`), `awaitMs?` | starts the run; `{started,name,origin}`. With `awaitMs` (not from the server thread) also `status` (pass\|fail\|skip\|error\|running), `reason?`, `auto:{arrive.N, via, pass, forbid, stand, watch.N, expect.*}`, `observed:{ticks,repaths,hops,digs}`, `lines`. Each run appends a line to `<name>.verdicts.jsonl`; a player on the server gets the report and the judgement buttons in chat. One run at a time. |
| `worlddriver.scene.verdict` | `name` (req), `verdict` (req: pass\|fail\|flaky), `note?` | appends the human judgement (copying the latest run's numbers) → `{recorded, file, record}` |
| `worlddriver.scene.accept` | `name` (req) | latest run judged `pass`, each observed number +20 % rounded up → the fixture's `expect` → `{accepted, json, expect}`. Not for `here`. |

