package app.candash.cluster

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class EfficiencyCalculator(
    private val carState: CarState,
    private val prefs: SharedPreferences
) {
    private val gson = Gson()

    // these triples contain (odometerKm, kwhDischarge, kwhCharge)
    private var kwhHistory = mutableListOf<Triple<Float, Float, Float>>()
    private var parkedKwhHistory = mutableListOf<Triple<Float, Float, Float>>()
    private var parkedStartKwh: Pair<Float, Float>? = null
    private val tripleFloatListType =
        object : TypeToken<MutableList<Triple<Float, Float, Float>>>() {}.type

    fun changeLookBack(): String {
        val inMiles = prefs.getBooleanPref(Constants.uiSpeedUnitsMPH)
        val old = prefs.getPref(Constants.efficiencyLookBack)
        val options = if (inMiles) {
            listOf(0f, 1f.miToKm, 5f.miToKm, 15f.miToKm, 30f.miToKm)
        } else {
            listOf(0f, 10f, 25f, 50f)
        }
        val index = (options.indexOf(old) + 1) % options.size
        prefs.setPref(Constants.efficiencyLookBack, options[index])

        return if (inMiles) {
            "Last %.0f miles".format(options[index].kmToMi)
        } else {
            "Last %.0f kilometers".format(options[index])
        }
    }

    fun clearHistory() {
        kwhHistory.clear()
        parkedKwhHistory.clear()
        saveHistoryToPrefs()
    }

    fun getEfficiencyText(lookBackKm: Float = prefs.getPref(Constants.efficiencyLookBack)): String {
        val inMiles = prefs.getBooleanPref(Constants.uiSpeedUnitsMPH)
        val power = carState[SName.power] ?: 0f
        return if (lookBackKm == 0f) {
            getInstantEfficiencyText(inMiles, power)
        } else {
            getRecentEfficiencyText(inMiles, lookBackKm)
        }
    }

    fun updateKwhHistory() {
        loadHistoryFromPrefs()
        val odo = carState[SName.odometer]
        val kwhDischargeTotal = carState[SName.kwhDischargeTotal]
        val kwhChargeTotal = carState[SName.kwhChargeTotal]
        if (odo == null || kwhDischargeTotal == null || kwhChargeTotal == null) {
            return
        }
        updateParkedHistory(odo, kwhDischargeTotal, kwhChargeTotal)
        updateHistory(odo, kwhDischargeTotal, kwhChargeTotal)
    }

    private fun updateParkedHistory(odo: Float, discharge: Float, charge: Float) {
        val gear = carState[SName.gearSelected] ?: SVal.gearInvalid
        // Switching from D/R/N to Park
        if (gear in setOf(SVal.gearPark, SVal.gearInvalid) && parkedStartKwh == null) {
            parkedStartKwh = Pair(discharge, charge)
            saveHistoryToPrefs()
            return
        }

        // Switching from Park to D/R/N
        if (gear !in setOf(SVal.gearPark, SVal.gearInvalid) && parkedStartKwh != null) {
            parkedKwhHistory.add(
                Triple(
                    odo,
                    discharge - parkedStartKwh!!.first,
                    charge - parkedStartKwh!!.second
                )
            )
            parkedStartKwh = null
            while (parkedKwhHistory.firstOrNull { it.first < odo - 51f } != null) {
                parkedKwhHistory.removeFirst()
            }
            saveHistoryToPrefs()
            return
        }
    }

    private fun updateHistory(odo: Float, discharge: Float, charge: Float) {
        val histEndOdo = kwhHistory.lastOrNull()?.first ?: 0f
        val newTriple = Triple(odo, discharge, charge)
        if (odo - histEndOdo >= 1.0 || odo - histEndOdo < 0f) {
            // We're missing too much data, start over
            kwhHistory.clear()
            kwhHistory.add(newTriple)
        } else if (odo - histEndOdo >= 0.1) {
            // We have travelled far enough, add to history
            kwhHistory.add(newTriple)
        } else {
            // We haven't travelled far enough yet, return because
            // there is no need to cleanup or save to prefs
            return
        }
        // Cleanup history from more than 51 km ago
        while (kwhHistory.firstOrNull { it.first < odo - 51f } != null) {
            kwhHistory.removeFirst()
        }
        saveHistoryToPrefs()
    }

    private fun getInstantEfficiencyText(inMiles: Boolean, power: Float): String {
        val speed = carState[SName.uiSpeed] ?: 0f
        // If speed is 0 (or null) return to prevent "infinity kWh/mi"
        if (speed == 0f) {
            return "-"
        }
        val instantEfficiencyWh = power / speed
        return if (inMiles) {
            "%.0f Wh/mi".format(instantEfficiencyWh)
        } else {
            "%.0f Wh/km".format(instantEfficiencyWh)
        }
    }

    private fun getRecentEfficiencyText(inMiles: Boolean, lookBackKm: Float): String {
        val newOdo = carState[SName.odometer]
        // If parked, use the (dis)charge values from the start of park so display doesn't change
        val newDischarge = parkedStartKwh?.first ?: carState[SName.kwhDischargeTotal]
        val newCharge = parkedStartKwh?.second ?: carState[SName.kwhChargeTotal]
        if (newOdo == null || newDischarge == null || newCharge == null) {
            return "-"
        }
        val targetKm = newOdo - lookBackKm
        val oldKwhTriple = kwhHistory.lastOrNull { it.first <= targetKm }
            ?: return getCalculatingText(inMiles, lookBackKm, newOdo)

        val odoDelta = newOdo - oldKwhTriple.first
        var dischargeDeltaKwh = newDischarge - oldKwhTriple.second
        var chargeDeltaKwh = newCharge - oldKwhTriple.third
        // Subtract parked consumption/charges
        for (parkedTriple in parkedKwhHistory.filter { it.first >= targetKm }) {
            dischargeDeltaKwh -= parkedTriple.second
            chargeDeltaKwh -= parkedTriple.third
        }
        val consumedWh = (dischargeDeltaKwh - chargeDeltaKwh) * 1000
        return if (inMiles) {
            "%.0f Wh/mi".format(consumedWh / odoDelta.kmToMi)
        } else {
            "%.0f Wh/km".format(consumedWh / odoDelta)
        }
    }

    private fun getCalculatingText(inMiles: Boolean, lookBackKm: Float, odo: Float): String {
        if (kwhHistory.size > 0) {
            val distanceKm = odo - kwhHistory.first().first
            // Using toInt as a floor rounding
            return if (inMiles) {
                "(%d of %d mi)".format(
                    distanceKm.kmToMi.toInt(),
                    lookBackKm.kmToMi.toInt()
                )
            } else {
                "(%d of %d km)".format(
                    distanceKm.toInt(),
                    lookBackKm.toInt()
                )
            }
        } else {
            return "(calculating)"
        }
    }

    private fun saveHistoryToPrefs() {
        prefs.setStringPref(Constants.kwhHistory, gson.toJson(kwhHistory))
        prefs.setStringPref(Constants.parkedKwhHistory, gson.toJson(parkedKwhHistory))
        prefs.setPref(Constants.parkedStartKwhDischarge, parkedStartKwh?.first ?: 0f)
        prefs.setPref(Constants.parkedStartKwhCharge, parkedStartKwh?.second ?: 0f)
    }

    private fun loadHistoryFromPrefs() {
        if (kwhHistory.isEmpty() && prefs.getStringPref(Constants.kwhHistory) != null) {
            kwhHistory =
                gson.fromJson(prefs.getStringPref(Constants.kwhHistory), tripleFloatListType)
        }
        if (parkedKwhHistory.isEmpty() && prefs.getStringPref(Constants.parkedKwhHistory) != null) {
            parkedKwhHistory =
                gson.fromJson(prefs.getStringPref(Constants.parkedKwhHistory), tripleFloatListType)
        }
        if (parkedStartKwh == null && prefs.getPref(Constants.parkedStartKwhDischarge) > 0) {
            parkedStartKwh = Pair(
                prefs.getPref(Constants.parkedStartKwhDischarge),
                prefs.getPref(Constants.parkedStartKwhCharge)
            )
        }
    }
}