package com.helmet.guard

import android.app.Application
import com.helmet.guard.service.GuardNotifications

class HelmetGuardApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }

    override fun onCreate() {
        super.onCreate()
        GuardNotifications.createChannels(this)
        graph.incidents.restorePending()
    }
}

val android.content.Context.appGraph: AppGraph
    get() = (applicationContext as HelmetGuardApp).graph
