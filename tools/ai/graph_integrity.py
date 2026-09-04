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

_DUPLICATE_REWARD_PATTERN = re.compile(
    r"collectibles\s*=\s*[^\n]*collectibles\s*\+\s*reward",
    re.IGNORECASE,
)
_REWARD_DEDUPE_GUARD = re.compile(
    r"reward\s*!in\s*[^\n]*collectibles|contains\s*\(\s*reward\s*\)|distinct\s*\(",
    re.IGNORECASE,
)
_NEW_SAVE_STATE_PATTERN = re.compile(
    r"data\s+class\s+SaveData\s*\([\s\S]*\b(?:terrarium|inventory|xp|weatherEcho|weather_echo|discover(?:y|ies)|growth)\w*\b",
    re.IGNORECASE,
)
_STYLE_DRIFT_PATTERNS = (
    re.compile(r"\bColor\.Magenta\b"),
    re.compile(r"\bColor\.Cyan\b"),
)
_INFINITE_ANIMATION_PATTERNS = (
    re.compile(r"\brememberInfiniteTransition\s*\("),
    re.compile(r"\binfiniteRepeatable\s*\("),
)

_SAVE_REPOSITORY_PATH = "android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt"
_SAVE_MIGRATION_PATH = "android/app/src/main/java/com/rork/weatherloom/data/SaveMigration.kt"


def _is_android(path: str) -> bool:
    return path.startswith("android/")


def _is_ui(path: str) -> bool:
    return "/ui/" in f"/{path}"


def inspect_changes(changes: Mapping[str, str]) -> list[GraphFinding]:
    """Cheap deterministic preflight. It complements tests and /graphRepair; it does not replace them."""
    findings: list[GraphFinding] = []
    normalized_changes = {str(PurePosixPath(path)): content for path, content in changes.items()}

    for normalized, content in normalized_changes.items():
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

        if "/core/reward/" in f"/{normalized}":
            if _DUPLICATE_REWARD_PATTERN.search(content) and not _REWARD_DEDUPE_GUARD.search(content):
                findings.append(
                    GraphFinding(
                        "high",
                        "Reward mutations cannot create a duplicate reward",
                        normalized,
                        "collectible reward append found without an idempotence/deduplication guard",
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

        if _is_ui(normalized):
            if "GameRepository(" in content:
                findings.append(
                    GraphFinding(
                        "medium",
                        "UI must not construct authoritative persistence state directly",
                        normalized,
                        "direct GameRepository construction detected in UI code",
                    )
                )

            for pattern in _STYLE_DRIFT_PATTERNS:
                if pattern.search(content):
                    findings.append(
                        GraphFinding(
                            "medium",
                            "UI preserves the approved Weatherloom visual language",
                            normalized,
                            f"debug/generic accent matched {pattern.pattern}",
                        )
                    )
                    break

            if any(pattern.search(content) for pattern in _INFINITE_ANIMATION_PATTERNS):
                if "reducedMotion" not in content:
                    findings.append(
                        GraphFinding(
                            "high",
                            "Reduced Motion suppresses decorative infinite animation",
                            normalized,
                            "infinite animation found without a reducedMotion guard",
                        )
                    )

    save_source = normalized_changes.get(_SAVE_REPOSITORY_PATH)
    if (
        save_source is not None
        and _NEW_SAVE_STATE_PATTERN.search(save_source)
        and _SAVE_MIGRATION_PATH not in normalized_changes
    ):
        findings.append(
            GraphFinding(
                "high",
                "New persistent SaveData state must advance through explicit migration",
                _SAVE_REPOSITORY_PATH,
                "new progression/terrarium save state detected without SaveMigration.kt in the change set",
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
