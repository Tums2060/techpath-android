package com.techpath.ui.hub

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.techpath.ui.theme.Dimens

/** Minimal markdown: headings, paragraphs, bullet/numbered lists, quotes; inline bold, italic, code, links. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
}

fun parseMarkdown(source: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) blocks += MdBlock.Paragraph(paragraph.joinToString(" "))
        paragraph.clear()
    }
    val heading = Regex("^(#{1,6})\\s+(.*)$")
    val bullet = Regex("^\\s*[-*]\\s+(.*)$")
    val numbered = Regex("^\\s*(\\d+)\\.\\s+(.*)$")
    for (raw in source.lines()) {
        val line = raw.trimEnd()
        heading.find(line)?.let { flush(); blocks += MdBlock.Heading(it.groupValues[1].length, it.groupValues[2]); continue }
        bullet.find(line)?.let { flush(); blocks += MdBlock.ListItem("•", it.groupValues[1]); continue }
        numbered.find(line)?.let { flush(); blocks += MdBlock.ListItem("${it.groupValues[1]}.", it.groupValues[2]); continue }
        if (line.startsWith(">")) { flush(); blocks += MdBlock.Quote(line.removePrefix(">").trim()); continue }
        if (line.isBlank()) flush() else paragraph += line.trim()
    }
    flush()
    return blocks
}

@Composable
fun MarkdownText(blocks: List<MdBlock>, modifier: Modifier = Modifier) {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    Column(modifier) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, linkStyle),
                    style = if (block.level == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Dimens.spaceM, bottom = Dimens.spaceXs),
                )
                is MdBlock.Paragraph -> Text(
                    inline(block.text, linkStyle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = Dimens.spaceXs),
                )
                is MdBlock.ListItem -> Row(Modifier.padding(start = Dimens.spaceS, top = Dimens.spaceXs / 2, bottom = Dimens.spaceXs / 2)) {
                    Text(block.marker, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(Dimens.indent))
                    Text(inline(block.text, linkStyle), style = MaterialTheme.typography.bodyLarge)
                }
                is MdBlock.Quote -> Text(
                    inline(block.text, linkStyle),
                    style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Dimens.spaceS),
                )
            }
        }
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|\[(.+?)]\((\S+?)\)""")

/** Bold, italic, inline code and links. Links open in the browser via the default URI handler. */
private fun inline(text: String, linkStyle: TextLinkStyles): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in INLINE.findAll(text)) {
        append(text.substring(last, m.range.first))
        val (bold, italic, code, linkText, url) = m.destructured
        when {
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inline(bold, linkStyle)) }
            italic.isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inline(italic, linkStyle)) }
            code.isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(code) }
            else -> withLink(LinkAnnotation.Url(url, linkStyle)) { append(linkText) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}
