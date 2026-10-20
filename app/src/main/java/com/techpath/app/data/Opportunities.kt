package com.techpath.app.data

import android.content.Context
import com.techpath.app.data.db.ApplicationStatus
import com.techpath.app.data.db.OpportunityDao
import com.techpath.app.data.db.SavedOpportunityEntity
import com.techpath.core.VectorCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
data class Opportunity(
    val id: String,
    val title: String,
    val organisation: String,
    val type: String,
    val field: String,
    val location: String,
    val deadline: String,
    val requirements: List<String>,
    val description: String = "",
    @SerialName("apply_url") val applyUrl: String,
    @SerialName("vector") val vectorB64: String = "",
) {
    @Transient val vector: FloatArray? = vectorB64.takeIf { it.isNotEmpty() }?.let { VectorCodec.decode(it) }
    val deadlineDate: LocalDate? get() = runCatching { LocalDate.parse(deadline) }.getOrNull()
}

/**
 * Source of opportunity listings. Only [SampleOpportunitySource] exists today; a
 * real feed (e.g. a university careers API) can implement this later.
 */
interface OpportunitySource {
    /** True when listings are demonstration data and must be labelled as such. */
    val isSample: Boolean
    suspend fun listings(): List<Opportunity>
}

class SampleOpportunitySource(private val context: Context) : OpportunitySource {
    @Serializable
    private data class File(val sample: Boolean = true, val listings: List<Opportunity>)

    override val isSample = true
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: List<Opportunity>? = null

    override suspend fun listings(): List<Opportunity> = cache ?: withContext(Dispatchers.IO) {
        context.assets.open("opportunities.json").bufferedReader().use { json.decodeFromString<File>(it.readText()).listings }
    }.also { cache = it }
}

/** Listings plus the student's saved/application state (never submitted in-app). */
class OpportunityRepository(val source: OpportunitySource, private val dao: OpportunityDao) {
    val saved: Flow<Map<String, ApplicationStatus>> = dao.observeAll().map { list -> list.associate { it.opportunityId to it.status } }

    suspend fun setStatus(id: String, status: ApplicationStatus?) {
        if (status == null) dao.delete(id) else dao.upsert(SavedOpportunityEntity(id, status, System.currentTimeMillis()))
    }
}
