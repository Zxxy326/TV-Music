package tv.music.player.core

import android.content.Context
import tv.music.player.core.playback.DemoPlaybackController
import tv.music.player.core.settings.SettingsRepository

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val settings = SettingsRepository(appContext)
    val playback = DemoPlaybackController()
}
