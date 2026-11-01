# Step 01 — Accessibility service skeleton

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/01-service-skeleton.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
The accessibility service can be turned on, never crashes, and correctly knows which app is in front.

## Read
- ADR: 003, 004, 019, 021
- SPEC: §3, §6.1, §6.3, §6.4, §7.1, §7.2, §7.3, §14.4

## Build
- [ ] Manifest permissions and `<queries>` (§6.1), service declaration and XML config (§6.3, §6.4).
- [ ] Placeholder disclosure strings for the service description (final copy comes in Step 11).
- [ ] Service lifecycle with heartbeat and try/catch on every entry point (§7.1).
- [ ] Event intake with own-package filter (§7.2).
- [ ] `ForegroundTracker` including dynamic add/remove of content-changed events (§7.3).
- [ ] Diagnostic log ring buffer and a simple debug screen showing: service connected?, current foreground package, last 20 log lines.

## Don't
No counting yet. No overlays. No Room.

## Done when
- [ ] CI green.
- [ ] 🔶 Phone test script covers: enabling the service (including Android 13+ "Allow restricted settings"), opening Instagram/YouTube/home screen and seeing the foreground package change correctly, pulling the notification shade (package must NOT change), opening the keyboard (must NOT change), split screen.
