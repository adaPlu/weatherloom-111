#!/usr/bin/env python3
"""Headless mirror of SimulationEngine.kt used to validate authored levels.

Run: python3 tools/validate_levels.py
Every level must be solved by its canonical solution, otherwise the content is broken.
Keep this file in lockstep with core/sim/SimulationEngine.kt.
"""
import copy
import json
import sys
from pathlib import Path

LEVELS = Path(__file__).resolve().parent.parent / "android/app/src/main/assets/levels.json"

# terrain char -> (terrain, feature)
CHARS = {
    ".": ("BareSoil", "None"),
    "m": ("Meadow", "None"),
    "f": ("Meadow", "Flower"),
    "c": ("Crop", "None"),
    "v": ("Village", "None"),
    "h": ("Village", "House"),
    "R": ("Reservoir", "None"),
    "~": ("River", "None"),
    "L": ("Lake", "None"),
    "w": ("Wetland", "None"),
    "T": ("Forest", "None"),
    "^": ("Mountain", "None"),
    "s": ("Stone", "None"),
    "-": ("Road", "None"),
    "W": ("Meadow", "Windmill"),
    "X": ("Stone", "Windmill"),
}
WATER_BODY = {"Reservoir", "Lake"}
CHANNEL = {"Reservoir", "Lake", "River", "Wetland"}
DIRS = {"None": (0, 0), "North": (0, -1), "East": (1, 0), "South": (0, 1), "West": (-1, 0)}
CARDINALS = ["North", "East", "South", "West"]


class Level:
    def __init__(self, d):
        self.__dict__.update(d)
        self.height = len(d["map"])
        self.width = len(d["map"][0])
        self.size = self.width * self.height
        self.terrain = []
        self.feature = []
        self.elev = []
        for y, row in enumerate(d["map"]):
            assert len(row) == self.width, f'{d["id"]} row {y} width'
            for x, ch in enumerate(row):
                t, f = CHARS[ch]
                self.terrain.append(t)
                self.feature.append(f)
        assert len(d["elevation"]) == self.height, f'{d["id"]} elevation height'
        for y, row in enumerate(d["elevation"]):
            assert len(row) == self.width, f'{d["id"]} elevation row {y} width'
            for ch in row:
                self.elev.append(int(ch))
        fog = d.get("fog")
        self.start_fog = [0] * self.size
        if fog:
            assert len(fog) == self.height, f'{d["id"]} fog height'
            i = 0
            for y, row in enumerate(fog):
                assert len(row) == self.width, f'{d["id"]} fog row {y} width'
                for ch in row:
                    self.start_fog[i] = int(ch)
                    i += 1
        self.start_fog_total = sum(self.start_fog)
        self.river_flow = d.get("riverFlow", "South")

    def idx(self, x, y):
        return y * self.width + x

    def nb(self, i, d):
        dx, dy = DIRS[d]
        x, y = i % self.width + dx, i // self.width + dy
        if 0 <= x < self.width and 0 <= y < self.height:
            return y * self.width + x
        return -1


class State:
    KEYS = ["temp", "moisture", "cloud", "water", "storage", "snow", "fog", "windDir",
            "windStr", "bloom", "bloomTimer", "freeze", "frozen", "precip", "precipSnow",
            "windmillTicks", "spinning"]

    def __init__(self, size):
        for k in State.KEYS:
            setattr(self, k, [0] * size)
        self.overflowed = False

    def copy(self):
        s = State.__new__(State)
        for k in State.KEYS:
            setattr(s, k, list(getattr(self, k)))
        s.overflowed = self.overflowed
        return s


def baseline_temp(lv, i):
    return max(-2, min(2, lv.baseTemp - lv.elev[i] // 2))


def initial_state(lv):
    s = State(lv.size)
    for i in range(lv.size):
        t = lv.terrain[i]
        s.temp[i] = baseline_temp(lv, i)
        s.moisture[i] = 1 if (t in WATER_BODY or t == "Wetland") else lv.startMoisture
        s.fog[i] = lv.start_fog[i]
        s.water[i] = 1 if t == "River" else 0
        s.storage[i] = lv.startReservoir if t == "Reservoir" else 0
        s.snow[i] = 1 if (
            t == "Mountain"
            and getattr(lv, "startSnowSummits", False)
            and lv.elev[i] >= 3
        ) else 0
    return s


def line_cells(lv, a, b):
    """Bresenham from a to b, inclusive."""
    x0, y0 = a
    x1, y1 = b
    cells = []
    dx, dy = abs(x1 - x0), abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx - dy
    while True:
        cells.append(lv.idx(x0, y0))
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x0 += sx
        if e2 < dx:
            err += dx
            y0 += sy
    return cells


def stroke_dir(a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    if abs(dx) >= abs(dy):
        return "East" if dx > 0 else ("West" if dx < 0 else "None")
    return "South" if dy > 0 else "North"


def step(lv, threads, prev, beat, events, seen):
    s = prev.copy()
    size = lv.size
    s.precip = [0] * size
    s.precipSnow = [0] * size
    s.windDir = [0] * size
    s.windStr = [0] * size
    DIRORD = ["None", "North", "East", "South", "West"]

    warm = [0] * size
    cold = [0] * size
    for t in threads:
        ty, cells, d = t["type"], t["cells"], t["dir"]
        if ty == "WarmFront":
            for c in cells:
                warm[c] = 2
            for c in cells:
                for dd in CARDINALS:
                    n = lv.nb(c, dd)
                    if n >= 0 and warm[n] == 0:
                        warm[n] = 1
        elif ty == "ColdFront":
            for c in cells:
                cold[c] = 2
            for c in cells:
                for dd in CARDINALS:
                    n = lv.nb(c, dd)
                    if n >= 0 and cold[n] == 0:
                        cold[n] = 1
        elif ty == "WindBand":
            for c in cells:
                s.windDir[c] = DIRORD.index(d)
                s.windStr[c] = 2
            for c in cells:
                for dd in CARDINALS:
                    n = lv.nb(c, dd)
                    if n >= 0 and s.windStr[n] == 0:
                        s.windDir[n] = DIRORD.index(d)
                        s.windStr[n] = 1
        elif ty == "MoistureRibbon":
            for c in cells:
                s.moisture[c] = min(3, s.moisture[c] + 1)
            if beat % 2 == 0:
                for c in cells:
                    for dd in CARDINALS:
                        n = lv.nb(c, dd)
                        if n >= 0:
                            s.moisture[n] = min(3, s.moisture[n] + 1)

    for i in range(size):
        w, c = warm[i], cold[i]
        if w > 0 and c > 0:
            pass
        elif w > 0:
            target = 2 if w == 2 else 1
            if s.temp[i] < target:
                s.temp[i] += 1
            if w == 2 and beat % 2 == 0:
                s.moisture[i] = min(3, s.moisture[i] + 1)
        elif c > 0:
            target = -2 if c == 2 else -1
            if s.temp[i] > target:
                s.temp[i] -= 1
        elif beat % 2 == 0:
            base = baseline_temp(lv, i)
            if s.temp[i] > base:
                s.temp[i] -= 1
            elif s.temp[i] < base:
                s.temp[i] += 1
        if w == 1 and c == 0 and beat % 4 == 0:
            s.moisture[i] = min(3, s.moisture[i] + 1)

    # advection
    cloud_out = [0] * size
    fog_out = [0] * size
    for i in range(size):
        st = s.windStr[i]
        d = DIRORD[s.windDir[i]]
        if st <= 0 or d == "None":
            cloud_out[i] += s.cloud[i]
            fog_out[i] += s.fog[i]
            continue
        n = lv.nb(i, d)
        mc = min(s.cloud[i], st)
        mf = min(s.fog[i], 1)
        cloud_out[i] += s.cloud[i] - mc
        fog_out[i] += s.fog[i] - mf
        if n >= 0:
            cloud_out[n] += mc
            fog_out[n] += mf
    for i in range(size):
        s.cloud[i] = min(3, cloud_out[i])
        s.fog[i] = min(2, fog_out[i])

    # front interaction
    for i in range(size):
        if s.moisture[i] < 1:
            continue
        md = 0
        for dd in CARDINALS:
            n = lv.nb(i, dd)
            if n >= 0:
                md = max(md, abs(s.temp[i] - s.temp[n]))
        if md >= 2:
            s.cloud[i] = min(3, s.cloud[i] + 1)
            if s.cloud[i] >= 2 and "CloudsCollide" not in seen:
                seen.add("CloudsCollide")
                events.append((beat, "CloudsCollide", i))
        elif s.temp[i] <= -1 and s.moisture[i] >= 2:
            s.cloud[i] = min(3, s.cloud[i] + 1)

    # uplift
    uplift = [0] * size
    for i in range(size):
        if s.windStr[i] <= 0:
            continue
        n = lv.nb(i, DIRORD[s.windDir[i]])
        if n >= 0 and lv.elev[n] > lv.elev[i]:
            uplift[n] = 1

    # precipitation
    for i in range(size):
        inten = 0
        if s.cloud[i] >= 2 and s.moisture[i] >= 1:
            inten = s.cloud[i] - 1
        if uplift[i] and s.cloud[i] >= 1 and s.moisture[i] >= 1:
            inten += 1
        if inten <= 0:
            continue
        inten = min(inten, 2)
        snowing = s.temp[i] <= -1
        s.precip[i] = inten
        s.precipSnow[i] = 1 if snowing else 0
        if snowing:
            s.snow[i] = min(2, s.snow[i] + 1)
            if "SnowBegins" not in seen:
                seen.add("SnowBegins")
                events.append((beat, "SnowBegins", i))
        else:
            if lv.terrain[i] in WATER_BODY:
                s.storage[i] += inten
            else:
                s.water[i] = min(3, s.water[i] + inten)
            if "RainBegins" not in seen:
                seen.add("RainBegins")
                events.append((beat, "RainBegins", i))
        s.cloud[i] = max(0, s.cloud[i] - inten)
        s.moisture[i] = max(0, s.moisture[i] - 1)

    # runoff
    win = [0] * size
    wout = [0] * size
    for i in range(size):
        if s.water[i] < 1:
            continue
        t = lv.terrain[i]
        if t in WATER_BODY:
            continue
        dest = -1
        if t == "River":
            n = lv.nb(i, lv.river_flow)
            if n >= 0 and (lv.terrain[n] in CHANNEL or lv.elev[n] < lv.elev[i]):
                dest = n
        if dest < 0:
            my = lv.elev[i] * 2 + s.water[i]
            best, bh = -1, my
            for dd in CARDINALS:
                n = lv.nb(i, dd)
                if n < 0:
                    continue
                nh = lv.elev[n] * 2 + s.water[n] - (2 if lv.terrain[n] in CHANNEL else 0)
                if nh < bh:
                    bh, best = nh, n
            if best >= 0 and my - bh >= 2:
                dest = best
        if dest >= 0:
            wout[i] += 1
            win[dest] += 1
    for i in range(size):
        s.water[i] = max(0, s.water[i] - wout[i])
    for i in range(size):
        if win[i] == 0:
            continue
        if lv.terrain[i] in WATER_BODY:
            s.storage[i] += win[i]
            if lv.terrain[i] == "Reservoir" and "RunoffReaches" not in seen:
                seen.add("RunoffReaches")
                events.append((beat, "RunoffReaches", i))
        else:
            s.water[i] = min(3, s.water[i] + win[i])

    # a reservoir only holds so much; the rest runs over the lip
    res = [i for i in range(size) if lv.terrain[i] == "Reservoir"]
    total = sum(s.storage[i] for i in res)
    if total > lv.reservoirCapacity:
        excess = total - lv.reservoirCapacity
        s.overflowed = True
        if "Overflow" not in seen:
            seen.add("Overflow")
            events.append((beat, "Overflow", res[0]))
        for i in reversed(res):
            take = min(excess, s.storage[i])
            s.storage[i] -= take
            excess -= take
            if excess == 0:
                break

    # reactions
    lo, hi = lv.bloomTempRange
    for i in range(size):
        t, f = lv.terrain[i], lv.feature[i]
        if f == "Flower":
            if s.water[i] >= 1 and lo <= s.temp[i] <= hi:
                s.bloomTimer[i] += 1
            elif s.bloomTimer[i] > 0 and s.bloom[i] < 2:
                s.bloomTimer[i] -= 1
            if s.bloomTimer[i] >= 2 and s.bloom[i] < 2:
                s.bloom[i] = 2
                if "FlowerBloomed" not in seen:
                    seen.add("FlowerBloomed")
                    events.append((beat, "FlowerBloomed", i))
            elif s.bloomTimer[i] >= 1 and s.bloom[i] < 1:
                s.bloom[i] = 1
        if t == "Crop":
            if s.temp[i] <= -1:
                s.freeze[i] += 1
            elif s.freeze[i] > 0:
                s.freeze[i] -= 1
            if s.freeze[i] >= 4 and s.frozen[i] == 0:
                s.frozen[i] = 1
                events.append((beat, "CropFrozen", i))
        if f == "Windmill":
            spin = s.windStr[i] >= 1
            s.spinning[i] = 1 if spin else 0
            if spin:
                s.windmillTicks[i] += 1
        if s.fog[i] > 0:
            if s.windStr[i] >= 2 or (s.windStr[i] == 1 and beat % 2 == 0) or (s.temp[i] >= 1 and beat % 3 == 0):
                s.fog[i] -= 1

    # melt / evaporate
    for i in range(size):
        if s.snow[i] > 0 and s.temp[i] >= 1 and beat % 3 == 0:
            s.snow[i] -= 1
            s.water[i] = min(3, s.water[i] + 1)
        if lv.terrain[i] not in CHANNEL and s.water[i] > 0 and s.temp[i] >= 1 and beat % 4 == 0:
            s.water[i] -= 1
            s.moisture[i] = min(3, s.moisture[i] + 1)
        if s.cloud[i] > 0 and s.moisture[i] == 0 and beat % 5 == 0:
            s.cloud[i] -= 1
    return s


def measure(lv, s, spec):
    m = spec["metric"]
    if m == "ReservoirWater":
        return sum(s.storage[i] for i in range(lv.size) if lv.terrain[i] == "Reservoir")
    if m == "BloomedFlowers":
        return sum(1 for i in range(lv.size) if lv.feature[i] == "Flower" and s.bloom[i] >= 2)
    if m == "FrozenCrops":
        return sum(1 for i in range(lv.size) if lv.terrain[i] == "Crop" and s.frozen[i])
    if m == "SnowTiles":
        return sum(1 for i in range(lv.size) if lv.terrain[i] == "Mountain" and s.snow[i] >= 1)
    if m == "VillageFog":
        return sum(s.fog[i] for i in range(lv.size) if lv.terrain[i] == "Village")
    if m == "WindmillTicks":
        mills = [i for i in range(lv.size) if lv.feature[i] == "Windmill"]
        if not mills:
            return 0
        return min(s.windmillTicks[i] for i in mills) if spec.get("everyWindmill") else max(s.windmillTicks[i] for i in mills)
    if m == "FloodedTiles":
        return sum(1 for i in range(lv.size)
                   if lv.terrain[i] in ("Village", "Crop", "Road") and s.water[i] >= 3)
    if m == "WetlandWater":
        return sum(s.water[i] for i in range(lv.size) if lv.terrain[i] == "Wetland")
    raise ValueError(m)


def run(lv, threads):
    s = initial_state(lv)
    events, seen = [], set()
    for beat in range(1, lv.beats + 1):
        s = step(lv, threads, s, beat, events, seen)
    return s, events


DAILY_TEMPLATE_IDS = ["c1-1", "c1-3", "c2-2", "c3-1", "c4-1", "c5-1", "c6-1"]


def canonical_threads(lv, d):
    threads = []
    for st in d["solution"]:
        a, b = tuple(st["from"]), tuple(st["to"])
        threads.append({
            "type": st["type"],
            "cells": line_cells(lv, a, b),
            "dir": stroke_dir(a, b),
        })
    return threads


def spec_met(value, spec):
    cmp_ = spec["cmp"]
    if cmp_ == "Eq":
        return value == spec["target"]
    if cmp_ == "Gte":
        return value >= spec["target"]
    return value <= spec["target"]


def canonical_solves(d):
    lv = Level(d)
    s, _ = run(lv, canonical_threads(lv, d))
    return all(spec_met(measure(lv, s, spec), spec) for spec in d["objectives"])


def daily_variant(template, variant):
    d = copy.deepcopy(template)
    if variant == 1:
        for spec in d["objectives"]:
            if (
                spec["cmp"] == "Gte"
                and spec["target"] > 2
                and spec["metric"] != "WindmillTicks"
            ):
                spec["target"] -= 1
    elif variant == 2:
        d["threads"] = {name: count + 1 for name, count in d["threads"].items()}
    return d


def validate_daily_variants(data):
    by_id = {d["id"]: d for d in data["levels"]}
    ok = True
    print("DAILY FORECAST VARIANTS")
    for level_id in DAILY_TEMPLATE_IDS:
        template = by_id.get(level_id)
        if template is None:
            print(f"FAIL  {level_id:6} missing daily template")
            ok = False
            continue
        for variant in range(3):
            d = daily_variant(template, variant)
            passed = canonical_solves(d)
            print(f'{"PASS" if passed else "FAIL"}  {level_id:6} variant {variant}')
            ok &= passed
    return ok


def main():
    data = json.loads(LEVELS.read_text())
    ok = True
    for d in data["levels"]:
        lv = Level(d)
        threads = []
        for st in d["solution"]:
            a, b = tuple(st["from"]), tuple(st["to"])
            threads.append({"type": st["type"], "cells": line_cells(lv, a, b), "dir": stroke_dir(a, b)})
        # budget check
        used = {}
        for t in threads:
            used[t["type"]] = used.get(t["type"], 0) + 1
        for k, v in used.items():
            if v > d["threads"].get(k, 0):
                print(f'  !! {d["id"]} solution uses {v}x {k} but budget is {d["threads"].get(k,0)}')
                ok = False
        for t in threads:
            if len(t["cells"]) > d["maxThreadCells"]:
                print(f'  !! {d["id"]} stroke length {len(t["cells"])} > max {d["maxThreadCells"]}')
                ok = False

        s, events = run(lv, threads)
        results = []
        solved = True
        for spec in d["objectives"]:
            v = measure(lv, s, spec)
            cmp_ = spec["cmp"]
            met = (v == spec["target"]) if cmp_ == "Eq" else (v >= spec["target"] if cmp_ == "Gte" else v <= spec["target"])
            solved &= met
            results.append(f'{spec["label"]}={v} {"" if cmp_=="Eq" else cmp_}{spec["target"]} {"OK" if met else "FAIL"}')
        flag = "PASS" if solved else "FAIL"
        if not solved:
            ok = False
        print(f'{flag}  {d["id"]:6} {d["name"]:20} | ' + " | ".join(results))
        if not solved:
            print("        events:", events[:8])
    print()
    ok &= validate_daily_variants(data)
    print()
    print("ALL LEVELS VALID" if ok else "CONTENT BROKEN")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
