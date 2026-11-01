# Step 06 — Daily limit and blocking

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/06-blocking.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
Reach your limit and reels get covered. Back still works, the video's sound pauses, and Strict mode can block the whole app.

## Read
- ADR: 008, 009
- SPEC: §8.6, §9.1, §9.2, §9.3, §9.4, §9.6, §17

## Build
- [ ] Limit settings (plain UI for now): daily limit, blocking on/off, strict mode.
- [ ] `AllowanceCalculator` with tests.
- [ ] `EnforcementController` (§9.2).
- [ ] Block overlay (§9.3) with audio focus and re-entry debounce; BlockActivity fallback.
- [ ] Notifications at warning % and at limit (§17), including the Android 13 permission request.
- [ ] "Earn more reels" button can be a stub that says "Challenges come in Step 07".

## Done when
- [ ] CI green.
- [ ] 🔶 Limit 5: block appears at the right moment; Back leaves reels and the block disappears; audio pauses (note which apps don't respect it); DMs still work; Strict mode blocks the whole app; rotate and lock the phone while blocked: nothing breaks.
