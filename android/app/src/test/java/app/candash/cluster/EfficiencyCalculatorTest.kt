package app.candash.cluster


import android.app.Application
import android.content.Context.MODE_PRIVATE
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EfficiencyCalculatorTest {


    private val app: Application = getApplicationContext()
    private val carState = createCarState(
        mutableMapOf(
            SName.uiSpeed to 60f,
            SName.power to 60f,
            SName.gearSelected to SVal.gearDrive,
            SName.kwhChargeTotal to 0f
        )
    )
    private val efficiencyCalculator = EfficiencyCalculator(
        carState,
        app.getSharedPreferences("test", MODE_PRIVATE)
    )

    @Test
    fun testInstantaneousDisplay() {
        assertEquals("1 Wh/km", efficiencyCalculator.getEfficiencyText(0f))
    }

    @Test
    fun testStartingDisplay_isDash() {
        assertEquals("-", efficiencyCalculator.getEfficiencyText(1f))
        assertEquals("-", efficiencyCalculator.getEfficiencyText(30f))
    }

    @Test
    fun testPastDisplay_oneValue_notEnough() {
        updateKwhHistory(0f, 0f)
        assertEquals("(calculating)", efficiencyCalculator.getEfficiencyText(.1f))
    }

    @Test
    fun testPastDisplay_twoValues_notEnough() {
        updateKwhHistory(0f, 0f)
        // jumps >= 1 are ignored
        updateKwhHistory(.1f, .1f)

        assertEquals("(0 of 0 km)", efficiencyCalculator.getEfficiencyText(.1f))
    }

    @Test
    fun testPastDisplay_threeValues() {
        updateKwhHistory(0f, 0f)
        updateKwhHistory(.1f, .1f)
        updateKwhHistory(.2f, .2f)

        assertEquals("1 Wh/km", efficiencyCalculator.getEfficiencyText(.1f))
    }

    private fun updateKwhHistory(
        odometer: Float,
        kwhDischargeTotal: Float,
    ) {
        updateCarState(odometer, kwhDischargeTotal)
        efficiencyCalculator.updateKwhHistory()
    }

    /**
     * While driving, typically the odometer and discharge will increase, and not the charge.
     */
    private fun updateCarState(
        odometer: Float,
        kwhDischargeTotal: Float,
    ) {
        carState.putAll(
            createCarState(
                mutableMapOf(
                    SName.odometer to odometer,
                    SName.kwhDischargeTotal to kwhDischargeTotal,
                )
            )
        )
    }
}