package com.techpath.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Reads bundled files. Implemented over Android assets in AppContainer, and by a fake in tests. */
interface AssetReader {
    fun read(path: String): String
    fun list(dir: String): List<String>
}

/** Read-only access to the bundled dataset, guides and opportunities. Path files load on demand. */
class DatasetRepository(private val assets: AssetReader, private val json: Json) {
    private var paths: List<PathInfo>? = null
    private val nodesByPath = mutableMapOf<String, List<SkillNode>>()

    suspend fun paths(): List<PathInfo> = paths ?: io {
        json.decodeFromString<List<PathInfo>>(assets.read("paths.json"))
    }.also { paths = it }

    suspend fun path(id: String): PathInfo? = paths().firstOrNull { it.id == id }

    suspend fun nodes(pathId: String): List<SkillNode> = nodesByPath[pathId] ?: io {
        json.decodeFromString<PathData>(assets.read("paths/$pathId.json")).nodes
    }.also { nodesByPath[pathId] = it }

    suspend fun opportunities(): List<Opportunity> = io {
        json.decodeFromString<List<Opportunity>>(assets.read("opportunities.json"))
    }

    suspend fun articles(): List<Article> = io {
        assets.list("articles").filter { it.endsWith(".md") }.sorted().map { file ->
            val title = assets.read("articles/$file").lineSequence()
                .firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: file
            Article(file, title)
        }
    }

    suspend fun article(file: String): String = io { assets.read("articles/$file") }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
