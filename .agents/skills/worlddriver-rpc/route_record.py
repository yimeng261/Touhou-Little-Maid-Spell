#!/usr/bin/env python3
"""route_record.py — record one run of a fixed route (bot OR human) and compare runs.

The same recorder serves both drivers, so a bot run and a human run of the same route are
directly comparable: it samples mc.client.player over ONE websocket at ~20 Hz, pulls
block.place / block.break events by cursor, and writes one JSON line per sample.

  record  --label NAME --start X,Y,Z --goal X,Z [--radius R] [--yaw DEG] [--bot] [--out DIR]
          Teleports to the start (facing --yaw), waits, and starts the clock on the first
          0.5 block of movement. With --bot it issues mc.bot.goto itself; without it a human
          drives. Stops when the player is within --radius of the goal column (or --max-s).
  compare A.jsonl B.jsonl [...] [--png OUT]
          Per-run totals, then time spent per 25-block slice of progress toward the goal,
          so the slices where one run loses time stand out. --png draws the overlay.
  revert  RUN.jsonl
          Undo the blocks that run placed and broke, so the next driver meets the same terrain.
"""
import argparse, asyncio, json, math, os, sys, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rpc  # reuse resolve_host / resolve_port / call

import websockets

HZ = 20
# Edit events are polled every 0.5 s, so the driver may have moved ~4 blocks past the edit.
REACH = 10
PLACEABLE_HINT = ("block.place", "block.break")
# Both drivers start from the same inventory, or the comparison measures the kit, not the driver.
KIT = ("clear @p", "give @p minecraft:iron_pickaxe", "give @p minecraft:iron_shovel",
       "give @p minecraft:iron_axe", "give @p minecraft:dirt 64", "give @p minecraft:cobblestone 64",
       "give @p minecraft:cooked_beef 16", "give @p minecraft:water_bucket")


def xz(s):
    return tuple(float(v) for v in s.split(","))


async def _call(ws, state, method, params=None, timeout=10):
    state["rid"] += 1
    ok, res = await rpc.call(ws, state["rid"], method, params or {}, timeout)
    if not ok:
        raise RuntimeError(f"{method}: {res}")
    return res


async def record(args):
    sx, sy, sz = xz(args.start)
    gx, gz = xz(args.goal)
    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{time.strftime('%Y%m%d-%H%M%S')}-{args.label}.jsonl")
    uri = f"ws://{rpc.resolve_host(None)}:{rpc.resolve_port(None)}/rpc"
    st = {"rid": 0}
    async with websockets.connect(uri, max_size=16 * 1024 * 1024, ping_interval=None) as ws:
        await _call(ws, st, "mc.bot.cancel")
        # The kit's `clear` would take a goal compass with it, so the compass is part of the kit.
        compass = (f"give @p minecraft:compass[minecraft:lodestone_tracker={{target:{{pos:[I;{int(gx)},"
                   f"{int(sy)},{int(gz)}],dimension:\"minecraft:overworld\"}},tracked:false}},"
                   f"minecraft:custom_name='\"goal\"']")
        for cmd in (f"tp @p {sx} {sy} {sz} {args.yaw} 0", "effect clear @p",
                    "effect give @p minecraft:instant_health 1 10 true",
                    "effect give @p minecraft:saturation 1 10 true", *KIT, compass):
            await _call(ws, st, "mc.action.runCommand", {"cmd": cmd})
        cursor = await _call(ws, st, "mc.observe.cursor")
        await asyncio.sleep(2.0)                                   # let chunks settle after the tp
        # The body can settle ~1 block off the tp target; measure "moved" from where it came to rest.
        rest = (await _call(ws, st, "mc.client.player"))["pos"]
        rx, rz = rest["x"], rest["z"]
        meta = {"meta": True, "label": args.label, "driver": "bot" if args.bot else "human",
                "start": [sx, sy, sz], "goal": [gx, gz], "radius": args.radius, "hz": HZ}
        f = open(path, "w")
        f.write(json.dumps(meta) + "\n")
        if args.bot:
            await _call(ws, st, "mc.bot.goto", {"xz": {"x": int(gx), "z": int(gz)}})
            print(f"[rec] bot goto issued → {gx},{gz}", flush=True)
        else:
            print(f"[rec] 就位。开始移动即开始计时；目标 x={gx:.0f} z={gz:.0f}（半径 {args.radius} 格）", flush=True)
        t0 = None
        last_print = 0.0
        next_ev = 0.0
        end_reason = "max-s"
        while True:
            tick_start = time.monotonic()
            p = await _call(ws, st, "mc.client.player")
            now = time.monotonic()
            if not p.get("present"):
                await asyncio.sleep(0.2)
                continue
            x, y, z = p["pos"]["x"], p["pos"]["y"], p["pos"]["z"]
            if t0 is None:
                if math.hypot(x - rx, z - rz) > 0.5:
                    t0 = now
                    # Edits made while waiting (a goal beacon being built) are not the run's.
                    cursor = await _call(ws, st, "mc.observe.cursor")
                    print("[rec] 计时开始", flush=True)
                else:
                    await asyncio.sleep(1 / HZ)
                    continue
            rec = {"t": round(now - t0, 3), "x": round(x, 3), "y": round(y, 3), "z": round(z, 3),
                   "yaw": round(p["look"]["yaw"], 1), "pitch": round(p["look"]["pitch"], 1),
                   "g": p.get("onGround"), "w": p.get("inWater"), "uw": p.get("underWater"),
                   "sn": p.get("crouching"), "hp": p.get("health"), "slot": p.get("selectedSlot")}
            if now >= next_ev:
                evs = await _call(ws, st, "mc.observe.eventsSince", {"cursor": cursor, "limit": 200})
                for e in evs:
                    cursor = max(cursor, e.get("seq", cursor))
                    if e.get("type") in PLACEABLE_HINT:
                        rec.setdefault("ev", []).append([e["type"], e.get("pos"), e.get("data")])
                next_ev = now + 0.5
            f.write(json.dumps(rec) + "\n")
            dist = math.hypot(x - gx, z - gz)
            if now - last_print > 10:
                print(f"[rec] t={now - t0:5.0f}s pos=({x:.0f},{y:.0f},{z:.0f}) 距目标 {dist:.0f}", flush=True)
                last_print = now
            if dist <= args.radius:
                end_reason = "arrived"
                break
            if now - t0 > args.max_s:
                break
            await asyncio.sleep(max(0.0, 1 / HZ - (time.monotonic() - tick_start)))
        if args.bot:
            await _call(ws, st, "mc.bot.cancel")
        else:   # a human cannot see the goal radius; tell them the run is over
            await _call(ws, st, "mc.action.runCommand",
                        {"cmd": f"title @p title \"{end_reason} {time.monotonic() - t0:.1f}s\""})
        f.write(json.dumps({"end": end_reason, "t": round(time.monotonic() - t0, 3)}) + "\n")
        f.close()
        print(f"[rec] 结束 ({end_reason}) 用时 {time.monotonic() - t0:.1f}s → {path}", flush=True)


async def revert(args):
    """Undo one run's world edits so the next driver meets the same terrain.

    A break is put back from the id its event carries (block states such as log axis are lost);
    a place is cleared to air, or to water when a same-level neighbour is water, because the
    event does not say what the placed block replaced. Newest edit first, so a cell edited
    twice ends at its oldest state. Only edits within REACH of where the driver stood are its
    own: anything farther came from a command during the run (the goal beacon) and stays.
    """
    _, rows, _ = load(args.file)
    edits = [ev for r in rows for ev in r.get("ev", [])
             if math.dist((r["x"], r["y"], r["z"]), (ev[1]["x"] + 0.5, ev[1]["y"] + 0.5, ev[1]["z"] + 0.5)) <= REACH]
    uri = f"ws://{rpc.resolve_host(None)}:{rpc.resolve_port(None)}/rpc"
    st = {"rid": 0}
    n = 0
    async with websockets.connect(uri, max_size=16 * 1024 * 1024, ping_interval=None) as ws:
        for kind, pos, data in reversed(edits):
            x, y, z = pos["x"], pos["y"], pos["z"]
            if kind == "block.break":
                block = data or "minecraft:stone"
            else:
                block = "minecraft:air"
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    b = await _call(ws, st, "mc.world.block", {"pos": {"x": x + dx, "y": y, "z": z + dz}})
                    if b.get("type") == "minecraft:water":
                        block = "minecraft:water"
                        break
            await _call(ws, st, "mc.action.runCommand", {"cmd": f"setblock {x} {y} {z} {block}"})
            n += 1
    print(f"[revert] {n} edits undone from {args.file}")


def load(path):
    meta, rows, end = None, [], None
    for line in open(path):
        r = json.loads(line)
        if r.get("meta"):
            meta = r
        elif "end" in r:
            end = r
        else:
            rows.append(r)
    return meta, rows, end


def analyse(meta, rows):
    sx, _, sz = meta["start"]
    gx, gz = meta["goal"]
    total = math.hypot(gx - sx, gz - sz)
    out = {"label": meta["label"], "driver": meta["driver"], "time": rows[-1]["t"] if rows else 0}
    walked = sum(math.hypot(b["x"] - a["x"], b["z"] - a["z"]) for a, b in zip(rows, rows[1:]))
    out["walked"] = walked
    out["straight"] = total
    # stops: runs of >=1 s with horizontal speed < 0.5 b/s
    stops, stop_t, run = 0, 0.0, 0.0
    jumps, water_t, place, brk, hurt = 0, 0.0, 0, 0, 0.0
    for a, b in zip(rows, rows[1:]):
        dt = max(b["t"] - a["t"], 1e-3)
        sp = math.hypot(b["x"] - a["x"], b["z"] - a["z"]) / dt
        if sp < 0.5:
            run += dt
        else:
            if run >= 1.0:
                stops += 1
                stop_t += run
            run = 0.0
        if a.get("g") and not b.get("g") and b["y"] > a["y"] + 0.05 and not b.get("w"):
            jumps += 1
        if b.get("w"):
            water_t += dt
        if b.get("hp") is not None and a.get("hp") is not None and b["hp"] < a["hp"]:
            hurt += a["hp"] - b["hp"]
        # The event channel reports one placement twice in the same poll; count each cell once.
        for ev in {(e[0], json.dumps(e[1], sort_keys=True)): e for e in b.get("ev", [])}.values():
            if ev[0] == "block.place":
                place += 1
            else:
                brk += 1
    out.update(stops=stops, stop_t=stop_t, jumps=jumps, water_t=water_t, placed=place, broken=brk, hurt=hurt)
    # time per progress slice (progress = straight-line distance covered toward the goal)
    slices = {}
    for a, b in zip(rows, rows[1:]):
        prog = total - math.hypot(gx - a["x"], gz - a["z"])
        k = max(0, int(prog // 25))
        slices[k] = slices.get(k, 0.0) + (b["t"] - a["t"])
    out["slices"] = slices
    return out


def compare(args):
    runs = [analyse(*load(p)[:2]) for p in args.files]
    hdr = f"{'':14}" + "".join(f"{r['label']:>16}" for r in runs)
    print(hdr)
    for key, fmt in (("driver", "{}"), ("time", "{:.1f}s"), ("walked", "{:.0f}"), ("straight", "{:.0f}"),
                     ("stops", "{}"), ("stop_t", "{:.1f}s"), ("jumps", "{}"), ("water_t", "{:.1f}s"),
                     ("placed", "{}"), ("broken", "{}"), ("hurt", "{:.0f}")):
        print(f"{key:14}" + "".join(f"{fmt.format(r[key]):>16}" for r in runs))
    avg = [r["straight"] / r["time"] if r["time"] else 0 for r in runs]
    print(f"{'net b/s':14}" + "".join(f"{v:>16.2f}" for v in avg))
    print("\n每 25 格推进的用时（秒），以第一个为基准，差值 >3s 标 *")
    keys = sorted(set().union(*[r["slices"].keys() for r in runs]))
    for k in keys:
        vals = [r["slices"].get(k, 0.0) for r in runs]
        flag = " *" if any(v - vals[0] > 3 or vals[0] - v > 3 for v in vals[1:]) else ""
        print(f"{k * 25:4d}-{k * 25 + 25:<4d}    " + "".join(f"{v:>16.1f}" for v in vals) + flag)
    if args.png:
        draw(args.files, args.png)


def draw(files, out):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    fig, axes = plt.subplots(2, 2, figsize=(16, 11))
    (ax_map, ax_prog), (ax_y, ax_sp) = axes
    for p in files:
        meta, rows, _ = load(p)
        gx, gz = meta["goal"]
        sx, _, sz = meta["start"]
        total = math.hypot(gx - sx, gz - sz)
        t = [r["t"] for r in rows]
        prog = [total - math.hypot(gx - r["x"], gz - r["z"]) for r in rows]
        lbl = f"{meta['label']} ({meta['driver']}, {t[-1]:.0f}s)"
        ax_map.plot([r["x"] for r in rows], [r["z"] for r in rows], lw=1, label=lbl)
        ax_prog.plot(t, prog, lw=1, label=lbl)
        ax_y.plot(prog, [r["y"] for r in rows], lw=1, label=lbl)
        sp = [0.0] + [math.hypot(b["x"] - a["x"], b["z"] - a["z"]) / max(b["t"] - a["t"], 1e-3)
                      for a, b in zip(rows, rows[1:])]
        k = 10  # 0.5 s rolling mean
        sm = [sum(sp[max(0, i - k):i + 1]) / (i + 1 - max(0, i - k)) for i in range(len(sp))]
        ax_sp.plot(prog, sm, lw=1, label=lbl)
        for r in rows:
            for ev in r.get("ev", []):
                ax_map.plot(r["x"], r["z"], "k+" if ev[0] == "block.place" else "rx", ms=4)
    ax_map.set_title("top-down X/Z  (+ place, x break)"); ax_map.invert_yaxis(); ax_map.set_aspect("equal", "datalim")
    ax_prog.set_title("progress toward goal vs time"); ax_prog.set_xlabel("s"); ax_prog.set_ylabel("blocks")
    ax_y.set_title("elevation vs progress"); ax_y.set_xlabel("progress (blocks)")
    ax_sp.set_title("horizontal speed vs progress (0.5s mean)"); ax_sp.set_xlabel("progress (blocks)"); ax_sp.set_ylabel("b/s")
    ax_sp.axhline(5.6, ls=":", c="gray"); ax_sp.axhline(4.3, ls=":", c="gray")
    for ax in (ax_map, ax_prog, ax_y, ax_sp):
        ax.grid(alpha=.3); ax.legend(fontsize=8)
    fig.tight_layout()
    fig.savefig(out, dpi=90)
    print(f"[chart] {out}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("record")
    r.add_argument("--label", required=True)
    r.add_argument("--start", required=True)
    r.add_argument("--goal", required=True)
    r.add_argument("--radius", type=float, default=3.0)
    r.add_argument("--yaw", type=float, default=0.0)
    r.add_argument("--bot", action="store_true")
    r.add_argument("--max-s", type=float, default=900)
    r.add_argument("--out", default="/tmp/wdlive/routes")
    c = sub.add_parser("compare")
    c.add_argument("files", nargs="+")
    c.add_argument("--png")
    v = sub.add_parser("revert")
    v.add_argument("file")
    a = ap.parse_args()
    if a.cmd == "record":
        asyncio.run(record(a))
    elif a.cmd == "revert":
        asyncio.run(revert(a))
    else:
        compare(a)


if __name__ == "__main__":
    main()
