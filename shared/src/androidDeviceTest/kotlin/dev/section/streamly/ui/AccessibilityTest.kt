package dev.section.streamly.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToLog
import dev.section.streamly.home.PosterCard
import dev.section.streamly.home.domain.MediaItem
import dev.section.streamly.ui.components.EmptyView
import dev.section.streamly.ui.components.ErrorView
import dev.section.streamly.ui.components.LoadingView
import dev.section.streamly.ui.components.OfflineBanner
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Checks what a screen reader is told, using the merged semantics tree TalkBack reads from. */
class AccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private fun texts(node: androidx.compose.ui.semantics.SemanticsNode) =
        node.config.getOrNull(SemanticsProperties.Text)?.map { it.text }.orEmpty()

    @Test
    fun posterCardIsOneButtonThatReadsItsTitleOnce() {
        var clicks = 0
        // No poster path, so the placeholder (which also contains the title) is what is shown.
        val item = MediaItem(1, "Heat", posterPath = null, isMovie = true)
        rule.setContent { MaterialTheme { PosterCard(item, onClick = { clicks++ }) } }

        rule.onRoot().printToLog("A11Y_POSTER")
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNode(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher("reads the title exactly once") { texts(it) == listOf("Heat") })
        rule.onNode(hasClickAction()).performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun errorViewHasHeadingAndIsAnnouncedAssertively() {
        rule.setContent { MaterialTheme { ErrorView("Boom", onRetry = {}) } }
        rule.onRoot().printToLog("A11Y_ERROR")
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher("heading is the error title") { texts(it) == listOf("Something went wrong") })
        rule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.LiveRegion,
                LiveRegionMode.Assertive
            )
        )
            .assertExists()
        rule.onNode(hasClickAction()).assertExists() // Retry
    }

    @Test
    fun loadingEmptyAndOfflineAreAnnounced() {
        rule.setContent {
            MaterialTheme {
                LoadingView()
                EmptyView("Nothing here")
                OfflineBanner()
            }
        }
        rule.onRoot().printToLog("A11Y_STATES")
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
            .assertCountEquals(3)
        rule.onNode(SemanticsMatcher("loading is described") {
            it.config.getOrNull(SemanticsProperties.ContentDescription) == listOf("Loading")
        }).assertExists()
    }
}
