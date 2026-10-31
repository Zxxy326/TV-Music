package tv.music.player.ui.navigation

import android.net.Uri

sealed class TvRoute(val route: String) {
    data object Home : TvRoute("home")
    data object Search : TvRoute("search")
    data object Player : TvRoute("player")
    data object Favorites : TvRoute("library/favorites")
    data object Recent : TvRoute("library/recent")
    data object LocalHub : TvRoute("library/local")
    data object Downloads : TvRoute("library/downloads")
    data object DeviceMusic : TvRoute("local/device")
    data object Smb : TvRoute("local/smb")
    data object SourceMarket : TvRoute("sources/market")
    data object MarketRelease : TvRoute("sources/market/{tag}") {
        fun of(tag: String): String = "sources/market/${Uri.encode(tag)}"
    }
    data object Account : TvRoute("account")
    data object AccountPlaylist : TvRoute("account/playlist/{id}") {
        fun of(id: String): String = "account/playlist/${Uri.encode(id)}"
    }
    data object Settings : TvRoute("settings")
    data object SettingsGroup : TvRoute("settings/{groupId}") {
        fun of(groupId: String): String = "settings/$groupId"
    }
}
