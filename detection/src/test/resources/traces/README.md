# Recorded traces

Real recordings made with the in-app Inspector (SPEC §15, ADR-005). Each recording is a pair:

- `name.jsonl`: one JSON object per line (format: SPEC §15.3, `TraceFormat.kt`).
- `name.expected.json`: how many reels were really watched, the app, notes, the app's version
  code and the phone.

Traces hold screen structure only (class names, view ids, bounds, list indexes, a hash). Never
text, captions, usernames or images. `RecordedTracesTest` checks every file here is well formed;
from Step 03, `TraceReplayTest` replays them and checks the count.

How to record: `docs/TESTING_ON_DEVICE.md`.
