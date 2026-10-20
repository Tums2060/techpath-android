package com.techpath.core

/**
 * Parses the dataset's resource cells: `Title (https://url) | Title (https://url)`.
 *
 * Titles can themselves contain " | " (e.g. "Guide | Microsoft Learn"), so we
 * match whole `title (url)` units instead of splitting on the pipe. Matches the
 * pipeline's `parse_resources`.
 */
object ResourceParser {

    private val UNIT = Regex("""(.+?)\s*\((h?ttps?://\S+?)\)(?=\s*\|\s|\s*$)""")

    fun parse(cell: String?, type: String): List<Resource> {
        if (cell.isNullOrBlank()) return emptyList()
        return UNIT.findAll(cell.trim()).map { m ->
            val title = m.groupValues[1].trim().trimStart('|').trim()
            var url = m.groupValues[2]
            if (url.startsWith("ttp")) url = "h$url" // scrape sometimes drops the 'h'
            Resource(title = title.ifEmpty { url }, url = url, type = type)
        }.toList()
    }

    /** Display label for a resource type key. */
    fun label(type: String): String = when (type) {
        "article" -> "Article"
        "docs" -> "Official docs"
        "video" -> "Video"
        "course" -> "Course"
        "opensource" -> "Open source"
        else -> type.replaceFirstChar { it.uppercase() }
    }
}
