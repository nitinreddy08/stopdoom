# Third-party sources

Only the skill/markdown files below were copied in; the full upstream repos were
cloned, trimmed to these files, and deleted.

| Path in this repo | Upstream | Commit | License |
| --- | --- | --- | --- |
| `.claude/skills/motion-design/` | [LottieFiles/motion-design-skill](https://github.com/LottieFiles/motion-design-skill) (`skills/motion-design/`) | `f9a8a04` | MIT |
| `.claude/skills/reverse-engineer-anything/` | [morluto/rea](https://github.com/morluto/rea) (`.agents/skills/reverse-engineer-anything/`) | `e18594b` | MIT |
| `references/awesome/` | [sindresorhus/awesome](https://github.com/sindresorhus/awesome) (`readme.md` and docs; the logo/media was not copied) | `bc98e51` | CC0 |

Notes:

- `motion-design` and `reverse-engineer-anything` live under `.claude/skills/`, where
  Claude Code auto-discovers project skills.
- The `rea` skill only supplies instructions. It does **not** register the REA MCP
  server or install analysis engines; see its `references/connection-and-recovery.md`
  and https://github.com/morluto/rea if the REA tools themselves are needed.
- `rea`'s four other skills (`rea-analysis-optimization`, `rea-tool-design`,
  `rea-ci-tests-audit`, `rea-changelog-update`) are for maintaining `rea` itself and
  were not copied.
- `awesome` is a curated list, not a skill, so it is kept as a plain reference.
