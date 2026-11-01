# Step 13 — Release prep

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/13-release.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
A signed release APK on a GitHub Release, and everything ready for Play Store and F-Droid submission.

## Read
- ADR: 016, 017
- SPEC: §19.4, §19.5, §19.6

## Build
- [ ] R8 with keep rules; release signing from environment variables; `release.yml` on version tags.
- [ ] Reproducibility check (build twice, compare hashes) and fixes.
- [ ] Store/F-Droid metadata, `docs/PLAY_SUBMISSION.md` (accessibility declaration + demo video script).
- [ ] CONTRIBUTING.md (how to record and submit traces), THIRD_PARTY_NOTICES.md.
- [ ] License check of every `.claude/skills/` folder; report anything missing before the repo goes public.
- [ ] Secret scan of the git history.

## Done when
- [ ] Draft GitHub Release with a signed APK.
- [ ] 🔶 Install the release APK (uninstall the debug one first) and run a quick smoke test: count, block, one challenge.
