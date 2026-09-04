from __future__ import annotations

from dataclasses import dataclass
import os


@dataclass(frozen=True)
class RolePair:
    worker: str
    reviewer: str
    model_class: str = "worker"


ROLE_PAIRS: tuple[RolePair, ...] = (
    RolePair("AgentHarnessWorker", "AgentHarnessAdversary", "architect"),
    RolePair("DomainModelWorker", "DomainModelAdversary", "architect"),
    RolePair("PersistenceWorker", "PersistenceAdversary", "architect"),
    RolePair("RewardWorker", "RewardAdversary", "architect"),
    RolePair("WeatherEchoWorker", "WeatherEchoAdversary", "worker"),
    RolePair("ReactionWorker", "ReactionAdversary", "architect"),
    RolePair("UIWorker", "UIStyleAccessibilityAdversary", "worker"),
    RolePair("AnimationWorker", "AnimationPerformanceReducedMotionAdversary", "worker"),
    RolePair("ContentWorker", "ContentSchemaAdversary", "bulk"),
    RolePair("TestWorker", "MutationEdgeCaseAdversary", "worker"),
    RolePair("ReleaseWorker", "SupplyChainSecretAdversary", "architect"),
    RolePair("Orchestrator", "IntegrationGraphAdversary", "architect"),
)

_BY_WORKER = {pair.worker: pair for pair in ROLE_PAIRS}


def pair_for(worker_role: str) -> RolePair:
    try:
        return _BY_WORKER[worker_role]
    except KeyError as exc:
        raise ValueError(f"unknown Weatherloom worker role: {worker_role}") from exc


def validate_pair(worker_role: str, reviewer_role: str) -> RolePair:
    pair = pair_for(worker_role)
    if worker_role == reviewer_role:
        raise ValueError("a worker cannot review its own work")
    if pair.reviewer != reviewer_role:
        raise ValueError(
            f"{worker_role} must be reviewed by {pair.reviewer}, not {reviewer_role}"
        )
    return pair


def configured_model(model_class: str) -> str:
    specific = {
        "architect": "WEATHERLOOM_AI_ARCHITECT_MODEL",
        "worker": "WEATHERLOOM_AI_WORKER_MODEL",
        "bulk": "WEATHERLOOM_AI_BULK_MODEL",
    }.get(model_class)
    if specific:
        value = os.getenv(specific)
        if value:
            return value
    value = os.getenv("WEATHERLOOM_AI_MODEL")
    if value:
        return value
    raise RuntimeError(
        "No Weatherloom development model configured. Set WEATHERLOOM_AI_MODEL "
        "or the role-specific WEATHERLOOM_AI_*_MODEL variable."
    )
