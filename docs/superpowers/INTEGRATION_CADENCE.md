# Weatherloom Integration Cadence

This repository uses a short integration loop during active feature development.

## Branch flow

1. Implement a coherent feature on a `feature/*` branch.
2. Run the full Weatherloom CI gate on that feature branch.
3. Merge the completed feature into `develop` only when CI is green.
4. After every **one or two completed features**, merge green `develop` into `main`.
5. Re-run the full CI gate on `main` after each integration checkpoint.

## Rules

- Do not allow more than two completed features to accumulate on `develop` without a `main` integration checkpoint.
- Never merge a red CI state forward.
- `main` should remain releasable or very close to releasable.
- Production signing secrets and other release credentials are never committed to any branch.
- If two features are tightly coupled and only make sense together, they count as one integration batch.
- Documentation-only commits do not force an integration checkpoint, but may ride with the next feature batch.

## Current program increment

For `feature/release-readiness`, the first integration checkpoint is scheduled after Task 1 or Task 2 of the production-readiness plan. The exact point depends on whether the production-signing skeleton can be completed and validated without requiring unavailable secrets. In all cases, no more than two completed feature tasks will remain only on `develop` before `develop` is merged to `main`.
