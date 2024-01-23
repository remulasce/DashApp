package app.candash.cluster

import app.candash.cluster.IntegratingEfficiencyHistory.Companion.minimumIntervalMi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class IntegratingEfficiencyHistoryTest {

    val time = TestTimeSource()
    val history = IntegratingEfficiencyHistory()

    @Test
    fun testSanity() {
        assert(true)
    }

    @Test
    fun testSingleValue_noResult() {
        history.addInstantaneousPower(50.0, time.markNow(), 0.0)

        assert(history.getLatestInterval() == null)
    }

    @Test
    fun testDualValues_underDistance_noResult() {
        history.addInstantaneousPower(50.0, time.markNow(), 0.0)
        history.addInstantaneousPower(
            50.0, time.markNow(),
            // Not enough distance
            IntegratingEfficiencyHistory.Companion.minimumIntervalMi / 2
        )

        assert(history.getLatestInterval() == null)
    }

    @Test
    fun testDualValues_meetsDistance() {
        history.addInstantaneousPower(50.0, time.markNow(), 0.0)
        time += 1.hours // makes the validation easier
        history.addInstantaneousPower(
            50.0, time.markNow(), minimumIntervalMi * 1.1
        )

        val latestInterval = history.getLatestInterval()
        checkNotNull(latestInterval)
        assert(latestInterval.energyUsedKwh == 50.0)
        assert(latestInterval.distanceMi == minimumIntervalMi * 1.1)
        assert(latestInterval.duration == 1.hours)
    }

    @Test
    fun testDualValues_prunesInstantReadings() {
        history.addInstantaneousPower(50.0, time.markNow(), 0.0)
        time += 1.milliseconds
        history.addInstantaneousPower(
            50.0, time.markNow(), minimumIntervalMi * 1.1
        )

        // Of the two readings, the first should be pruned, the second left for next time
        assert(history.calculatedIntervals.size == 1)
    }

    @Test
    fun testManyDifferentValues() {
        val path = listOf(
            TestPoint(0.0, 0, 0.0),
            TestPoint(50.0, 1, 0.01), // avg 25f
            TestPoint(100.0, 5, 0.1) // Clicks over
        )
        walkPath(path)

        assertEquals(1, history.calculatedIntervals.size)
        assert(history.getLatestInterval()!!.duration == 6.minutes)
        assert(history.getLatestInterval()!!.distanceMi == .1)
        assertEquals(
            (25 * 1 + 75 * 5.0) / TimeUnit.MINUTES.convert(1, TimeUnit.HOURS),
            history.getLatestInterval()!!.energyUsedKwh,
            .0001
        )
    }

    @Test
    fun testRollOverToNextInterval() {
        val path = listOf(
            TestPoint(0.0, 0, 0.0),
            TestPoint(50.0, 5, 1.0), // avg 25f
            TestPoint(100.0, 5, 2.0) // Clicks over
        )
        walkPath(path)

        assertEquals(2, history.calculatedIntervals.size)
        val firstInterval = history.calculatedIntervals[0]
        val secondInterval = history.calculatedIntervals[1]
        assertEquals(
            1.0 * 25 * 5 / TimeUnit.MINUTES.convert(1, TimeUnit.HOURS),
            firstInterval.energyUsedKwh,
            .0001
        )
        assertEquals(
            1.0 * 75 * 5 / TimeUnit.MINUTES.convert(1, TimeUnit.HOURS),
            secondInterval.energyUsedKwh,
            .0001
        )
    }

    @Test
    fun testEfficiencyWhMi() {
        val path = listOf(
            TestPoint(15.0, 0, 0.0),
            TestPoint(15.0, 1, 1.0), // avg 25f
        )
        walkPath(path)

        assertNotNull(history.getAverageEfficiency(1.0))
        assertEquals(250.0, history.getAverageEfficiency(1.0)!!, .0001)
    }

    @Test
    fun testEfficiencyWhMi_multiElement() {
        val path = listOf(
            TestPoint(300.0, 0, 0.0), // long ago out of window
            TestPoint(10.0, 1, 1.0),
            TestPoint(10.0, 1, 2.0),
            TestPoint(15.0, 1, 3.0),
            TestPoint(15.0, 1, 4.0),
            TestPoint(15.0, 1, 5.0),
        )
        walkPath(path)

        assertNotNull(history.getAverageEfficiency(4.0))
        assertEquals(218.75, history.getAverageEfficiency(4.0)!!, .0001)
    }

    @Test
    fun testSplitElementEfficiency() {
        val path = listOf(
            TestPoint(0.0, 0, 0.0), // long ago out of window
            TestPoint(15.0, 2, 2.0),
            TestPoint(30.0, 2, 4.0),
        )
        walkPath(path)

        // Only half the path. Should be greater than the average of 15kw (250 wh/mi)
        assertEquals(375.0, history.getAverageEfficiency(2.0))
    }

    @Test
    fun testPruneOldIntervals() {
        walkPath(
            listOf(
                TestPoint(15.0, 0, 0.0), // Should get pruned
                TestPoint(0.0, 0, 1.0),
                TestPoint(0.0, 2, 201.0),
            )
        )
        // Oldest one should have been pruned
        assertEquals(1, history.calculatedIntervals.size)
    }

    @Test
    fun testDontPruneIntervalsCrossingTheLine() {
        walkPath(
            listOf(
                TestPoint(15.0, 0, 0.0), // Should not get pruned
                TestPoint(0.0, 0, 200.0),
                TestPoint(0.0, 2, 201.0),
            )
        )
        assertEquals(2, history.calculatedIntervals.size)
    }

    @Test
    fun testMergeIntervals() {
        walkPath(
            listOf(
                // Very low differing intervals should be combined.
                TestPoint(15.0, 0, 0.0),
                TestPoint(15.0, 0, 0.1),
                TestPoint(15.0, 0, 0.2),
                TestPoint(15.0, 0, 6.0),
            )
        )
        assertEquals(2, history.calculatedIntervals.size)
    }


    data class TestPoint(val powerKw: Double, val durationMinutes: Int, val odometerMi: Double)

    /** power, seconds increment, odometer each step */
    private fun walkPath(path: List<TestPoint>) {
        path.forEach {
            time += it.durationMinutes.minutes
            history.addInstantaneousPower(it.powerKw, time.markNow(), it.odometerMi)
        }
    }
}