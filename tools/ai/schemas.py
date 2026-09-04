from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, field_validator, model_validator

Severity = Literal["low", "medium", "high", "critical"]
Verdict = Literal["APPROVED", "NEEDS_CHANGES", "REJECTED"]


class Finding(BaseModel):
    severity: Severity
    invariant: str = Field(min_length=1)
    evidence: str = Field(min_length=1)
    failure_path: str = Field(min_length=1)
    required_fix: str = Field(min_length=1)


class ReviewResult(BaseModel):
    verdict: Verdict
    findings: list[Finding] = Field(default_factory=list)

    @model_validator(mode="after")
    def findings_match_verdict(self) -> "ReviewResult":
        if self.verdict == "APPROVED" and self.findings:
            raise ValueError("APPROVED reviews cannot contain unresolved findings")
        if self.verdict in {"NEEDS_CHANGES", "REJECTED"} and not self.findings:
            raise ValueError(f"{self.verdict} reviews must explain at least one finding")
        return self


class WorkItem(BaseModel):
    id: str = Field(min_length=1)
    worker_role: str = Field(min_length=1)
    reviewer_role: str = Field(min_length=1)
    prompt: str = Field(min_length=1)
    targets: tuple[str, ...] = ()
    invariants: tuple[str, ...] = ()

    @field_validator("targets")
    @classmethod
    def unique_targets(cls, targets: tuple[str, ...]) -> tuple[str, ...]:
        if len(targets) != len(set(targets)):
            raise ValueError("work item targets must be unique")
        return targets


class PairResult(BaseModel):
    work_item_id: str
    artifact: str
    review: ReviewResult
    attempts: int = Field(ge=1)
