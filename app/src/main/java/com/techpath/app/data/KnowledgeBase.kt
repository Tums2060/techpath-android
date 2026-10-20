package com.techpath.app.data

import android.content.Context
import com.techpath.core.CareerPath
import com.techpath.core.EmbeddingMeta
import com.techpath.core.HashEmbedder
import com.techpath.core.Resource
import com.techpath.core.SkillNode
import com.techpath.core.VectorCodec
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
 * The bundled roadmap knowledge base (assets built by /pipeline): career paths,
 * nodes with their precomputed vectors, and the embedding metadata. Loaded once,
 * lazily, off the main thread.
 */
class KnowledgeBase(private val context: Context) {

    @Serializable
    private data class CareersFile(val version: String, val embedding: EmbeddingMeta, val careers: List<CareerDto>)

    @Serializable
    private data class CareerDto(
        val id: String,
        val name: String,
        val type: String,
        val description: String,
        @SerialName("node_count") val nodeCount: Int,
        val vector: String,
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
        val vector: String,
    )

    class Data(
        val version: String,
        val embedding: EmbeddingMeta,
        val careers: List<CareerPath>,
        val nodesByPath: Map<String, List<SkillNode>>,
        val hashEmbedder: HashEmbedder?,
    ) {
        private val careerById = careers.associateBy { it.id }
        private val nodeIndex = nodesByPath.mapValues { (_, list) -> list.associateBy { it.id } }

        fun career(id: String): CareerPath? = careerById[id]
        fun nodes(path: String): List<SkillNode> = nodesByPath[path].orEmpty()
        fun node(path: String, id: String): SkillNode? = nodeIndex[path]?.get(id)

        /** Title-only vectors for the lexical skill-gap check (hash embeddings only). */
        private val titleVectors = HashMap<String, FloatArray>()
        fun titleVector(node: SkillNode): FloatArray = synchronized(titleVectors) {
            titleVectors.getOrPut(node.path + "/" + node.id) { hashEmbedder!!.embedOne(node.title) }
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: Data? = null

    suspend fun get(): Data = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load() }.also { cached = it }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun load(): Data {
        val assets = context.assets
        val careersFile = assets.open("careers.json").use { json.decodeFromStream<CareersFile>(it) }
        val careers = careersFile.careers.map {
            CareerPath(it.id, it.name, it.type, it.description, it.nodeCount, VectorCodec.decode(it.vector))
        }
        val nodes = assets.open("nodes.json").buffered(1 shl 16).use { json.decodeFromStream<NodesFile>(it) }.nodes
        val byPath = nodes.groupBy({ it.path }) {
            SkillNode(it.id, it.path, it.slug, it.title, it.description, it.core, it.resources, VectorCodec.decode(it.vector))
        }
        val meta = careersFile.embedding
        val hash = if (meta.isLocal) {
            HashEmbedder.fromLexicon(assets.open("hash_lexicon.json").bufferedReader().use { it.readText() }, meta.idf)
        } else null
        return Data(careersFile.version, meta, careers.sortedBy { it.name.lowercase() }, byPath, hash)
    }
}
