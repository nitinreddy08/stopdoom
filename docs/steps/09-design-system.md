# Step 09 — Design system

## Prompt to paste

> Read docs/PROGRESS.md and docs/steps/09-design-system.md. Then read only the SPEC.md and ADR.md sections that step lists.
> Show me a short plan (files you'll create or change, tests you'll write) and wait for my OK.
> After I approve: build it, run the tests, commit, push, and make sure CI is green.
> Then check every item in "Done when", update docs/PROGRESS.md, and tell me what to test on my phone (if this step has a device gate).
> Do not start any other step.

## Goal
The app's look: fonts, colors, and the signature effect where the whole UI drains to grey as your count rises.

## Read
- Skills: read the design/motion skills in `.claude/skills/` first.
- SPEC: §11.1, §11.2, §11.3 (layouts), §11.4

## Build
- [ ] Bundle Bricolage Grotesque and Atkinson Hyperlegible (OFL files + license).
- [ ] OKLab color lerp helper with tests.
- [ ] Theme that takes severity and produces all colors; dark and light.
- [ ] Contrast check test at severity 0, 0.5, 1.
- [ ] Restyle Today, Block overlay, Live Pill, challenge screens with the theme (brain area can stay a placeholder).
- [ ] Debug-only severity slider in Advanced settings.

## Done when
- [ ] CI green.
- [ ] 🔶 Drag the debug slider: the whole app drains from vivid to grey smoothly. You're happy with how it looks (iterate here as much as you want).
