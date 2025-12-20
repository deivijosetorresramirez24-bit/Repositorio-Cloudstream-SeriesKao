package com.DamianKing12

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class SeriesKaoPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SeriesKaoProvider())
    }
}
