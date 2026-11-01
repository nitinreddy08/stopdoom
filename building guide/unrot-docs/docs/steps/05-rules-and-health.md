# Step 05 — Rules updates and health banners

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/05-rules-and-health.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
Detection can be fixed without a new app release, and the app tells you clearly when tracking is off or broken.

## Read
- ADR: 007, 009 (protection gap part)
- SPEC: §8.4, §13.2, §14.1, §14.2, §14.3

## Build
- [ ] `RulesRepository`: bundled → last-known-good → remote; validator reused from Step 03.
- [ ] `RulesFetchWorker` (12 h, network constraint) + "Update rules now" + offline mode setting.
- [ ] Ask the user for the GitHub owner/repo for the rules URL (SPEC §23).
- [ ] `ServiceHealthMonitor`, protection gaps, banners on Today.
- [ ] Breakage detector and the prefilled GitHub issue "Report" flow.
- [ ] Retention worker (§8.4).

## Done when
- [ ] Validator tests cover broken JSON, wrong schema version, invalid regex.
- [ ] CI green.
- [ ] 🔶 Turn the service off → red banner; on → banner gone and a gap is recorded. Push a harmless rules change → "Update rules now" shows the new version in Settings.
