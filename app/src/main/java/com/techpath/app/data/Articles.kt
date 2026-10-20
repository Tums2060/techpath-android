package com.techpath.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Article(val id: String, val title: String, val summary: String, val body: String) {
    /** Very small relevance score for the hub search box. */
    fun matches(query: String): Int {
        val terms = query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }
        if (terms.isEmpty()) return 1
        val t = title.lowercase()
        val b = body.lowercase()
        return terms.sumOf { term -> (if (term in t) 5 else 0) + Regex(Regex.escape(term)).findAll(b).count().coerceAtMost(5) }
            .takeIf { s -> terms.all { it in t || it in b } } ?: 0
    }
}

/** Career Information Hub: bundled Markdown articles in assets/articles. */
class ArticleRepository(private val context: Context) {
    @Volatile private var cache: List<Article>? = null

    suspend fun all(): List<Article> = cache ?: withContext(Dispatchers.IO) {
        val files = context.assets.list("articles").orEmpty().filter { it.endsWith(".md") }.sorted()
        files.map { f ->
            val text = context.assets.open("articles/$f").bufferedReader().use { it.readText() }
            val lines = text.lines()
            val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim() ?: f
            val summary = lines.firstOrNull { it.startsWith("> ") }?.removePrefix("> ")?.trim().orEmpty()
            Article(f.removeSuffix(".md"), title, summary, text)
        }
    }.also { cache = it }

    suspend fun get(id: String): Article? = all().firstOrNull { it.id == id }
}
