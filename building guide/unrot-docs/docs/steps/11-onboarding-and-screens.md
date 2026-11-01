# Step 11 — Onboarding, History, Settings, About

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/11-onboarding-and-screens.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
A finished-feeling app: first-run onboarding, history charts, every setting, and the About screen.

## Read
- ADR: 010, 019
- SPEC: §8.4, §8.5, §11.2, §11.3, §13 (all)

## Build
- [ ] Full onboarding flow (§13.1) with final disclosure copy (§13.3) and OEM battery guidance (§13.6).
- [ ] History: 7/30 days, day detail with hourly chart (drawn with Canvas).
- [ ] Settings complete (all keys in §8.6), export/import, delete all data.
- [ ] About: version, rules version, source link, license, privacy, "not a medical measure".
- [ ] Final PRIVACY.md text.

## Done when
- [ ] CI green.
- [ ] 🔶 Uninstall, reinstall, go through onboarding from scratch. Try "Not now" at the disclosure. Export and re-import your data.
