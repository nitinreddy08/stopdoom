# How to build this app step by step

ADR.md and SPEC.md are **reference books**. The agent never reads them cover to cover.
Each build step lives in its own small file in `docs/steps/`. Each step file says exactly
which sections of SPEC.md and ADR.md to read, what to build, and how to know it's done.

The order in `docs/steps/` replaces the phase order in SPEC.md §20.

## The loop (repeat for every step)

1. **Start a fresh Claude Code session** for each step. A clean context is the whole point:
   the agent only loads what this step needs.
2. **Paste the prompt** at the top of the step file (section "Prompt to paste").
3. **Let it plan first.** The prompt asks the agent to show you a short plan before writing code.
   Read it. If it mentions things outside the step, tell it to drop them.
4. **Let it build and test.** It commits, pushes, and waits for CI to go green.
5. **Device gate (some steps):** the agent gives you a numbered test script. Download the APK
   from the GitHub Actions run, install it, follow the script, and report what you saw.
   Don't skip this. Bugs found here are cheap; bugs found five steps later are not.
6. **The agent updates `docs/PROGRESS.md`** (what was built, decisions made, known issues).
   That file is the memory between sessions.
7. Check the step's "Done when" list yourself. Then move to the next step file.

## If something goes wrong

- **Agent wanders into a later step:** "Stop. Only do what's in docs/steps/<this file>."
- **A test on your phone fails:** stay in the same step. Tell the agent exactly what you did and
  what number you saw. Ask it to record a trace with the Inspector if it's a counting problem.
- **The agent says the spec is wrong about an Android API:** that's allowed. It must write a
  "Proposed" ADR at the bottom of ADR.md and ask you before changing direction.
- **A session gets long and messy:** ask it to update PROGRESS.md, then start a fresh session
  with the same step prompt plus "Continue from PROGRESS.md."

## Put this in CLAUDE.md (replace the earlier one-liner)

```
This repo builds an Android app described in docs/.
- docs/ADR.md = binding decisions. docs/SPEC.md = detailed reference. Never read them fully;
  read only the sections the current step file lists.
- Work happens one step at a time from docs/steps/. Never start work from a later step.
- Start every session by reading docs/PROGRESS.md and the current step file.
- End every session by updating docs/PROGRESS.md.
- Never decompile Instagram/TikTok/YouTube/Facebook/Snapchat. Rules come from Inspector traces.
- For UI, design, and animation work, check .claude/skills/ first; SPEC.md wins on conflict.
```

## Step list

| Step | What you get | Phone test? |
|---|---|---|
| 00 | Empty app builds in CI, APK downloads | Install & open |
| 01 | Accessibility service runs, knows the foreground app | Yes |
| 02 | Inspector records scroll sessions; you record traces | **Yes, important** |
| 03 | Counting brain of the app, fully unit-tested (no phone) | No |
| 04 | Live reel count on screen and in the floating pill | **Yes, important** |
| 05 | Rules update from GitHub; "tracking is off/broken" banners | Yes |
| 06 | Daily limit and the block screen | Yes |
| 07 | The three focus challenges | Yes |
| 08 | Cool-down before loosening settings | Quick |
| 09 | Colors, fonts, the draining-UI theme | Look & feel |
| 10 | The deforming brain | Look & feel |
| 11 | Full onboarding, History, Settings, About | Fresh install |
| 12 | Friend battles | Two accounts |
| 13 | Release build, store text, ready to publish | Install release |
