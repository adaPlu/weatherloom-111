from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable, Sequence
from typing import TypeAlias

from agents import Agent, RunConfig, Runner

from .roles import configured_model, validate_pair
from .schemas import PairResult, ReviewResult, WorkItem

WorkerRun: TypeAlias = Callable[[WorkItem, str | None], Awaitable[str]]
ReviewerRun: TypeAlias = Callable[[WorkItem, str], Awaitable[ReviewResult]]


class ReviewRejected(RuntimeError):
    pass


class TargetOverlapError(ValueError):
    pass


def _review_prompt(item: WorkItem, artifact: str) -> str:
    invariants = "\n".join(f"- {v}" for v in item.invariants) or "- No extra invariants declared."
    return f"""You are {item.reviewer_role}, the independent adversarial reviewer for Weatherloom.
You did not author this work. Try to disprove correctness rather than rubber-stamp it.

Work item: {item.id}
Worker: {item.worker_role}
Declared targets: {', '.join(item.targets) or '(none)'}
Invariants:\n{invariants}

Review the immutable artifact below. Return the structured ReviewResult only.

ARTIFACT\n{artifact}
"""


def _repair_prompt(item: WorkItem, previous_artifact: str, review: ReviewResult) -> str:
    findings = "\n".join(
        f"- [{f.severity}] {f.invariant}: {f.required_fix} (evidence: {f.evidence})"
        for f in review.findings
    )
    return f"""Revise your prior Weatherloom work for {item.id}.
Do not broaden scope. Repair every reviewer finding and preserve all declared invariants.

PRIOR ARTIFACT\n{previous_artifact}

REVIEW FINDINGS\n{findings}
"""


async def run_review_loop(
    item: WorkItem,
    worker_run: WorkerRun,
    reviewer_run: ReviewerRun,
    *,
    max_attempts: int = 3,
) -> PairResult:
    validate_pair(item.worker_role, item.reviewer_role)
    if max_attempts < 1:
        raise ValueError("max_attempts must be at least 1")

    feedback: str | None = None
    artifact = ""
    for attempt in range(1, max_attempts + 1):
        artifact = await worker_run(item, feedback)
        review = await reviewer_run(item, artifact)
        if review.verdict == "APPROVED":
            return PairResult(
                work_item_id=item.id,
                artifact=artifact,
                review=review,
                attempts=attempt,
            )
        if review.verdict == "REJECTED":
            raise ReviewRejected(f"{item.id} rejected by {item.reviewer_role}")
        feedback = _repair_prompt(item, artifact, review)

    raise ReviewRejected(f"{item.id} did not reach APPROVED within {max_attempts} attempts")


def validate_parallel_targets(items: Sequence[WorkItem]) -> None:
    owner: dict[str, str] = {}
    conflicts: list[str] = []
    for item in items:
        for target in item.targets:
            previous = owner.setdefault(target, item.id)
            if previous != item.id:
                conflicts.append(f"{target}: {previous} <-> {item.id}")
    if conflicts:
        raise TargetOverlapError(
            "parallel Weatherloom work items have overlapping targets: " + "; ".join(conflicts)
        )


async def run_parallel(
    items: Sequence[WorkItem],
    runner: Callable[[WorkItem], Awaitable[PairResult]],
) -> list[PairResult]:
    validate_parallel_targets(items)
    return list(await asyncio.gather(*(runner(item) for item in items)))


def _integration_artifact(items: Sequence[WorkItem], results: Sequence[PairResult]) -> str:
    by_id = {item.id: item for item in items}
    blocks: list[str] = []
    for result in sorted(results, key=lambda value: value.work_item_id):
        item = by_id[result.work_item_id]
        invariants = "\n".join(f"- {value}" for value in item.invariants) or "- none"
        targets = "\n".join(f"- {value}" for value in item.targets) or "- none"
        blocks.append(
            "\n".join(
                (
                    f"WORK ITEM {item.id}",
                    f"worker={item.worker_role}",
                    f"reviewer={item.reviewer_role}",
                    f"pair_verdict={result.review.verdict}",
                    f"attempts={result.attempts}",
                    "targets:",
                    targets,
                    "invariants:",
                    invariants,
                    "artifact:",
                    result.artifact,
                )
            )
        )
    return "\n\n---\n\n".join(blocks)


async def run_checkpoint(
    items: Sequence[WorkItem],
    pair_runner: Callable[[WorkItem], Awaitable[PairResult]],
    integration_reviewer: ReviewerRun,
) -> tuple[list[PairResult], ReviewResult]:
    """Run independent pairs, then require IntegrationGraphAdversary approval before promotion."""
    if not items:
        raise ReviewRejected("checkpoint cannot promote an empty work set")

    results = await run_parallel(items, pair_runner)
    if any(result.review.verdict != "APPROVED" for result in results):
        raise ReviewRejected("checkpoint contains work without pair-level APPROVED review")

    targets = tuple(sorted({target for item in items for target in item.targets}))
    invariants = tuple(sorted({value for item in items for value in item.invariants}))
    integration_item = WorkItem(
        id="checkpoint-integration",
        worker_role="Orchestrator",
        reviewer_role="IntegrationGraphAdversary",
        prompt="Challenge the converged Weatherloom checkpoint before promotion.",
        targets=targets,
        invariants=invariants,
    )
    validate_pair(integration_item.worker_role, integration_item.reviewer_role)

    integration_review = await integration_reviewer(
        integration_item,
        _integration_artifact(items, results),
    )
    if integration_review.verdict != "APPROVED":
        raise ReviewRejected(
            "IntegrationGraphAdversary did not approve checkpoint convergence"
        )

    return results, integration_review


async def live_worker(item: WorkItem, feedback: str | None = None) -> str:
    pair = validate_pair(item.worker_role, item.reviewer_role)
    model = configured_model(pair.model_class)
    prompt = feedback or item.prompt
    agent = Agent(
        name=item.worker_role,
        instructions=(
            "Implement only the requested Weatherloom work product. Preserve the deterministic "
            "puzzle core, offline-first Android runtime, approved felt/wool visual language, and "
            "declared file targets. Do not include secrets."
        ),
        model=model,
    )
    result = await Runner.run(
        agent,
        prompt,
        run_config=RunConfig(
            workflow_name=f"Weatherloom/{item.id}/{item.worker_role}",
            trace_include_sensitive_data=False,
        ),
    )
    return str(result.final_output)


async def live_reviewer(item: WorkItem, artifact: str) -> ReviewResult:
    pair = validate_pair(item.worker_role, item.reviewer_role)
    model = configured_model("architect" if pair.model_class != "bulk" else "worker")
    agent = Agent(
        name=item.reviewer_role,
        instructions=(
            "Act as an independent adversarial code/design reviewer. Seek concrete failure paths. "
            "APPROVED means no unresolved finding. Never expose or request secrets."
        ),
        model=model,
        output_type=ReviewResult,
    )
    result = await Runner.run(
        agent,
        _review_prompt(item, artifact),
        run_config=RunConfig(
            workflow_name=f"Weatherloom/{item.id}/{item.reviewer_role}",
            trace_include_sensitive_data=False,
        ),
    )
    return result.final_output_as(ReviewResult, raise_if_incorrect_type=True)


async def run_live_pair(item: WorkItem, *, max_attempts: int = 3) -> PairResult:
    return await run_review_loop(
        item,
        live_worker,
        live_reviewer,
        max_attempts=max_attempts,
    )
