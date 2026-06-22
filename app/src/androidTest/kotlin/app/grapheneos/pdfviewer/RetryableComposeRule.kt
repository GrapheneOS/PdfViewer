package app.grapheneos.pdfviewer

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.IdlingResource
import androidx.compose.ui.test.MainTestClock
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@OptIn(ExperimentalTestApi::class)
class RetryableComposeRule(
    private val factory: () -> ComposeTestRule = { createEmptyComposeRule() }
) : TestRule, ComposeTestRule {

    @Volatile
    private var current: ComposeTestRule = factory()

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                current = factory()
                current.apply(base, description).evaluate()
            }
        }
    }

    override val density: Density get() = current.density
    override val mainClock: MainTestClock get() = current.mainClock

    override fun <T> runOnUiThread(action: () -> T): T = current.runOnUiThread(action)
    override fun <T> runOnIdle(action: () -> T): T = current.runOnIdle(action)
    override fun waitForIdle() = current.waitForIdle()
    override suspend fun awaitIdle() = current.awaitIdle()

    override fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) =
        current.waitUntil(timeoutMillis, condition)

    override fun waitUntil(
        conditionDescription: String,
        timeoutMillis: Long,
        condition: () -> Boolean
    ) = current.waitUntil(conditionDescription, timeoutMillis, condition)

    override fun registerIdlingResource(idlingResource: IdlingResource) =
        current.registerIdlingResource(idlingResource)

    override fun unregisterIdlingResource(idlingResource: IdlingResource) =
        current.unregisterIdlingResource(idlingResource)

    override fun onNode(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean
    ): SemanticsNodeInteraction = current.onNode(matcher, useUnmergedTree)

    override fun onAllNodes(
        matcher: SemanticsMatcher,
        useUnmergedTree: Boolean
    ): SemanticsNodeInteractionCollection = current.onAllNodes(matcher, useUnmergedTree)

    @ExperimentalTestApi
    override fun waitUntilNodeCount(
        matcher: SemanticsMatcher,
        count: Int,
        timeoutMillis: Long
    ) = current.waitUntilNodeCount(matcher, count, timeoutMillis)

    @ExperimentalTestApi
    override fun waitUntilAtLeastOneExists(
        matcher: SemanticsMatcher,
        timeoutMillis: Long
    ) = current.waitUntilAtLeastOneExists(matcher, timeoutMillis)

    @ExperimentalTestApi
    override fun waitUntilExactlyOneExists(
        matcher: SemanticsMatcher,
        timeoutMillis: Long
    ) = current.waitUntilExactlyOneExists(matcher, timeoutMillis)

    @ExperimentalTestApi
    override fun waitUntilDoesNotExist(
        matcher: SemanticsMatcher,
        timeoutMillis: Long
    ) = current.waitUntilDoesNotExist(matcher, timeoutMillis)
}
