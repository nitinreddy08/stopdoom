# Testing on your phone

How to install a test build, turn Unrot on, and record traces with the Inspector. Written for a
OnePlus 11R on Android 16 (OxygenOS 16); menu names on other phones may differ a little.

## 1. Install a build

1. On GitHub, open **Actions**, pick the newest green run on your branch and download the
   **app-debug** artifact. Unzip it to get `app-debug.apk`.
2. Open the APK on the phone. Builds since Step 01 share one debug key, so a new build installs
   as an **update**: no need to uninstall, and recordings and settings are kept.
3. If Play Protect says **"App blocked to protect your device"**: Play Store → profile picture →
   Play Protect → ⚙ → turn off **Scan apps with Play Protect** for the install, then turn it
   back on.

## 2. Turn Unrot on

1. Open Unrot. The **Unrot is off** card shows. Tap **Turn on Unrot**.
2. Read the disclosure screen and tap **Agree and continue**. Accessibility settings open.
3. Find **Unrot** (on OnePlus: under **Downloaded apps**) and switch it on.
4. If the switch is greyed out or says the setting is restricted, go back to Unrot. A **"Is the
   switch greyed out?"** card appears: tap **Open App info** → ⋮ (top right) → **Allow restricted
   settings**, confirm, then repeat steps 1–3.
5. The card disappears once the service runs. (Debug builds show **Accessibility service:
   Connected**.)

## 3. Record a trace

A trace records what an app's screen structure looks like to Unrot while you scroll: sizes and
positions of lists, class names, view ids, and a scrambled fingerprint of the reel in the middle.
**No text, captions, usernames or images are saved.**

For each recording:

1. In Unrot, tap **Inspector (record traces)**.
2. Tap the test you are about to do in the list. This fills in the label and the expected number
   of reels. (Or type your own.)
3. Tap **Start recording**.
4. Switch to the app (home button or recent apps), go to the right screen, and do **exactly**
   what the label says. Stay on each reel for about 2 seconds before the next swipe.
5. Come back to Unrot (recent apps) and tap **Stop recording**. The recording appears under
   **Recordings**.

Recordings stop by themselves after 5 minutes or 20 000 lines. If you make a mistake, stop,
delete the recording and do it again.

### The recordings needed (Step 02)

"Expected" counts every reel you see, including the first one.

| # | Do this | Expected |
|---|---|---|
| 1 | Instagram **Reels** tab: wait 2 s on the first reel, then exactly 10 swipes up | 11 |
| 2 | Instagram Reels: 5 swipes forward, 2 back, 2 forward | 6 |
| 3 | Instagram Reels: on one reel, open the comments, scroll the comments, close them | 1 |
| 4 | Instagram **home feed**: scroll past about 20 posts (no Reels tab) | 0 |
| 5 | Instagram **Stories**: open a story and tap through 5 stories | 0 |
| 6 | Snapchat **Spotlight**: wait 2 s, then exactly 10 swipes up | 11 |
| 7 | YouTube Shorts: 10 swipes | 11 |
| 8 | YouTube home feed scrolling | 0 |
| 9 | TikTok: 10 swipes | 11 |
| 10 | Facebook Reels: 10 swipes | 11 |

Skip apps you don't have (currently: YouTube, TikTok and Facebook). Snapchat (#6) is an extra
beyond the step's list, because it is installed.

"Swipe up" means moving to the next reel. In #2, "forward" is the next reel and "back" the
previous one; going back to a reel you already saw doesn't count again, so the answer is 6.

## 4. Send the recordings

In the Inspector, under **Recordings**:

- **Save all to Downloads** writes one zip (`unrot-traces-….zip`) to the phone's Downloads
  folder. Attach that file in the Claude chat (or upload it to GitHub, below).
- **Share all as one zip** opens the share sheet (Gmail, Drive, Telegram…).
- **Share** on one recording sends that trace and its `.expected.json`.

Upload to GitHub (if the chat won't take the file): open the repository in a browser, switch to
the work branch, open `detection/src/test/resources/traces/`, then **Add file → Upload files**
and pick the files (unzip first).

The agent unzips them into `detection/src/test/resources/traces/`, where every build checks them
(`RecordedTracesTest`) and, from Step 03, replays them to test the counting.

## 5. Checks carried over from Step 01

Do these when convenient; report anything odd from the debug screen's log.

- **Split screen:** put two apps in split screen and tap one half, then the other. The debug
  screen's **Foreground app** should follow the half you tapped.
- **Long lock:** lock the phone for 5 minutes in one go. After unlocking, **Connected since**
  should be unchanged.
- **Update install:** this build should install over the previous one without uninstalling,
  and Unrot should still be switched on in Accessibility settings afterwards.
