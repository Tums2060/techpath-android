package com.techpath.app

import android.app.Application
import kotlinx.coroutines.launch

class TechPathApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.settings.ensureParticipantId()
            container.knowledgeBase.get() // warm the knowledge base in the background
        }
    }
}
