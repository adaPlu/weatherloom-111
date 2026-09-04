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


def test_duplicate_reward_mutation_is_rejected() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/java/com/rork/weatherloom/core/reward/RewardService.kt": (
                "val next = save.copy(collectibles = save.collectibles + reward)"
            )
        }
    )
    assert any("duplicate reward" in f.invariant.lower() for f in findings)


def test_new_save_state_without_migration_is_rejected() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/java/com/rork/weatherloom/data/GameRepository.kt": (
                "data class SaveData(val terrariumInventory: List<String> = emptyList())"
            )
        }
    )
    assert any("migration" in f.invariant.lower() for f in findings)


def test_nondeterministic_reaction_rule_is_rejected() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/java/com/rork/weatherloom/core/terrarium/reaction/ReactionEngine.kt": (
                "val pick = Random().nextInt()"
            )
        }
    )
    assert any("deterministic" in f.invariant for f in findings)


def test_weatherloom_style_drift_is_rejected() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/java/com/rork/weatherloom/ui/screens/TerrariumScreen.kt": (
                "val accent = Color.Magenta"
            )
        }
    )
    assert any("visual language" in f.invariant.lower() for f in findings)


def test_unconditional_infinite_animation_is_rejected_for_reduced_motion() -> None:
    findings = inspect_changes(
        {
            "android/app/src/main/java/com/rork/weatherloom/ui/terrarium/AmbientClouds.kt": (
                "val transition = rememberInfiniteTransition(); "
                "val alpha = transition.animateFloat(animationSpec = infiniteRepeatable(tween(1000)))"
            )
        }
    )
    assert any("reduced motion" in f.invariant.lower() for f in findings)


def test_reduced_motion_guard_allows_ambient_animation() -> None:
    assert_clean(
        {
            "android/app/src/main/java/com/rork/weatherloom/ui/terrarium/AmbientClouds.kt": (
                "if (!reducedMotion) { val transition = rememberInfiniteTransition(); "
                "transition.animateFloat(animationSpec = infiniteRepeatable(tween(1000))) }"
            )
        }
    )


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
