package com.nocturne.player.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pushes [WidgetState] to every placed widget. Updates are coalesced (at most one render in
 * flight, ~250 ms debounce) so rapid player events don't flood the launcher. Nothing polls:
 * PlaybackService calls [push] on real changes, plus a progress tick only while playing with
 * the screen on.
 */
object NocturneWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    private var revision = 0L

    fun push(context: Context, state: WidgetState) {
        WidgetState.write(context, state)
        requestUpdate(context)
    }

    @Synchronized
    fun requestUpdate(context: Context) {
        val app = context.applicationContext
        if (pending?.isActive == true) return
        pending = scope.launch {
            delay(250)
            runCatching {
                val manager = GlanceAppWidgetManager(app)
                val ids = manager.getGlanceIds(NocturneWidget::class.java)
                if (ids.isEmpty()) return@runCatching
                val rev = synchronized(this@NocturneWidgetUpdater) { ++revision }
                val widget = NocturneWidget()
                ids.forEach { id ->
                    updateAppWidgetState(app, id) { it[NocturneWidget.REVISION] = rev }
                    widget.update(app, id)
                }
            }
        }
    }
}
