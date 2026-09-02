#!/usr/bin/env python3
"""Authoring pass: repairs Larkspur Field and adds chapters 7-9.

Terrain and objectives are the handcrafted part; canonical solutions are left
empty here on purpose and filled in by solve_levels.py --write, which proves a
solution exists rather than trusting the author's guess.

    python3 tools/new_levels.py
    python3 tools/solve_levels.py --write
    python3 tools/validate_levels.py
"""
import json
from pathlib import Path

LEVELS = Path(__file__).resolve().parent.parent / "android/app/src/main/assets/levels.json"


def flat(n, rows=12):
    return [str(n) * 8 for _ in range(rows)]


def steps(pairs):
    """[(height, repeat), ...] top to bottom, must total 12 rows."""
    out = []
    for h, rep in pairs:
        out += [str(h) * 8] * rep
    assert len(out) == 12, len(out)
    return out


def lvl(**kw):
    base = {
        "baseTemp": 1,
        "startMoisture": 0,
        "startReservoir": 0,
        "reservoirCapacity": 8,
        "bloomTempRange": [0, 2],
        "riverFlow": "South",
        "beats": 56,
        "maxThreadCells": 18,
        "bloomStrokes": 3,
        "flourishStrokes": 3,
        "flourishCells": 30,
        "reward": None,
        "solution": [],
    }
    base.update(kw)
    for row in base["map"]:
        assert len(row) == 8, (base["id"], row)
    assert len(base["map"]) == 12, base["id"]
    return base


def obj(metric, cmp_, target, label, **extra):
    d = {"metric": metric, "cmp": cmp_, "target": target, "label": label}
    d.update(extra)
    return d


CHAPTERS = [
    {"index": 7, "title": "Thawing Season",
     "subtitle": "Snow is only water that has not arrived yet"},
    {"index": 8, "title": "Storm Braid",
     "subtitle": "Wind decides where the weather goes"},
    {"index": 9, "title": "The Weaver's Trial",
     "subtitle": "Every thread you know, on one loom"},
]

NEW = [
    # ------------------------------------------------ chapter 2 and 4 fill-ins
    lvl(
        id="c2-3", chapter=2, name="Kestrel Ridge",
        brief="Three mills along the ridge, and a fog bank sitting on the low road.",
        hint="One band can serve a whole line of mills if you draw it along them, not across them.",
        map=["TTmmmmTT", "mWmmmmWm", "mmmmmmmm", "mmmWmmmm", "mmmmmmmm",
             "mm----mm", "mhvvvhmm", "mm----mm", "mmmmmmmm", "TmmmmmmT",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(2, 2), (1, 3), (0, 5), (1, 2)]),
        baseTemp=0,
        fog=["00000000", "00000000", "00000000", "00000000", "01111100",
             "02222200", "02222200", "01111100", "00000000", "00000000",
             "00000000", "00000000"],
        beats=48, threads={"WindBand": 2},
        objectives=[obj("WindmillTicks", "Gte", 18, "Mills", everyWindmill=True),
                    obj("VillageFog", "Lte", 0, "Low road")],
        bloomStrokes=2, flourishStrokes=2, flourishCells=22,
    ),
    lvl(
        id="c4-3", chapter=4, name="Candlewick Rows",
        brief="Bloom the candlewick. The seed barley either side must stay warm.",
        hint="Cold air spills one tile past the thread you draw. Leave the barley a margin.",
        map=["TTmmmmTT", "mmccccmm", "mmmmmmmm", "mmmmmmmm", "mfmffmfm",
             "mmmmmmmm", "mmmmmmmm", "mmccccmm", "mmmmmmmm", "mmh-hmmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=flat(0),
        beats=56, threads={"WarmFront": 2, "ColdFront": 1},
        objectives=[obj("BloomedFlowers", "Gte", 4, "Candlewick"),
                    obj("FrozenCrops", "Lte", 0, "Barley")],
        bloomStrokes=3, flourishStrokes=2, flourishCells=24,
    ),
    lvl(
        id="c5-3", chapter=5, name="Sluicegate Fen",
        brief="Fill the fen and top the cistern below it without drowning the lane.",
        hint="A brook takes water past the tiles it runs through. Follow where it points.",
        map=["TTmmmmTT", "mmmmmmmm", "mmwwwwmm", "mmwwwwmm", "mmm~~mmm",
             "mmm~~mmm", "mmRRRRmm", "mmRRRRmm", "mmmmmmmm", "mm-hh-mm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(2, 2), (1, 4), (0, 2), (1, 4)]),
        reservoirCapacity=6, beats=60,
        threads={"WarmFront": 1, "ColdFront": 1, "MoistureRibbon": 1},
        objectives=[obj("WetlandWater", "Gte", 8, "Fen"),
                    obj("ReservoirWater", "Gte", 5, "Cistern"),
                    obj("FloodedTiles", "Lte", 0, "Lane")],
        bloomStrokes=3, flourishStrokes=3, flourishCells=30,
    ),

    # ---------------------------------------------------------- chapter seven
    lvl(
        id="c7-1", chapter=7, name="Snowmelt Steps",
        brief="Cap the steps in snow, then let the sun send it down to the cistern.",
        hint="Snow melts wherever the air is above freezing. Make it high and cold, then warm the slope below.",
        map=["^^^^^^^^", "^^^^^^^^", "ssssssss", "mmmmmmmm", "mmmmmmmm",
             "mmmmmmmm", "mm~~~~mm", "mmmmmmmm", "mmRRRRmm", "mmRRRRmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(4, 2), (3, 2), (2, 3), (1, 1), (0, 2), (1, 2)]),
        reservoirCapacity=6, beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "MoistureRibbon": 1},
        objectives=[obj("SnowTiles", "Gte", 6, "Step snow"),
                    obj("ReservoirWater", "Gte", 5, "Cistern")],
        bloomStrokes=4, flourishStrokes=3, flourishCells=34, reward="thawlily",
    ),
    lvl(
        id="c7-2", chapter=7, name="Whitecap Tarn",
        brief="A white cap on the horns and a full tarn beneath them.",
        hint="Moisture can be carried to cold air instead of dragging the cold air down to it.",
        map=["^^^^^^^^", "^^ss^^^^", "^^^^^^^^", "ssssssss", "mmmmmmmm",
             "mmmmmmmm", "mmmmmmmm", "mmRRRRmm", "mmRRRRmm", "mmmmmmmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(4, 3), (3, 1), (2, 3), (0, 2), (1, 3)]),
        reservoirCapacity=5, beats=60,
        threads={"WarmFront": 1, "ColdFront": 1, "MoistureRibbon": 2},
        objectives=[obj("SnowTiles", "Gte", 10, "Horn snow"),
                    obj("ReservoirWater", "Gte", 4, "Tarn")],
        bloomStrokes=4, flourishStrokes=3, flourishCells=34,
    ),
    lvl(
        id="c7-3", chapter=7, name="Meltwater Meadow",
        brief="The snowfield above feeds the meadow. Nothing else will.",
        hint="Water that arrives as meltwater still counts as a drink. The flowers only need the air to stay mild.",
        map=["^^^^^^^^", "ssssssss", "mmmmmmmm", "mmmmmmmm", "mfmffmfm",
             "mmmmmmmm", "mmmmmmmm", "mmccccmm", "mmmmmmmm", "mmh-hmmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(4, 1), (3, 1), (2, 2), (1, 4), (0, 4)]),
        beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "MoistureRibbon": 1},
        objectives=[obj("BloomedFlowers", "Gte", 4, "Meadow"),
                    obj("FrozenCrops", "Lte", 0, "Barley")],
        bloomStrokes=4, flourishStrokes=3, flourishCells=34,
    ),
    lvl(
        id="c7-4", chapter=7, name="Thawgate",
        brief="Snow on the gate, water in the pool, and the barley untouched.",
        hint="Cold belongs at the top of the map. Everything you want warm should be far below it.",
        map=["^^^^^^^^", "^^^^^^^^", "sssmmsss", "mmmmmmmm", "mm~~~~mm",
             "mmmmmmmm", "mmRRRRmm", "mmRRRRmm", "mmmmmmmm", "TTccccTT",
             "mmmmmmmm", "mmmmmmmm"],
        elevation=steps([(4, 2), (3, 1), (2, 2), (1, 1), (0, 2), (1, 4)]),
        reservoirCapacity=5, beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "MoistureRibbon": 1},
        objectives=[obj("SnowTiles", "Gte", 8, "Gate snow"),
                    obj("ReservoirWater", "Gte", 4, "Pool"),
                    obj("FrozenCrops", "Lte", 0, "Barley")],
        bloomStrokes=4, flourishStrokes=4, flourishCells=38,
    ),

    # ---------------------------------------------------------- chapter eight
    lvl(
        id="c8-1", chapter=8, name="Braided Gale",
        brief="Four mills, two ridges, one stubborn fog.",
        hint="Wind spills one tile either side of the band. Two bands can cover four rows.",
        map=["TTmmmmTT", "mWmmmmWm", "mmmmmmmm", "mmmmmmmm", "mm----mm",
             "mhvvvhmm", "mm----mm", "mmmmmmmm", "mmmmmmmm", "mWmmmmWm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=flat(0), baseTemp=0,
        fog=["00000000", "00000000", "00000000", "01111100", "02222200",
             "02222200", "02222200", "01111100", "00000000", "00000000",
             "00000000", "00000000"],
        beats=52, threads={"WindBand": 3},
        objectives=[obj("WindmillTicks", "Gte", 20, "Mills", everyWindmill=True),
                    obj("VillageFog", "Lte", 0, "Village")],
        bloomStrokes=3, flourishStrokes=3, flourishCells=34, reward="galeflax",
    ),
    lvl(
        id="c8-2", chapter=8, name="Leeward Fields",
        brief="The flowers sit in the lee of the crag. Bring the rain over to them.",
        hint="Wind pushes cloud one tile a beat. Make the cloud upwind of where you want it to fall.",
        map=["mmmmmmmm", "mmmmmmmm", "ss^^^^ss", "ss^^^^ss", "mmmmmmmm",
             "mmmmmmmm", "mfmffmfm", "mmmmmmmm", "mmmmmmmm", "mmh-hmmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(1, 2), (3, 2), (1, 3), (0, 5)]),
        beats=60,
        threads={"WarmFront": 1, "ColdFront": 1, "WindBand": 1, "MoistureRibbon": 1},
        objectives=[obj("BloomedFlowers", "Gte", 4, "Lee flowers")],
        bloomStrokes=4, flourishStrokes=3, flourishCells=34,
    ),
    lvl(
        id="c8-3", chapter=8, name="Turning Weather",
        brief="Keep the mills turning while the cistern fills. Neither can wait.",
        hint="A wind band is not only for mills. It moves your rain as well.",
        map=["TTmmmmTT", "mWmmmmWm", "mmmmmmmm", "mmmmmmmm", "mmmmmmmm",
             "mm~~~~mm", "mmmmmmmm", "mmRRRRmm", "mmRRRRmm", "mmmmmmmm",
             "TTh--hTT", "mmmmmmmm"],
        elevation=steps([(2, 3), (1, 4), (0, 2), (1, 3)]),
        reservoirCapacity=5, beats=60,
        threads={"WarmFront": 1, "ColdFront": 1, "WindBand": 1, "MoistureRibbon": 1},
        objectives=[obj("ReservoirWater", "Gte", 4, "Cistern"),
                    obj("WindmillTicks", "Gte", 18, "Mills", everyWindmill=True)],
        bloomStrokes=4, flourishStrokes=4, flourishCells=38,
    ),
    lvl(
        id="c8-4", chapter=8, name="Squall Line",
        brief="Soak the marsh. The cottages on the bank must stay above the water.",
        hint="Deep water spreads sideways. Keep the deepest part of the storm away from the bank.",
        map=["TTmmmmTT", "mmmmmmmm", "mmmmmmmm", "mwwwwwwm", "mwwwwwwm",
             "mwwwwwwm", "mmmmmmmm", "mmmmmmmm", "mh-mm-hm", "mmmmmmmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(2, 3), (0, 3), (1, 2), (2, 4)]),
        beats=60,
        threads={"WarmFront": 1, "ColdFront": 1, "WindBand": 1, "MoistureRibbon": 1},
        objectives=[obj("WetlandWater", "Gte", 12, "Marsh"),
                    obj("FloodedTiles", "Lte", 0, "Cottages")],
        bloomStrokes=4, flourishStrokes=3, flourishCells=34,
    ),

    # ----------------------------------------------------------- chapter nine
    lvl(
        id="c9-1", chapter=9, name="Glass Terrace",
        brief="Flowers on the terrace, water in the trough, mills turning above.",
        hint="Solve the highest thing first. What you make up there arrives everywhere below.",
        map=["TTmmmmTT", "mWmmmmWm", "mmmmmmmm", "mfmmmmfm", "mmmmmmmm",
             "mmfmmfmm", "mm~~~~mm", "mmRRRRmm", "mmmmmmmm", "TTh--hTT",
             "mmmmmmmm", "mmmmmmmm"],
        elevation=steps([(2, 3), (1, 3), (0, 2), (1, 4)]),
        reservoirCapacity=4, beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "WindBand": 1, "MoistureRibbon": 1},
        objectives=[obj("BloomedFlowers", "Gte", 4, "Terrace"),
                    obj("ReservoirWater", "Gte", 3, "Trough"),
                    obj("WindmillTicks", "Gte", 15, "Mills", everyWindmill=True)],
        bloomStrokes=5, flourishStrokes=4, flourishCells=42, reward="loomstar",
    ),
    lvl(
        id="c9-2", chapter=9, name="The Cold Braid",
        brief="Snow above, blooms below, and a hard line between them.",
        hint="Warm and cold cancel where they overlap. That is a tool, not a mistake.",
        map=["^^^^^^^^", "ssssssss", "mmmmmmmm", "mmmmmmmm", "mmmmmmmm",
             "mfmffmfm", "mmmmmmmm", "mmccccmm", "mmmmmmmm", "mmh-hmmm",
             "TTmmmmTT", "mmmmmmmm"],
        elevation=steps([(4, 1), (3, 1), (2, 3), (1, 3), (0, 4)]),
        beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "MoistureRibbon": 2},
        objectives=[obj("SnowTiles", "Gte", 6, "Ridge snow"),
                    obj("BloomedFlowers", "Gte", 4, "Meadow"),
                    obj("FrozenCrops", "Lte", 0, "Barley")],
        bloomStrokes=5, flourishStrokes=4, flourishCells=42,
    ),
    lvl(
        id="c9-3", chapter=9, name="Everything at Once",
        brief="Mills, marsh, cistern and blooms. The hollow is asking for all of it.",
        hint="Read the objectives in height order and weave from the top down.",
        map=["mWmmmmWm", "mmmmmmmm", "mfmmmmfm", "mmmmmmmm", "mwwwwwwm",
             "mwwwwwwm", "mm~~~~mm", "mmRRRRmm", "mmmmmmmm", "TTh--hTT",
             "mmmmmmmm", "mmmmmmmm"],
        elevation=steps([(3, 2), (2, 2), (1, 2), (0, 2), (1, 4)]),
        reservoirCapacity=4, beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "WindBand": 1, "MoistureRibbon": 2},
        objectives=[obj("WetlandWater", "Gte", 8, "Marsh"),
                    obj("ReservoirWater", "Gte", 3, "Cistern"),
                    obj("BloomedFlowers", "Gte", 2, "Banks"),
                    obj("WindmillTicks", "Gte", 15, "Mills", everyWindmill=True)],
        bloomStrokes=6, flourishStrokes=5, flourishCells=48,
    ),
    lvl(
        id="c9-4", chapter=9, name="The Last Loom",
        brief="One hollow, every rule, nothing spare.",
        hint="There is no thread here you do not need. Place the one with the fewest options first.",
        map=["^^^^^^^^", "sssmmsss", "mWmmmmWm", "mmmmmmmm", "mfmmmmfm",
             "mmm~~mmm", "mmm~~mmm", "mmRRRRmm", "mmmmmmmm", "TTccccTT",
             "mmh--hmm", "mmmmmmmm"],
        elevation=steps([(5, 1), (4, 1), (3, 2), (2, 3), (0, 2), (1, 3)]),
        reservoirCapacity=4, beats=60,
        threads={"WarmFront": 2, "ColdFront": 1, "WindBand": 1, "MoistureRibbon": 2},
        objectives=[obj("SnowTiles", "Gte", 4, "Pike snow"),
                    obj("ReservoirWater", "Gte", 3, "Pool"),
                    obj("BloomedFlowers", "Gte", 2, "Ledge"),
                    obj("WindmillTicks", "Gte", 15, "Mills", everyWindmill=True),
                    obj("FrozenCrops", "Lte", 0, "Barley")],
        bloomStrokes=6, flourishStrokes=5, flourishCells=50,
    ),
]

NEW_COLLECTIBLES = [
    {"id": "thawlily", "name": "Thaw Lily",
     "flavour": "Flowers in the puddle a snowdrift leaves behind, and never anywhere else.",
     "unlock": "Solve Snowmelt Steps", "biome": "Thaw"},
    {"id": "galeflax", "name": "Gale Flax",
     "flavour": "Its fibres lie flat in the direction of the last strong wind. Weavers use it as a compass.",
     "unlock": "Solve Braided Gale", "biome": "Ridges"},
    {"id": "loomstar", "name": "Loomstar",
     "flavour": "Six petals, each opening to a different condition. Nobody has seen all six at once.",
     "unlock": "Solve Glass Terrace", "biome": "Trials"},
]


def main():
    data = json.loads(LEVELS.read_text())
    by = {l["id"]: l for l in data["levels"]}

    # Larkspur Field: the two flower rows sat close enough for one rain band to
    # water both, so the second warm thread was decorative. Pull them apart.
    lark = by["c1-3"]
    lark["map"] = ["TTmmmmTT", "mmmmmmmm", "mmm--mmm", "mfmfmmfm", "mmmmmmmm",
                   "mmmmmmmm", "mmmmmmmm", "mmmmmmmm", "mmfmmfmm", "TmmmmmmT",
                   "TTmmmmTT", "mmmmmmmm"]
    lark["startMoisture"] = 1
    lark["objectives"][0]["target"] = 5
    lark["solution"] = []

    have = {c["index"] for c in data["chapters"]}
    for c in CHAPTERS:
        if c["index"] not in have:
            data["chapters"].append(c)

    ids = {c["id"] for c in data["collectibles"]}
    for c in NEW_COLLECTIBLES:
        if c["id"] not in ids:
            data["collectibles"].append(c)

    for lv in NEW:
        if lv["id"] in by:
            data["levels"][data["levels"].index(by[lv["id"]])] = lv
        else:
            data["levels"].append(lv)

    data["levels"].sort(key=lambda l: (l["chapter"], l["id"]))
    LEVELS.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    print(f'{len(data["levels"])} levels, {len(data["chapters"])} chapters, '
          f'{len(data["collectibles"])} collectibles')


if __name__ == "__main__":
    main()
