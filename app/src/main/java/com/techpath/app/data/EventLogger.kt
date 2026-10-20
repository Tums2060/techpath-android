package com.techpath.app.data

import com.techpath.app.data.db.EventDao
import com.techpath.app.data.db.EventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.Writer
import java.time.Instant

/** Research instrumentation: anonymised, local-only usage events. */
object Events {
    const val ONBOARDING_DONE = "onboarding_completed"
    const val CAREER_MATCHED = "career_matched"
    const val ROADMAP_GENERATED = "roadmap_generated"
    const val ROADMAP_FALLBACK = "roadmap_fallback_static"
    const val SKILL_OPENED = "skill_opened"
    const val STATUS_CHANGED = "status_changed"
    const val RESOURCE_OPENED = "resource_opened"
    const val OPPORTUNITY_SAVED = "opportunity_saved"
    const val OPPORTUNITY_STATUS = "opportunity_status_changed"
    const val OPPORTUNITY_LINK = "opportunity_link_opened"
    const val ARTICLE_OPENED = "article_opened"
    const val TAB_TIME = "tab_time"
    const val MODE_CHANGED = "mode_changed"
}

class EventLogger(
    private val dao: EventDao,
    private val settings: SettingsRepository,
    private val modeProvider: suspend () -> String,
    private val scope: CoroutineScope,
) {
    fun log(type: String, careerPath: String? = null, itemId: String? = null, detail: String? = null, durationMs: Long? = null) {
        scope.launch {
            dao.insert(
                EventEntity(
                    timestamp = System.currentTimeMillis(),
                    participantId = settings.ensureParticipantId(),
                    mode = modeProvider(),
                    type = type,
                    careerPath = careerPath,
                    itemId = itemId,
                    detail = detail,
                    durationMs = durationMs,
                ),
            )
        }
    }

    /** Writes all events as CSV. Returns the number of rows. */
    suspend fun exportCsv(out: Writer): Int {
        val rows = dao.all()
        out.write("event_id,participant_id,timestamp_utc,mode,event,career_path,item_id,detail,duration_ms\n")
        for (e in rows) {
            out.write(
                listOf(
                    e.id.toString(), e.participantId, Instant.ofEpochMilli(e.timestamp).toString(), e.mode, e.type,
                    e.careerPath.orEmpty(), e.itemId.orEmpty(), e.detail.orEmpty(), e.durationMs?.toString().orEmpty(),
                ).joinToString(",") { csv(it) } + "\n",
            )
        }
        out.flush()
        return rows.size
    }

    private fun csv(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}
