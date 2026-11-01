# Step 12 — Friend battles

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/12-battles.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
Add friends by code and compare daily counts. Optional, off by default.

## Read
- ADR: 014, 015
- SPEC: §16 (all)

## Before coding
You need a free Supabase project. The agent will write the setup guide in README first, then wait for you to create the project and add the keys to `local.properties` and GitHub secrets.

## Build
- [ ] `supabase/migrations/0001_init.sql` (verify grants against current Supabase docs for anonymous users).
- [ ] BuildConfig wiring; "not configured in this build" state.
- [ ] Join, friend code, add friend, accept/decline, leaderboard today/yesterday, leave battles.
- [ ] Sync worker and offline cached leaderboard.

## Done when
- [ ] CI green (without secrets too).
- [ ] 🔶 Two accounts (second phone or a friend) add each other and see each other's counts. Leaving battles removes your data.
