This repo builds an Android app described in docs/.
- docs/ADR.md = binding decisions. docs/SPEC.md = detailed reference. Never read them fully;
  read only the sections the current step file lists.
- Work happens one step at a time from docs/steps/. Never start work from a later step.
- Start every session by reading docs/PROGRESS.md and the current step file.
- End every session by updating docs/PROGRESS.md.
- Never decompile Instagram/TikTok/YouTube/Facebook/Snapchat. Rules come from Inspector traces.
- For UI, design, and animation work, check .claude/skills/ first; SPEC.md wins on conflict.
