package app.candash.cluster.compose

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.performClick
import app.candash.cluster.SName
import app.candash.cluster.SignalState
import app.candash.cluster.compose.ComposeScope.Companion.createComposableCarStateFromMap
import app.candash.cluster.compose.ComposeScope.Companion.createTestComposableCarStateFromMap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.hours
import kotlin.time.TestTimeSource

@RunWith(RobolectricTestRunner::class)
class EfficiencyLoggingTest {


    @get:Rule
    val composeTestRule = createComposeRule()


    @Test
    fun testSanity() {
        assert(true)
    }

    @Test
    fun testEmptyEfficiency() {
        val state =
            mutableMapOf<String, Float?>().createComposableCarStateFromMap()
        val time = TestTimeSource()

        composeTestRule.setContent {
            EfficiencyLogging(state, time)
        }

        composeTestRule.onNode(
            hasTestTag("EfficiencyTable")
        ).assertExists().onChildren()

        composeTestRule.onNode(
            hasText("mph")
        ).assertDoesNotExist()
    }

    @Test
    fun testBuildEfficiency() {
        val state =
            mutableMapOf<String, Float?>(
                SName.odometer to 0f,
                SName.kwhDischargeTotal to 0f
            ).createTestComposableCarStateFromMap()
        val odometerState: MutableState<SignalState?> = state[SName.odometer]!!
        val dischargState: MutableState<SignalState?> = state[SName.kwhDischargeTotal]!!

        val time = TestTimeSource()

        with(composeTestRule) {
            setContent {
                EfficiencyLogging(state, time)
            }

            onNode(
                hasText("Start")
            ).performClick()

            this.waitForIdle()

            odometerState.value = SignalState(1f, 0)
            dischargState.value = SignalState(1f, 0)
            time += 1.hours

            this.waitForIdle()

            onNode(
                hasText("Stop")
            ).performClick()

            this.waitForIdle()

            onNode(hasText("1000 wh/mi")).assertExists()
            onNode(hasText("1 mph")).assertExists()
            onNode(hasText("1 mi")).assertExists()
        }
    }
}