from __future__ import annotations

from dataclasses import dataclass
from pathlib import PurePosixPath
from typing import Mapping
import re


@dataclass(frozen=True)
class GraphFinding:
    severity: str
    invariant: str
    path: str
    evidence: str


_RANDOM_PATTERNS = (
    re.compile(r"\bRandom\s*\("),
    re.compile(r"\bMath\.random\s*\("),
    re.compile(r"\bSystem\.currentTimeMillis\s*\("),
    re.compile(r"\bSystem\.nanoTime\s*\("),
)


def _is_android(path: str) -> bool:
    return path.startswith("android/")


def inspect_changes(changes: Mapping[str, str]) -> list[GraphFinding]:
    """Cheap deterministic preflight. It complements tests and /graphRepair; it does not replace them."""
    findings: list[GraphFinding] = []
    for path, content in changes.items():
        normalized = str(PurePosixPath(path))
        if _is_android(normalized):
            if "android.permission.INTERNET" in content:
                findings.append(
                    GraphFinding(
                        "critical",
                        "Android runtime remains offline-first",
                        normalized,
                        "android.permission.INTERNET introduced",
                    )
                )
            if "OPENAI_API_KEY" in content or re.search(r"\b(?:openai|agents)\b", content, re.I):
                findings.append(
                    GraphFinding(
                        "critical",
                        "OpenAI tooling stays developer-only under tools/ai",
                        normalized,
                        "OpenAI runtime/key reference detected in Android source",
                    )
                )

        if "/core/terrarium/reaction/" in f"/{normalized}":
            for pattern in _RANDOM_PATTERNS:
                if pattern.search(content):
                    findings.append(
                        GraphFinding(
                            "high",
                            "ReactionEngine remains deterministic for a fixed snapshot",
                            normalized,
                            f"nondeterministic source matched {pattern.pattern}",
                        )
                    )
                    break

        if "/ui/" in f"/{normalized}" and "GameRepository(" in content:
            findings.append(
                GraphFinding(
                    "medium",
                    "UI must not construct authoritative persistence state directly",
                    normalized,
                    "direct GameRepository construction detected in UI code",
                )
            )

    return findings


def assert_clean(changes: Mapping[str, str]) -> None:
    findings = inspect_changes(changes)
    if findings:
        rendered = "\n".join(
            f"[{f.severity}] {f.invariant}: {f.path}: {f.evidence}" for f in findings
        )
        raise ValueError(f"Weatherloom graph-integrity preflight failed:\n{rendered}")
