from __future__ import annotations

import asyncio

import pytest
from agents import Agent, RunConfig, Runner
from agents.testing import ScriptedModel, assistant_message

import tools.ai.orchestrator as orchestrator
from tools.ai.orchestrator import (
    ReviewRejected,
    TargetOverlapError,
    run_parallel,
    run_review_loop,
    validate_parallel_targets,
)
from tools.ai.schemas import Finding, PairResult, ReviewResult, WorkItem


@pytest.mark.asyncio
async def test_agents_sdk_scripted_model_never_needs_live_api() -> None:
    model = ScriptedModel([[assistant_message("deterministic artifact")]])
    agent = Agent(name="Harness test worker", model=model)
    result = await Runner.run(
        agent,
        "produce artifact",
        run_config=RunConfig(tracing_disabled=True),
    )
    assert result.final_output == "deterministic artifact"
    model.assert_complete()


@pytest.mark.asyncio
async def test_review_loop_single_pass_approves() -> None:
    item = WorkItem(
        id="domain-contract",
        worker_role="DomainModelWorker",
        reviewer_role="DomainModelAdversary",
        prompt="implement domain contract",
        targets=("TerrariumItem.kt",),
    )

    async def worker(_item: WorkItem, _feedback: str | None) -> str:
        return "domain artifact"

    async def reviewer(_item: WorkItem, artifact: str) -> ReviewResult:
        assert artifact == "domain artifact"
        return ReviewResult(verdict="APPROVED")

    result = await run_review_loop(item, worker, reviewer)
    assert result.review.verdict == "APPROVED"
    assert result.attempts == 1


@pytest.mark.asyncio
async def test_review_loop_repairs_then_approves() -> None:
    item = WorkItem(
        id="save-migration",
        worker_role="PersistenceWorker",
        reviewer_role="PersistenceAdversary",
        prompt="implement migration",
        targets=("SaveMigration.kt",),
    )
    worker_calls = 0
    reviewer_calls = 0

    async def worker(_item: WorkItem, feedback: str | None) -> str:
        nonlocal worker_calls
        worker_calls += 1
        return "fixed" if feedback else "broken"

    async def reviewer(_item: WorkItem, artifact: str) -> ReviewResult:
        nonlocal reviewer_calls
        reviewer_calls += 1
        if artifact == "broken":
            return ReviewResult(
                verdict="NEEDS_CHANGES",
                findings=[
                    Finding(
                        severity="high",
                        invariant="migration preserves settings",
                        evidence="settings dropped",
                        failure_path="load schema 1",
                        required_fix="copy all settings",
                    )
                ],
            )
        return ReviewResult(verdict="APPROVED")

    result = await run_review_loop(item, worker, reviewer)
    assert result.review.verdict == "APPROVED"
    assert result.attempts == 2
    assert worker_calls == 2
    assert reviewer_calls == 2


def test_worker_cannot_self_review() -> None:
    item = WorkItem(
        id="bad-pair",
        worker_role="PersistenceWorker",
        reviewer_role="PersistenceWorker",
        prompt="x",
    )

    async def worker(_item: WorkItem, _feedback: str | None) -> str:
        return "x"

    async def reviewer(_item: WorkItem, _artifact: str) -> ReviewResult:
        return ReviewResult(verdict="APPROVED")

    with pytest.raises(ValueError, match="cannot review its own work"):
        asyncio.run(run_review_loop(item, worker, reviewer))


def test_parallel_work_rejects_overlapping_targets() -> None:
    items = [
        WorkItem(
            id="a",
            worker_role="TestWorker",
            reviewer_role="MutationEdgeCaseAdversary",
            prompt="a",
            targets=("shared.kt",),
        ),
        WorkItem(
            id="b",
            worker_role="UIWorker",
            reviewer_role="UIStyleAccessibilityAdversary",
            prompt="b",
            targets=("shared.kt",),
        ),
    ]
    with pytest.raises(TargetOverlapError, match="shared.kt"):
        validate_parallel_targets(items)


@pytest.mark.asyncio
async def test_parallel_work_runs_independent_pairs() -> None:
    items = [
        WorkItem(
            id="a",
            worker_role="TestWorker",
            reviewer_role="MutationEdgeCaseAdversary",
            prompt="a",
            targets=("a.kt",),
        ),
        WorkItem(
            id="b",
            worker_role="UIWorker",
            reviewer_role="UIStyleAccessibilityAdversary",
            prompt="b",
            targets=("b.kt",),
        ),
    ]
    started: list[str] = []
    release = asyncio.Event()

    async def runner(item: WorkItem) -> PairResult:
        started.append(item.id)
        if len(started) == 2:
            release.set()
        await asyncio.wait_for(release.wait(), timeout=1)
        return PairResult(
            work_item_id=item.id,
            artifact=item.id,
            review=ReviewResult(verdict="APPROVED"),
            attempts=1,
        )

    results = await run_parallel(items, runner)
    assert {result.work_item_id for result in results} == {"a", "b"}


@pytest.mark.asyncio
async def test_checkpoint_runs_integration_graph_gate_before_promotion() -> None:
    run_checkpoint = getattr(orchestrator, "run_checkpoint", None)
    assert callable(run_checkpoint), "checkpoint promotion must have an IntegrationGraphAdversary gate"

    items = [
        WorkItem(
            id="save",
            worker_role="PersistenceWorker",
            reviewer_role="PersistenceAdversary",
            prompt="save",
            targets=("SaveMigration.kt",),
            invariants=("no lost updates",),
        )
    ]

    async def pair_runner(item: WorkItem) -> PairResult:
        return PairResult(
            work_item_id=item.id,
            artifact="approved save artifact",
            review=ReviewResult(verdict="APPROVED"),
            attempts=1,
        )

    integration_calls: list[WorkItem] = []

    async def integration_reviewer(item: WorkItem, artifact: str) -> ReviewResult:
        integration_calls.append(item)
        assert item.worker_role == "Orchestrator"
        assert item.reviewer_role == "IntegrationGraphAdversary"
        assert "approved save artifact" in artifact
        assert "no lost updates" in artifact
        return ReviewResult(verdict="APPROVED")

    results, integration_review = await run_checkpoint(
        items,
        pair_runner,
        integration_reviewer,
    )

    assert len(results) == 1
    assert integration_review.verdict == "APPROVED"
    assert len(integration_calls) == 1


@pytest.mark.asyncio
async def test_checkpoint_cannot_promote_failed_integration_review() -> None:
    run_checkpoint = getattr(orchestrator, "run_checkpoint", None)
    assert callable(run_checkpoint), "checkpoint promotion must have an IntegrationGraphAdversary gate"

    items = [
        WorkItem(
            id="save",
            worker_role="PersistenceWorker",
            reviewer_role="PersistenceAdversary",
            prompt="save",
            targets=("SaveMigration.kt",),
        )
    ]

    async def pair_runner(item: WorkItem) -> PairResult:
        return PairResult(
            work_item_id=item.id,
            artifact="approved save artifact",
            review=ReviewResult(verdict="APPROVED"),
            attempts=1,
        )

    async def integration_reviewer(_item: WorkItem, _artifact: str) -> ReviewResult:
        return ReviewResult(
            verdict="NEEDS_CHANGES",
            findings=[
                Finding(
                    severity="high",
                    invariant="cross-feature state is compatible",
                    evidence="two features claim the same target",
                    failure_path="checkpoint convergence",
                    required_fix="resolve integration ownership before promotion",
                )
            ],
        )

    with pytest.raises(ReviewRejected, match="IntegrationGraphAdversary"):
        await run_checkpoint(items, pair_runner, integration_reviewer)
