# Step 08 — Commitment cool-down

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/08-commitment.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
Loosening a setting needs a 60-second cool-down (and a challenge in Strict mode). Tightening is instant.

## Read
- ADR: 009
- SPEC: §8.6, §11.5

## Build
- [ ] A classifier: for any settings change, is it loosening or tightening? (unit-tested for every key)
- [ ] Commitment screen with countdown, cancel on leave, challenge in Strict mode.
- [ ] Route all loosening changes through it.

## Done when
- [ ] Tests for every settings key.
- [ ] CI green.
- [ ] 🔶 Raise the limit → cool-down appears; lower it → instant; leave the cool-down screen → change cancelled.
