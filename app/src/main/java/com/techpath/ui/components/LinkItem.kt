package com.techpath.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.techpath.R
import com.techpath.data.Link
import com.techpath.ui.theme.Dimens
import com.techpath.ui.theme.TechPathTheme

/** Opens [url] in the browser. Returns false if no app can open it. */
fun Context.openUrl(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
}

@Composable
fun LinkItem(link: Link, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = Dimens.spaceS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(link.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(
                linkTypeLabel(link.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Dimens.spaceS))
        Icon(
            painterResource(R.drawable.ic_open_in_new),
            contentDescription = stringResource(R.string.action_open_link),
            modifier = Modifier.size(Dimens.iconM),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun linkTypeLabel(type: String): String = stringResource(
    when (type) {
        "official_docs" -> R.string.link_type_docs
        "videos" -> R.string.link_type_video
        "opensource" -> R.string.link_type_opensource
        "courses" -> R.string.link_type_course
        else -> R.string.link_type_article
    },
)

@Preview(showBackground = true)
@Composable
private fun LinkItemPreview() {
    TechPathTheme { LinkItem(Link("official_docs", "Kotlin documentation", "https://kotlinlang.org"), onOpen = {}) }
}
