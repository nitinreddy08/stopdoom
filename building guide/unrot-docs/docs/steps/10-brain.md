# Step 10 — The brain

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/10-brain.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
The living, deforming brain that droops, wobbles, cracks, and turns grey and pixelated as you scroll.

## Read
- ADR: 012
- SPEC: §11.4, §12 (all)
- Skills: motion/design skills in `.claude/skills/`.

## Build
- [ ] Outline, polar mesh, folds (§12.1–12.3), with a Compose Preview for tuning.
- [ ] Per-frame deformation without allocations (§12.4); twitch on count increase.
- [ ] AGSL effect on Android 13+, color-matrix fallback below (§12.5).
- [ ] Frame loop only while visible; reduced-motion static mode; static bitmap renderer for the Block overlay and Pill.
- [ ] Jank fallback (§21).

## Done when
- [ ] CI green.
- [ ] 🔶 The brain renders (if blank, the agent applies the software-layer fallback from ADR-012). Drag the debug slider from 0 to 1. Scroll reels with the app in split screen and watch it twitch. Tell the agent what to change.
