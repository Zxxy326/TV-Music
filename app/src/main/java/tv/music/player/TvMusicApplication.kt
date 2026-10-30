package tv.music.player

import android.app.Application
import tv.music.player.core.AppContainer
import tv.music.player.core.crash.CrashLogger

class TvMusicApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        container = AppContainer(this)
        container.connectMediaSession()
    }
}
