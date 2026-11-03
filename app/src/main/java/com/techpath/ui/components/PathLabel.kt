package com.techpath.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.techpath.R
import com.techpath.data.PathInfo

/** "Backend" for a main path, "Backend · Beginner" for its beginner variant. */
@Composable
fun pathLabel(path: PathInfo): String =
    if (path.isBeginnerVariant) stringResource(R.string.path_beginner_format, path.name) else path.name
