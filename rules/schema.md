# Detection rules schema (`detection-rules.json`)

Rules are data, never code (ADR-007). The app ships `rules/detection-rules.json` inside the
APK (copied into assets by the `copyRules` Gradle task) and can later download a newer copy
from this repo. The matching logic that uses these fields is in SPEC §7.6.

## Top level

| Field | Type | Meaning |
|---|---|---|
| `schemaVersion` | integer | Shape of this file. Currently `1`. Bumped only when fields change incompatibly. |
| `rulesVersion` | string | Version of the rule contents, `YYYY-MM-DD.N` (e.g. `2026-11-01.1`). Bump on every rules change. |
| `apps` | array of App | One entry per tracked app. |

## App

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable app id, lowercase (e.g. `instagram`). |
| `displayName` | string | Human-readable name (e.g. `Instagram`). |
| `packages` | array of string | Android package names that belong to this app. Non-empty. |
| `testedVersionCodes` | `null` or `{ "min": long, "max": long }` | App version codes the rules were verified against (used by breakage detection, Step 05). `null` = not verified yet. |
| `surfaces` | array of Surface | Reel/short surfaces in this app. If several match, the first in file order wins. |

## Surface

| Field | Type | Meaning |
|---|---|---|
| `id` | string | Stable surface id, `<appId>_<surface>` (e.g. `instagram_reels`). Used as `surfaceId` elsewhere. |
| `viewIdRegex` | array of string | Regexes the scrolled view's resource id must match (any one). Empty = no constraint. |
| `classNameRegex` | array of string | Regexes the scrolled view's class name must match (any one). Empty = no constraint. |
| `minHeightRatio` | number 0–1 | Minimum scrolled-view height as a fraction of the app window height. |
| `minWidthRatio` | number 0–1 | Minimum scrolled-view width as a fraction of the app window width. |
| `fullItemRatio` | number 0–1 | At settle, exactly one child must be at least this fraction of the view's height. |
| `requireVertical` | boolean | If `true`, the session must show vertical scrolling (`deltaY != 0` or an index change); `false` skips that check (SPEC §7.6 rule 4). |
| `settleMs` | integer ≥ 0 | Settle delay in milliseconds (counting state machine, SPEC §7.7). |
| `entryDwellMs` | integer ≥ 0 | Entry dwell in milliseconds (SPEC §7.7). |
| `strategy` | `AUTO` \| `INDEX` \| `SIGNATURE` \| `DELTA` | Counting strategy (ADR-006). |
| `deltaThresholdRatio` | number 0–1 | Threshold used by the `DELTA` strategy (ADR-006, SPEC §7.7). |

## Default structural rule

Until traces recorded on a real phone refine them (SPEC §15), every surface uses the
structural defaults from SPEC §7.6: empty `viewIdRegex` and `classNameRegex`,
`minHeightRatio 0.75`, `minWidthRatio 0.9`, `fullItemRatio 0.9`, `requireVertical true`,
`settleMs 300`, `entryDwellMs 1000`, `strategy AUTO`, `deltaThresholdRatio 0.6`.

View ids and class names are never guessed. They are added only from Inspector traces.
