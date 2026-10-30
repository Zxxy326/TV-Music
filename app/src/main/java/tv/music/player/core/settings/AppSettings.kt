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

enum class LyricStyle(val label: String, val hint: String) {
    CLASSIC("经典逐行", "当前行放大，上下渐隐"),
    SUBTITLE("大字幕", "底部两行超大描边字"),
    MINIMAL("极简纯文字", "纯黑底，无封面无装饰"),
    KARAOKE("卡拉OK扫色", "逐字亮起，未唱部分保持灰色"),
    BOUNCE("弹跳气泡", "逐字弹性跳动，带圆角气泡"),
    BILINGUAL("双语对照", "主词扫色，翻译副行同步高亮"),
}

/** 正值表示歌词提前出现。 */
fun formatLyricOffset(offsetMs: Int): String {
    if (offsetMs == 0) return "0.0s"
    val abs = kotlin.math.abs(offsetMs)
    val seconds = "${abs / 1000}.${(abs % 1000) / 100}s"
    return if (offsetMs > 0) "提前 $seconds" else "延后 $seconds"
}

enum class AudioQuality(val label: String) {
    STANDARD("标准"),
    HIGH("高音质"),
    LOSSLESS("无损"),
}

val AudioQuality.lxQuality: String
    get() = when (this) {
        AudioQuality.STANDARD -> "128k"
        AudioQuality.HIGH -> "320k"
        AudioQuality.LOSSLESS -> "flac"
    }

data class AppSettings(
    val accentMode: AccentMode = AccentMode.COVER,
    val animationsEnabled: Boolean = true,
    val blurBackground: Boolean = true,
    val lyricSize: LyricSize = LyricSize.MEDIUM,
    val lyricStyle: LyricStyle = LyricStyle.CLASSIC,
    val showLyricTranslation: Boolean = true,
    val lyricOffsetMs: Int = 0,
    val quality: AudioQuality = AudioQuality.HIGH,
    val gaplessPlayback: Boolean = true,
    val fadeInOut: Boolean = false,
    val audioFocus: Boolean = true,
    val companionEnabled: Boolean = false,
)
