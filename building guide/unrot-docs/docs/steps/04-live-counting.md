# Step 04 — Live counting on the phone

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/04-live-counting.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
The real thing works: scroll reels and watch the number go up, in the app and in a small floating pill.

## Read
- ADR: 008 (Live Pill part only), 010, 020, 021
- SPEC: §7.8, §8.1, §8.2, §8.3, §8.6 (only the keys this step uses), §9.4, §9.5, §9.6

## Build
- [ ] Wire service → detector thread → outputs → snapshots (§7.8, ADR-021).
- [ ] Room entities, DAOs, `CountRepository` with idempotent writes and correct "today" (§8.1–8.3).
- [ ] Today screen: big number, per-app numbers (plain styling; design comes later).
- [ ] Overlay manager with Compose lifecycle owner (§9.4) and the Live Pill (§9.5), following the safety list (§9.6).

## Don't
No limits or blocking yet. No brain visual.

## Done when
- [ ] CI green.
- [ ] 🔶 You run the counting part of SPEC §18.2 for each app you use. Expected numbers match (±1 allowed only if explained).
- [ ] Any mismatch: record a trace of it, add it to the test folder, fix via rules or code, re-test.
