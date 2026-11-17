package com.techpath.app

import android.content.Context
import androidx.room.Room
import com.techpath.app.data.ArticleRepository
import com.techpath.app.data.EventLogger
import com.techpath.app.data.KnowledgeBase
import com.techpath.app.data.OpportunityRepository
import com.techpath.app.data.RoadmapRepository
import com.techpath.app.data.SampleOpportunitySource
import com.techpath.app.data.SettingsRepository
import com.techpath.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency injection: one instance of each service per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "techpath.db")
        .fallbackToDestructiveMigration(dropAllTables = true) // prototype: schema changes reset local data
        .build()
    val settings = SettingsRepository(app)
    val knowledgeBase = KnowledgeBase(app)
    val articles = ArticleRepository(app)
    val logger: EventLogger = EventLogger(db.events(), settings, { roadmaps.modeState().effective.name }, appScope)
    val roadmaps: RoadmapRepository = RoadmapRepository(knowledgeBase, settings, db, logger)
    val opportunities = OpportunityRepository(SampleOpportunitySource(app), db.opportunities())
}
