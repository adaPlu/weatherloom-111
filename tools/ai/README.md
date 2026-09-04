# Weatherloom OpenAI development harness

This package is **developer tooling only**. It must never be imported by the Android app, added to the APK, or used to justify the Android `INTERNET` permission.

## Purpose

The harness implements the approved worker -> independent adversarial reviewer -> repair loop for Weatherloom. Each work-product role has one required reviewer, and branch convergence is separately challenged by `IntegrationGraphAdversary`. Independent work items may run concurrently only when their declared file/state targets do not overlap.

## Setup

```bash
python -m venv .venv
source .venv/bin/activate  # Windows PowerShell: .venv\Scripts\Activate.ps1
pip install -r tools/ai/requirements.txt
```

For **live** agent runs, configure credentials/model selection only in the developer environment:

```bash
export OPENAI_API_KEY=...
export WEATHERLOOM_AI_MODEL=<model available to your OpenAI project>
```

Optional role overrides: `WEATHERLOOM_AI_ARCHITECT_MODEL`, `WEATHERLOOM_AI_WORKER_MODEL`, and `WEATHERLOOM_AI_BULK_MODEL`.

No API key or model identifier is committed into Android source. Tests do not require a live key.

## Tests

```bash
pytest -q tools/ai/tests
```

The suite uses the Agents SDK `ScriptedModel` with tracing disabled, so deterministic tests make no model API request. CI must remain green without `OPENAI_API_KEY`.

## Trace/privacy contract

Live runs use `trace_include_sensitive_data=False`. Do not put keystores, signing passwords, API keys, tokens, private credentials, or unrelated personal data into prompts. For deterministic tests, tracing is disabled entirely.

## Review contract

Reviewers return a structured `ReviewResult`:

- `APPROVED`: no unresolved findings;
- `NEEDS_CHANGES`: one or more concrete repair findings, followed by another worker/reviewer pass;
- `REJECTED`: stop the feature and escalate a design blocker.

A worker cannot review itself. The role registry enforces the approved worker/reviewer mapping. `run_parallel()` rejects overlapping declared targets before starting work.

## Graph integrity

`graph_integrity.py` is a cheap deterministic preflight for especially dangerous architectural regressions such as Android runtime OpenAI/network references and nondeterministic ReactionEngine inputs. It complements tests and the project `/graphRepair` gate; it is not a substitute for either.

## Sandbox isolation

Sandbox Agents may be added as an optional execution backend for isolated coding work. The core orchestrator and deterministic tests intentionally do not depend on beta sandbox APIs. Review should operate on an immutable artifact/diff rather than a builder's mutable workspace.
