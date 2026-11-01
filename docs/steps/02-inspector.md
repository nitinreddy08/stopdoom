# Step 02 — Inspector and recording real traces

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/02-inspector.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
A recording tool inside the app. You use it on your phone to capture how each app's reels look to the accessibility system. These recordings become the test data for everything after.

## Read
- ADR: 005, 019
- SPEC: §7.4 (Signal shapes only), §7.8, §13.3, §13.4, §13.5, §15.1, §15.2, §15.3

## Build
- [ ] `SignalMapper` (event → plain data, never keep the event).
- [ ] Settle-snapshot code on the Android side (§7.8), used by the recorder for now.
- [ ] Inspector screen: label, expected count, start/stop, list, share, delete (§15.1–15.2).
- [ ] JSON Lines trace writer exactly as §15.3, plus the `.expected.json` sidecar.
- [ ] Minimal disclosure screen + enable flow + restricted-settings help (§13.3–13.5).
- [ ] Write `docs/TESTING_ON_DEVICE.md` with the recording instructions below.

## Don't
No counting logic. No text content in traces (only `sig` hashes).

## Done when
- [ ] CI green.
- [ ] 🔶 You record and share these traces (skip apps you don't have):
  1. Instagram Reels: wait 2 s, then exactly 10 swipes down (expected 11).
  2. Instagram Reels: 5 down, 2 up, 2 down (expected 6).
  3. Instagram Reels with comments opened and scrolled (expected 1).
  4. Instagram home feed, scroll 20 posts (expected 0).
  5. Instagram Stories, 5 taps (expected 0).
  6. YouTube Shorts: 10 swipes (expected 11).
  7. YouTube home feed scrolling (expected 0).
  8. TikTok: 10 swipes (expected 11).
  9. Facebook Reels: 10 swipes (expected 11).
- [ ] Traces are committed to `detection/src/test/resources/traces/` (the agent can do this if you upload them).
