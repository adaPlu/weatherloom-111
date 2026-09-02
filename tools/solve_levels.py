#!/usr/bin/env python3
"""Canonical-solution finder for Weatherloom levels.

Authoring a hollow means drawing terrain and stating objectives. Finding a set of
threads that actually satisfies those objectives by hand is the expensive part, so
this searches for one using the same engine the game and the validator run.

    python3 tools/solve_levels.py                 # solve every level missing a solution
    python3 tools/solve_levels.py c7-1 c7-2       # solve specific ids
    python3 tools/solve_levels.py --all           # re-solve everything (verification)
    python3 tools/solve_levels.py --budget 50     # stop after N seconds and save progress
    python3 tools/solve_levels.py --write         # write solutions back into levels.json

A level is only shippable once this finds a solution AND validate_levels.py agrees.
"""
import argparse
import itertools
import json
import random
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from validate_levels import LEVELS, Level, line_cells, measure, run, stroke_dir  # noqa: E402

TYPES = ["WarmFront", "ColdFront", "WindBand", "MoistureRibbon"]


def candidates(lv, ttype, max_cells):
    """Plausible strokes for one thread type.

    Fronts read as bands, so they are horizontal sweeps; wind is what players
    aim, so it also gets verticals and half-width runs. Keeping this list small
    is what makes the search finish in seconds instead of hours.
    """
    out = []
    w, h = lv.width, lv.height
    mid = w // 2

    for y in range(h):
        out.append(((0, y), (w - 1, y)))
    if ttype in ("WindBand", "MoistureRibbon"):
        for y in range(h):
            out.append(((0, y), (mid, y)))
            out.append(((mid, y), (w - 1, y)))
    if ttype == "WindBand":
        for x in range(w):
            out.append(((x, 0), (x, h - 1)))
        for x in range(0, w, 2):
            out.append(((x, 0), (x, h // 2)))
            out.append(((x, h // 2), (x, h - 1)))

    seen, keep = set(), []
    for a, b in out:
        if len(line_cells(lv, a, b)) > max_cells:
            continue
        key = (a, b)
        if key in seen:
            continue
        seen.add(key)
        keep.append((a, b))
    return keep


def to_thread(lv, ttype, seg):
    a, b = seg
    return {"type": ttype, "cells": line_cells(lv, a, b), "dir": stroke_dir(a, b)}


def score(lv, spec_list, threads):
    """0.0 means solved. Higher is further away, so the search can walk downhill."""
    state, _ = run(lv, threads)
    total = 0.0
    for spec in spec_list:
        v = measure(lv, state, spec)
        target = spec["target"]
        cmp_ = spec["cmp"]
        if cmp_ == "Gte":
            miss = max(0, target - v)
            total += miss / max(1.0, float(target))
        elif cmp_ == "Lte":
            miss = max(0, v - target)
            # Overshooting a "must not" objective is a hard design failure,
            # so weight it heavily rather than letting the search trade it away.
            total += 2.0 * miss / max(1.0, float(target) + 1.0)
        else:
            total += abs(v - target) / max(1.0, float(target))
    return total


def slots_for(level_dict):
    out = []
    for t in TYPES:
        for _ in range(level_dict["threads"].get(t, 0)):
            out.append(t)
    return out


def solve(level_dict, deadline, rng):
    """Coordinate descent with random restarts over the per-slot candidate lists."""
    lv = Level(level_dict)
    specs = level_dict["objectives"]
    max_cells = level_dict["maxThreadCells"]
    slots = slots_for(level_dict)
    if not slots:
        return None, None

    cand = {t: candidates(lv, t, max_cells) for t in set(slots)}
    per_slot = [cand[t] for t in slots]

    # Small enough search spaces are just enumerated - guarantees the best answer.
    combos = 1
    for c in per_slot:
        combos *= len(c) + 1
    if combos <= 6000:
        best, best_s = None, float("inf")
        for pick in itertools.product(*[[None] + c for c in per_slot]):
            if time.time() > deadline:
                break
            threads = [to_thread(lv, slots[i], seg) for i, seg in enumerate(pick) if seg]
            if not threads:
                continue
            s = score(lv, specs, threads)
            # Fewer strokes is a better canonical line, so ties go to the shorter answer.
            if s < best_s or (s == best_s and best and len(threads) < len(best)):
                best, best_s = [(slots[i], seg) for i, seg in enumerate(pick) if seg], s
            if best_s == 0.0 and best and len(best) == 1:
                break
        return best, best_s

    best, best_s = None, float("inf")
    while time.time() < deadline:
        pick = [rng.choice(c) for c in per_slot]
        cur = score(lv, specs, [to_thread(lv, slots[i], s) for i, s in enumerate(pick)])
        improved = True
        while improved and time.time() < deadline:
            improved = False
            for i in range(len(slots)):
                for seg in per_slot[i] + [None]:
                    if seg == pick[i]:
                        continue
                    trial = list(pick)
                    trial[i] = seg
                    threads = [to_thread(lv, slots[j], s) for j, s in enumerate(trial) if s]
                    if not threads:
                        continue
                    s = score(lv, specs, threads)
                    if s < cur - 1e-9:
                        cur, pick, improved = s, trial, True
        chosen = [(slots[i], s) for i, s in enumerate(pick) if s]
        if cur < best_s:
            best, best_s = chosen, cur
        if best_s == 0.0:
            break
    return best, best_s


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("ids", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--budget", type=float, default=45.0, help="seconds of search per level")
    ap.add_argument("--write", action="store_true", help="write solutions into levels.json")
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()

    data = json.loads(LEVELS.read_text())
    rng = random.Random(args.seed)

    targets = []
    for d in data["levels"]:
        if args.ids:
            if d["id"] in args.ids:
                targets.append(d)
        elif args.all or not d.get("solution"):
            targets.append(d)

    if not targets:
        print("nothing to solve")
        return 0

    failures = 0
    for d in targets:
        t0 = time.time()
        best, s = solve(d, time.time() + args.budget, rng)
        dt = time.time() - t0
        if best is None:
            print(f'  !! {d["id"]}: no threads declared')
            failures += 1
            continue
        status = "SOLVED" if s == 0.0 else f"BEST score={s:.3f}"
        print(f'{status:22} {d["id"]:6} {d["name"]:22} in {dt:4.1f}s')
        for ttype, (a, b) in best:
            print(f'      {ttype:16} {list(a)} -> {list(b)}')
        if s != 0.0:
            failures += 1
        elif args.write:
            d["solution"] = [
                {"type": t, "from": list(a), "to": list(b)} for t, (a, b) in best
            ]

    if args.write:
        LEVELS.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
        print(f"\nwrote solutions into {LEVELS.name}")

    print(f"\n{len(targets) - failures}/{len(targets)} solved")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
