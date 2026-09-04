from __future__ import annotations

import pytest

from tools.ai.graph_integrity import assert_clean, inspect_changes
from tools.ai.schemas import Finding, ReviewResult


def test_runtime_internet_permission_is_rejected() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/AndroidManifest.xml": (
                '<uses-permission android:name="android.permission.INTERNET" />'
            )
        }
    )
    assert any(f.severity == "critical" for f in findings)


def test_openai_key_reference_in_android_is_rejected() -> None:
    findings = inspect_changes(
        {"android/app/src/main/java/X.kt": 'val key = System.getenv("OPENAI_API_KEY")'}
    )
    assert any("developer-only" in f.invariant for f in findings)


def test_nondeterministic_reaction_rule_is_rejected() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/java/com/rork/weatherloom/core/terrarium/reaction/ReactionEngine.kt": (
                "val pick = Random().nextInt()"
            )
        }
    )
    assert any("deterministic" in f.invariant for f in findings)


def test_clean_developer_tool_change_passes() -> None:
    assert_clean({"tools/ai/orchestrator.py": "from agents import Agent"})


def test_needs_changes_requires_concrete_finding() -> None:
    with pytest.raises(ValueError):
        ReviewResult(verdict="NEEDS_CHANGES")


def test_rejected_requires_concrete_finding() -> None:
    with pytest.raises(ValueError):
        ReviewResult(verdict="REJECTED")


def test_approved_cannot_hide_unresolved_finding() -> None:
    with pytest.raises(ValueError):
        ReviewResult(
            verdict="APPROVED",
            findings=[
                Finding(
                    severity="medium",
                    invariant="Reduced Motion",
                    evidence="ambient animation still active",
                    failure_path="enable Reduced Motion",
                    required_fix="disable decorative particle animation",
                )
            ],
        )
