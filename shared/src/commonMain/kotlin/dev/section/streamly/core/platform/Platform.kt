package dev.section.streamly.core.platform

import androidx.compose.runtime.Composable

/** True on a television (Android TV / Google TV), where the D-pad is the main input. */
@Composable
expect fun rememberIsTv(): Boolean
