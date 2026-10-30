package tv.music.player.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.ui.graphics.vector.ImageVector

data class SettingsGroupInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val icon: ImageVector,
    val highlights: List<String>,
)

object SettingsGroups {

    val all: List<SettingsGroupInfo> = listOf(
        SettingsGroupInfo(
            id = "appearance",
            title = "外观",
            subtitle = "主题 · 动画 · 歌词样式",
            description = "深色底 + 封面主色，焦点环、字号与安全区都按 3 米观看距离调过。歌词样式、翻译与偏移即时生效并持久化。",
            icon = Icons.Rounded.Palette,
            highlights = listOf("主题色", "歌词样式", "歌词翻译", "歌词偏移"),
        ),
        SettingsGroupInfo(
            id = "playback",
            title = "播放",
            subtitle = "音质 · 无缝 · 音频焦点",
            description = "音质与取流链路已接入；MediaSessionService 提供后台播放、通知栏与媒体键控制。无缝、淡入淡出等细项后续补充。",
            icon = Icons.Rounded.PlayCircle,
            highlights = listOf("音质", "后台播放", "通知栏", "媒体键"),
        ),
        SettingsGroupInfo(
            id = "sources",
            title = "音源",
            subtitle = "LX 插件 · 内置脚本 · 音源市场",
            description = "LX QuickJS 引擎已接入，本地构建会注入用户提供的脚本；支持从文件选择器导入、重载与逐源取流测试。音源市场与排序在 M4 接入。",
            icon = Icons.Rounded.Cloud,
            highlights = listOf("已装脚本", "导入脚本", "逐源测试", "在线清单"),
        ),
        SettingsGroupInfo(
            id = "account",
            title = "账号",
            subtitle = "QQ 音乐 · 网易云",
            description = "TV 端只做扫码与 Cookie 两种入口，不提供手机号/密码输入。M3b2 接入登录与歌单同步。",
            icon = Icons.Rounded.AccountCircle,
            highlights = listOf("QQ 扫码", "网易云扫码", "Cookie 导入"),
        ),
        SettingsGroupInfo(
            id = "local",
            title = "本地库",
            subtitle = "USB 外接盘 · SMB 共享",
            description = "USB/外接盘与 NAS 的增量索引在 M4 落地；本地歌与在线歌混合入队。",
            icon = Icons.Rounded.Folder,
            highlights = listOf("USB 扫描", "SMB 挂载", "增量索引"),
        ),
        SettingsGroupInfo(
            id = "companion",
            title = "手机伴侣",
            subtitle = "扫码输入 · 手机遥控",
            description = "局域网 HTTP 服务已接入：手机扫码后输入搜索、暂停切歌、调进度与队列点播；手机与电视需在同一 Wi-Fi / 局域网。",
            icon = Icons.Rounded.Smartphone,
            highlights = listOf("配对码", "二维码", "输入同步", "播放遥控"),
        ),
        SettingsGroupInfo(
            id = "about",
            title = "关于",
            subtitle = "版本 · 许可 · 崩溃日志",
            description = "GPL-3.0 开源；参考项目 XiangsuPlayerHQ 署名与第三方声明见仓库 NOTICE。",
            icon = Icons.Rounded.Info,
            highlights = listOf("版本", "开源许可", "上游署名", "崩溃日志"),
        ),
    )

    fun byId(id: String): SettingsGroupInfo = all.firstOrNull { it.id == id } ?: all.first()
}
