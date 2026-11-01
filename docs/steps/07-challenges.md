# Step 07 — Focus challenges

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/07-challenges.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
Three short challenges that earn extra reels: Haptic Breathing, Face-Down, Steady Timer.

## Read
- ADR: 013
- SPEC: §8.6 (unlock keys), §10 (all)
- Skills: check `.claude/skills/` for motion guidance for the breathing ring.

## Build
- [ ] `ChallengeActivity`, result recording, unlock grants within the daily budget.
- [ ] Haptic Breathing with amplitude and pulse fallbacks + "Can't hold?" mode.
- [ ] Face-Down with gravity/accelerometer/proximity handling.
- [ ] Steady Timer with shake-dependent speed.
- [ ] Random selection rules (§10.4); connect the Block overlay's "Earn more" button.

## Done when
- [ ] Unit tests for the timing/threshold logic (pure functions fed with fake sensor values).
- [ ] CI green.
- [ ] 🔶 Each challenge: complete once, fail once on purpose. Tell the agent how the vibration feels; it tunes amplitudes.
