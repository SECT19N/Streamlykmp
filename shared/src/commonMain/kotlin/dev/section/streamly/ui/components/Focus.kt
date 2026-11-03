package dev.section.streamly.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Draws a ring while the element is focused, so D-pad users can see where they are.
 * The colour comes from the theme so it stays visible in both light and dark mode.
 */
@Composable
fun Modifier.focusRing(interactionSource: InteractionSource, shape: Shape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return if (focused) {
        border(3.dp, MaterialTheme.colorScheme.inverseSurface, shape)
    } else {
        this
    }
}

/**
 * A focusable, clickable card that TalkBack reads as one button and D-pad users see a ring on.
 * Shared by poster, channel and similar cards so they behave the same.
 */
@Composable
fun Modifier.clickableCard(
    onClick: () -> Unit,
    shape: Shape,
    onClickLabel: String? = null,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this
        .focusRing(interactionSource, shape)
        .clip(shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClickLabel = onClickLabel,
            role = Role.Button,
            onClick = onClick,
        )
        .semantics(mergeDescendants = true) {}
}
