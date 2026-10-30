package tv.music.player.core.settings

enum class AccentMode(val label: String) {
    COVER("跟随封面"),
    TEAL("青绿"),
    AMBER("琥珀"),
    VIOLET("紫罗兰"),
    ROSE("玫瑰"),
}

enum class LyricSize(val label: String, val scale: Float) {
    SMALL("小 · 34sp", 0.8f),
    MEDIUM("中 · 48sp", 1.0f),
    LARGE("大 · 72sp", 1.35f),
}

enum class AudioQuality(val label: String) {
    STANDARD("标准"),
    HIGH("高音质"),
    LOSSLESS("无损"),
}

data class AppSettings(
    val accentMode: AccentMode = AccentMode.COVER,
    val animationsEnabled: Boolean = true,
    val blurBackground: Boolean = true,
    val lyricSize: LyricSize = LyricSize.MEDIUM,
    val quality: AudioQuality = AudioQuality.HIGH,
    val gaplessPlayback: Boolean = true,
    val fadeInOut: Boolean = false,
    val audioFocus: Boolean = true,
    val companionEnabled: Boolean = false,
)
