package app.candash.cluster

import java.util.SortedMap
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class IntegratingEfficiencyHistory {

    // Raw Recent Logs
    private val instantaneousReadings: MutableList<InstantaneousReading> = mutableListOf()

    // Historical efficiency
    private val _calculatedIntervals: MutableList<CalculatedInterval> = mutableListOf()
    internal val calculatedIntervals get(): List<CalculatedInterval> = _calculatedIntervals

    // Adds a new power reading and maybe completes an interval or prunes past collected data.
    // Assumes odometer only ever increases
    fun addInstantaneousPower(powerKw: Double, timestamp: ComparableTimeMark, odometerMi: Double) {
        instantaneousReadings += InstantaneousReading(powerKw, timestamp, odometerMi)

        integrateInstantaneousReadings()
        pruneOldIntervals()
        mergeOldIntervals()
    }

    // Computes a new interval if we have enough data to do so, and removes the instantaneous
    // readings for that interval
    private fun integrateInstantaneousReadings() {
        if (instantaneousReadings.size >= 2
            && latestOdometer - instantaneousReadings.first().odometerMi >= minimumIntervalMi
        ) {
            val zippedIntervals = instantaneousReadings.toList().zipWithNext { left, right ->
                // Leaves the most recent reading to allow averaging for the next interval.
                instantaneousReadings.remove(left)
                val elapsedTime: Duration = (right.timestamp - left.timestamp).absoluteValue
                val avgKw = listOf(
                    left.powerKw, right.powerKw
                ).average()
                val energyUsedKwh =
                    elapsedTime.inWholeNanoseconds * avgKw / 1.hours.inWholeNanoseconds
                val distance = abs(right.odometerMi - left.odometerMi)
                CalculatedInterval(
                    energyUsedKwh, elapsedTime, min(left.odometerMi, right.odometerMi), distance
                )
            }
            _calculatedIntervals += zippedIntervals.reduce(CalculatedInterval::plus)
        }
    }

// TODO(Handle case where we're not moving at all but still collecting data.

    private fun pruneOldIntervals() {
        while (true) {
            val earliest = earliestIntervalOdometer ?: break
            if (shouldPrune(earliest)) {
                _calculatedIntervals.removeAt(0)
            } else {
                break
            }
        }
    }

    private fun shouldPrune(earliest: CalculatedInterval) =
        latestOdometer - earliest.endPointMi >= pruneOdometer

    // Given map of past-mileage vs minimum interval size
    // Starting from most recent, traverse backward
    // For each interval
    //
    private fun mergeOldIntervals() {
        val listIterator = _calculatedIntervals.asReversed().listIterator()
        for (interval in listIterator) {
            val maxInterval =
                getLargestIntervalForAge(latestOdometer - interval.endPointMi)
            // Eligible for merging.
            if (eligibleForMerge(interval, maxInterval)) {
                val next = if (listIterator.hasNext()) listIterator.next() else continue
                if (interval.distanceMi + next.distanceMi <= maxInterval) {
                    listIterator.remove()
                    listIterator.previous()
                    listIterator.set(interval + next)
                }
            }
        }
    }

    private fun eligibleForMerge(
        interval: CalculatedInterval,
        maxIntervalDistanceMi: Double
    ): Boolean {
        return interval.distanceMi < maxIntervalDistanceMi
                || interval.duration > maxIntervalDuration
    }

    private fun getLargestIntervalForAge(miles: Double): Double {
        // For how old an interval is, what is the precision guarantee (max allowed interval)
        // Intervals smaller than this will be attempted to be merged
        val intervalMaxInterval: SortedMap<Double, Double> = sortedMapOf(
            5.0 to 0.1,
            10.0 to 0.2,
            20.0 to 0.5,
            100.0 to 1.0,
            pruneOdometer to 2.0, // should be last, we'd hope.
            Double.MAX_VALUE to 10.0 // guarantee it
        )
        // Tailmap includes exactly equal.
        return intervalMaxInterval[intervalMaxInterval.tailMap(miles).firstKey()]!!
    }

    private val earliestIntervalOdometer get() = calculatedIntervals.firstOrNull()
    private val latestOdometer get() = instantaneousReadings.last().odometerMi

    /**
     * Returns the most recently calculated interval, on the order of .1 mi.
     * Mostly for testing, but could be usefulish for a smoothed-live display
     */
    fun getLatestInterval(): CalculatedInterval? {
        return calculatedIntervals.lastOrNull()
    }

    fun getAverageEfficiency(lookbackMiles: Double): Double? {
        val earliestOdometer = latestOdometer - lookbackMiles

        val earlySlice = calculatedIntervals.indexOfLast { it.startPointMi <= earliestOdometer }
        val lateSlice = calculatedIntervals.size  // last for now
        if (earlySlice == -1 || lateSlice == 0) return null

        return getIntervalSlice(earliestOdometer, latestOdometer).averageEfficiencyWhPerMi
    }

    private fun getIntervalSlice(
        earliestOdometer: Double, latestOdometer: Double
    ): CalculatedInterval {
        val earlySlice = calculatedIntervals.indexOfLast { it.startPointMi <= earliestOdometer }
        val lateSlice = calculatedIntervals.size  // last for now

        val superList: MutableList<CalculatedInterval> =
            calculatedIntervals.subList(earlySlice, lateSlice).toMutableList()

        if (superList.first().startPointMi != earliestOdometer) {
            superList[0] = superList.first().split(earliestOdometer).second
        }
        if (superList.last().endPointMi != latestOdometer) {
            superList[superList.size - 1] = superList.last().split(latestOdometer).first
        }
        // Best to average out the cuts at the start and end, rather than the whole slice.
        return superList.reduce(CalculatedInterval::plus)
    }

    // Public API. Implicitly assumes we have the most recent odometer reading.
// fun getAverageEfficiency(lookbackMiles: Double)
// fun getPowerUsage(lookbackMiles: Double
    companion object {
        // Once we have enough readings to construct an interval this long, we will do so.
        const val minimumIntervalMi = .1
        const val pruneOdometer = 200.0
        val maxIntervalDuration = 5.minutes

    }
}

/** An instantaneous reading of the power draw at the timestamp.
 *
 * Includes odometer for easier indexing on a wh/mi basis.
 */
data class InstantaneousReading(
    val powerKw: Double, val timestamp: ComparableTimeMark, val odometerMi: Double
)

/**
 * An integrated usage period.
 *
 * Instantaneous readings are periodically collected into discrete intervals of a certain size. Intervals
 * can be combined to reduce size requirements over long durations.
 */
data class CalculatedInterval(
    val energyUsedKwh: Double,
    val duration: Duration,
    val startPointMi: Double,
    val distanceMi: Double,
) {
    operator fun plus(other: CalculatedInterval): CalculatedInterval = CalculatedInterval(
        this.energyUsedKwh + other.energyUsedKwh,
        this.duration + other.duration,
        min(this.startPointMi, other.startPointMi),
        this.distanceMi + other.distanceMi
    )

    val averageEfficiencyWhPerMi: Double
        get() = energyUsedKwh * 1000 / distanceMi


    fun split(splitLineOdometerMi: Double): Pair<CalculatedInterval, CalculatedInterval> {
        check(splitLineOdometerMi > this.startPointMi && splitLineOdometerMi < endPointMi) { "Odometer split point not in range of this interval" }
        var frac = (splitLineOdometerMi - startPointMi) / distanceMi
        check(frac != 0.0 && frac != 1.0)

        val a = CalculatedInterval(
            this.energyUsedKwh * frac, this.duration.times(frac), startPointMi, distanceMi * frac
        )
        frac = 1 - frac
        val b = CalculatedInterval(
            this.energyUsedKwh * frac,
            this.duration.times(frac),
            startPointMi + distanceMi * frac,
            distanceMi * (1 - frac)
        )
        return Pair(a, b)
    }

    val endPointMi get() = startPointMi + distanceMi
}

// TLDR:
// Occasionally inputs instantaneous power + timestamp
// Holds recent history
// Lookback window applies and saves the integral
// Apply smoothing? Requires N+1 readings, which is fine.
// Clear the history occasionally
// TLDR, there is a difference between the raw logs and the saved integrations.

// Odometer vs time: We're trying to integrate the wh/mi, not actual wh.
// But, with a raw power reading.... We actually need the time to integrate into wh
// So it's an integrating wh detector, and then you can do with that as wished.
// That also lets me validate it