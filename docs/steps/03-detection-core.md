# Step 03 — Detection core (no phone needed)

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/03-detection-core.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
The pure-Kotlin counting engine, proven correct by replaying your real traces and synthetic edge-case traces.

## Read
- ADR: 005, 006, 007 (rule format only)
- SPEC: §7.4, §7.5, §7.6, §7.7, §7.9, §15.4, §15.5

## Build
- [ ] Rule models + JSON parsing + validator (bad regex, schema too new, out-of-range numbers).
- [ ] `SurfaceMatcher` (§7.6).
- [ ] `Detector.step()` reducer implementing every transition in §7.7.
- [ ] INDEX, SIGNATURE, DELTA strategies, AUTO resolution, index-jump guard.
- [ ] Synthetic traces for every edge case E1–E22.
- [ ] `TraceReplayTest` over all traces (synthetic + your real ones).
- [ ] If a real trace fails with the default structural rules: adjust rules in `rules/detection-rules.json` (not code) when possible, and explain each change in PROGRESS.md.

## Don't
No Android code in `:detection`. Don't touch the app UI.

## Done when
- [ ] Every trace replays to its expected count.
- [ ] Each E1–E22 case has a named test.
- [ ] CI green.
