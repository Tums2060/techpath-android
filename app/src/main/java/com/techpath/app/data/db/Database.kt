package com.techpath.app.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The current roadmap per career path, stored as the core Roadmap JSON. */
@Entity(tableName = "roadmaps")
data class RoadmapEntity(
    @PrimaryKey val careerPath: String,
    val json: String,
    val source: String,
    val createdAt: Long,
)

enum class SkillStatus(val label: String) {
    NOT_STARTED("Not started"), LEARNING("Learning"), DONE("Done");
}

@Entity(tableName = "skill_status", primaryKeys = ["careerPath", "nodeId"])
data class SkillStatusEntity(
    val careerPath: String,
    val nodeId: String,
    val status: SkillStatus,
    val updatedAt: Long,
)

/** Cached LLM explanation for a skill in the context of a career path. */
@Entity(tableName = "explanations", primaryKeys = ["careerPath", "nodeId"])
data class ExplanationEntity(
    val careerPath: String,
    val nodeId: String,
    val whatItMeans: String,
    val whatItInvolves: String,
    val whyItMatters: String,
    val model: String,
    val createdAt: Long,
)

enum class ApplicationStatus(val label: String) {
    SAVED("Saved"), APPLIED("Applied"), INTERVIEWING("Interviewing"), OFFER("Offer"), REJECTED("Rejected");
}

@Entity(tableName = "saved_opportunities")
data class SavedOpportunityEntity(
    @PrimaryKey val opportunityId: String,
    val status: ApplicationStatus,
    val updatedAt: Long,
)

/** Anonymised research event. No personal data: only ids of app content. */
@Entity(tableName = "events", indices = [Index("timestamp")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val participantId: String,
    val mode: String,
    val type: String,
    val careerPath: String? = null,
    val itemId: String? = null,
    val detail: String? = null,
    val durationMs: Long? = null,
)

@Dao
interface RoadmapDao {
    @Query("SELECT * FROM roadmaps WHERE careerPath = :path")
    fun observe(path: String): Flow<RoadmapEntity?>

    @Upsert
    suspend fun upsert(e: RoadmapEntity)

    @Query("DELETE FROM roadmaps")
    suspend fun clear()
}

@Dao
interface SkillStatusDao {
    @Query("SELECT * FROM skill_status WHERE careerPath = :path")
    fun observe(path: String): Flow<List<SkillStatusEntity>>

    @Query("SELECT * FROM skill_status WHERE careerPath = :path AND nodeId = :nodeId")
    fun observeOne(path: String, nodeId: String): Flow<SkillStatusEntity?>

    @Upsert
    suspend fun upsert(e: SkillStatusEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(e: List<SkillStatusEntity>)

    @Query("DELETE FROM skill_status")
    suspend fun clear()
}

@Dao
interface ExplanationDao {
    @Query("SELECT * FROM explanations WHERE careerPath = :path AND nodeId = :nodeId")
    suspend fun get(path: String, nodeId: String): ExplanationEntity?

    @Upsert
    suspend fun upsert(e: ExplanationEntity)

    @Query("DELETE FROM explanations")
    suspend fun clear()
}

@Dao
interface OpportunityDao {
    @Query("SELECT * FROM saved_opportunities")
    fun observeAll(): Flow<List<SavedOpportunityEntity>>

    @Upsert
    suspend fun upsert(e: SavedOpportunityEntity)

    @Query("DELETE FROM saved_opportunities WHERE opportunityId = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM saved_opportunities")
    suspend fun clear()
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(e: EventEntity)

    @Query("SELECT * FROM events ORDER BY timestamp, id")
    suspend fun all(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM events")
    fun count(): Flow<Int>

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Database(
    entities = [RoadmapEntity::class, SkillStatusEntity::class, ExplanationEntity::class,
        SavedOpportunityEntity::class, EventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun roadmaps(): RoadmapDao
    abstract fun skillStatus(): SkillStatusDao
    abstract fun explanations(): ExplanationDao
    abstract fun opportunities(): OpportunityDao
    abstract fun events(): EventDao
}
