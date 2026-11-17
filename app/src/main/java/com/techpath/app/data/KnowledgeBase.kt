package com.techpath.app.data

import android.content.Context
import com.techpath.core.CareerPath
import com.techpath.core.MlModel
import com.techpath.core.Resource
import com.techpath.core.SkillNode
import com.techpath.core.SkillVectors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * The bundled ML engine and roadmap knowledge base (assets built by
 * `pipeline/train_model.py`): the trained TF-IDF + Logistic Regression model,
 * the career paths and every skill node. Loaded once, lazily, off the main
 * thread. Skill TF-IDF vectors come from the same vectoriser
 * the model uses, exactly like `tfidf.transform(final["text"])` in the notebook
 * (precomputed by the pipeline in `model/skill_vectors.bin`).
 */
class KnowledgeBase(private val context: Context) {

    @Serializable
    private data class CareersFile(val version: String, val careers: List<CareerDto>)

    @Serializable
    private data class CareerDto(
        val id: String,
        val name: String,
        val type: String,
        val description: String,
        @SerialName("node_count") val nodeCount: Int,
    )

    @Serializable
    private data class NodesFile(val nodes: List<NodeDto>)

    @Serializable
    private data class NodeDto(
        val id: String,
        val path: String,
        val slug: String = "",
        val title: String,
        val description: String,
        val core: Boolean = false,
        val resources: List<Resource> = emptyList(),
    )

    class Data(
        val version: String,
        val model: MlModel,
        val careers: List<CareerPath>,
        val nodesByPath: Map<String, List<SkillNode>>,
    ) {
        private val careerById = careers.associateBy { it.id }
        private val nodeIndex = nodesByPath.mapValues { (_, list) -> list.associateBy { it.id } }
        private val pathsByTitle: Map<String, List<String>> = nodesByPath.values.flatten()
            .groupBy({ normTitle(it.title) }) { it.path }.mapValues { (_, v) -> v.distinct() }
        private val probaCache = HashMap<String, FloatArray>()

        val nodeCount: Int get() = nodesByPath.values.sumOf { it.size }

        fun career(id: String): CareerPath? = careerById[id]
        fun nodes(path: String): List<SkillNode> = nodesByPath[path].orEmpty()
        fun node(path: String, id: String): SkillNode? = nodeIndex[path]?.get(id)

        /** Model probabilities for a skill's own text (cached). */
        fun proba(node: SkillNode): FloatArray = synchronized(probaCache) {
            probaCache.getOrPut(node.path + "/" + node.id) { model.classifier.predictProba(node.vector) }
        }

        /** P(node's own path | node text): how characteristic the skill is of its career. */
        fun typicality(node: SkillNode): Float {
            val i = model.classifier.indexOf(node.path)
            return if (i < 0) 0f else proba(node)[i]
        }

        /** The model's top predictions for a skill, as (display name, probability). */
        fun topPredictions(node: SkillNode, k: Int = 3): List<Pair<String, Float>> {
            val p = proba(node)
            return p.indices.sortedByDescending { p[it] }.take(k)
                .map { (career(model.classifier.classes[it])?.name ?: model.classifier.classes[it]) to p[it] }
        }

        /** Number of career paths (including this one) that contain a skill with the same title. */
        fun sharedPathCount(node: SkillNode): Int = pathsByTitle[normTitle(node.title)]?.size ?: 1

        /** Display names of other paths that contain this skill. */
        fun otherPaths(node: SkillNode): List<String> =
            pathsByTitle[normTitle(node.title)].orEmpty().filter { it != node.path }.map { career(it)?.name ?: it }.sorted()

        private companion object {
            fun normTitle(t: String) = t.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: Data? = null

    suspend fun get(): Data = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.Default) { load() }.also { cached = it }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun load(): Data {
        val assets = context.assets
        val model = MlModel.load(
            assets.open("model/model_meta.json").bufferedReader().use { it.readText() },
            assets.open("model/vocab.txt").bufferedReader().use { it.readText() },
            assets.open("model/idf.bin").use { it.readBytes() },
            assets.open("model/coef.bin").use { it.readBytes() },
        )
        val careersFile = assets.open("careers.json").use { json.decodeFromStream<CareersFile>(it) }
        val careers = careersFile.careers.map { CareerPath(it.id, it.name, it.type, it.description, it.nodeCount) }
        val nodes = assets.open("nodes.json").buffered(1 shl 16).use { json.decodeFromStream<NodesFile>(it) }.nodes
        val vectors = SkillVectors.read(assets.open("model/skill_vectors.bin").use { it.readBytes() })
        check(vectors.size == nodes.size) { "skill_vectors.bin has ${vectors.size} rows, nodes.json ${nodes.size}" }
        val byPath = nodes.withIndex().groupBy({ it.value.path }) { (i, it) ->
            SkillNode(it.id, it.path, it.slug, it.title, it.description, it.core, it.resources, vectors[i])
        }
        return Data(careersFile.version, model, careers.sortedBy { it.name.lowercase() }, byPath)
    }
}
