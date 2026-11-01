package app.unrot.inspector

/** The recordings asked for in `docs/TESTING_ON_DEVICE.md`, to fill in the form with a tap. */
data class TracePreset(val label: String, val expectedReels: Int)

object TracePresets {
    val ALL: List<TracePreset> = listOf(
        TracePreset("Instagram Reels, wait 2 s, 10 swipes down", 11),
        TracePreset("Instagram Reels, 5 down, 2 up, 2 down", 6),
        TracePreset("Instagram Reels, comments opened and scrolled", 1),
        TracePreset("Instagram home feed, 20 posts", 0),
        TracePreset("Instagram Stories, 5 taps", 0),
        TracePreset("Snapchat Spotlight, wait 2 s, 10 swipes", 11),
        TracePreset("YouTube Shorts, 10 swipes", 11),
        TracePreset("YouTube home feed", 0),
        TracePreset("TikTok, 10 swipes", 11),
        TracePreset("Facebook Reels, 10 swipes", 11),
    )
}
