package app.candash.cluster

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.GradientDrawable.Orientation
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import app.candash.cluster.databinding.FragmentDashBinding
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class DashFragment : Fragment() {
    private lateinit var binding: FragmentDashBinding
    private lateinit var viewModel: DashViewModel
    private lateinit var unitConverter: UnitConverter
    private lateinit var prefs: SharedPreferences
    private var savedLayoutParams: MutableMap<View, ConstraintLayout.LayoutParams> = mutableMapOf()

    // Gradient animations:
    private lateinit var autopilotAnimation: ValueAnimator
    private lateinit var blindspotAnimation: ValueAnimator
    private lateinit var overlayGradient: GradientDrawable
    private var gradientColorFrom: Int = 0

    private val Int.px: Int
        get() = (this * Resources.getSystem().displayMetrics.density).toInt()

    /**
     * This rounds a float to the provided decimal places and returns a string
     * which is formatted to show that many decimal places.
     *
     * @param dp If null, decimal places are automatically determined by size of the Float
     */
    private fun Float.roundToString(dp: Int? = null): String {
        return when {
            dp != null -> "%.${dp}f".format(this)
            abs(this) < 1f -> "%.2f".format(this)
            abs(this) < 10f -> "%.1f".format(this)
            else -> "%.0f".format(this)
        }
    }

    /**
     * This converts a float to the correct unit of measurement specified in sharedPreferences,
     * then rounds it to the provided decimal places and returns a string
     * which is formatted to show that many decimal places.
     *
     * @param nativeUnit Specify the original unit of measurement of the value
     * @param dp If null, decimal places are automatically determined by size of the Float
     */
    private fun Float.convertAndRoundToString(nativeUnit: Units, dp: Int? = null): String {
        return unitConverter.convertToPreferredUnit(nativeUnit, this).roundToString(dp)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashBinding.inflate(inflater, container, false)
        prefs = requireContext().getSharedPreferences("dash", Context.MODE_PRIVATE)
        return binding.root
    }

    /**
     * These views are bumped up when in split screen closer to the status bar
     */
    private fun topUIViews(): Set<View> =
        setOf(
            binding.PRND,
            binding.batterypercent,
            binding.battery,
            binding.batteryOverlay,
            binding.leftTurnSignalLight,
            binding.leftTurnSignalDark,
            binding.rightTurnSignalLight,
            binding.rightTurnSignalDark,
            binding.autopilot,
            binding.TACC
        )

    /**
     * These are telltales which should be hidden when in split screen
     */
    private fun nonSplitScreenTelltaleUIViews(): Set<View> =
        setOf(
            binding.telltaleDrl,
            binding.telltaleLb,
            binding.telltaleHb,
            binding.telltaleAhbStdby,
            binding.telltaleAhbActive,
            binding.telltaleFogFront,
            binding.telltaleFogRear,
            binding.odometer,
            binding.battCharge,
            binding.battHeat,
            binding.telltaleTPMSFaultHard,
            binding.telltaleTPMSFaultSoft,
            binding.telltaleLimRegen
        )

    private fun leftSideUIViews(): Set<View> =
        setOf(
            binding.fronttemp,
            binding.fronttemplabel,
            binding.fronttempunits,
            binding.fronttempgauge,
            binding.reartemp,
            binding.reartemplabel,
            binding.reartempunits,
            binding.reartempgauge,
            binding.frontbraketemp,
            binding.frontbraketemplabel,
            binding.frontbraketempunits,
            binding.frontbraketempgauge,
            binding.rearbraketemp,
            binding.rearbraketemplabel,
            binding.rearbraketempunits,
            binding.rearbraketempgauge,
        )

    private fun rightSideUIViews(): Set<View> =
        setOf(
            binding.fronttorque,
            binding.fronttorquelabel,
            binding.fronttorqueunits,
            binding.fronttorquegauge,
            binding.reartorque,
            binding.reartorquelabel,
            binding.reartorqueunits,
            binding.reartorquegauge,
            binding.coolantflow,
            binding.coolantflowlabel,
            binding.coolantflowunits,
            binding.coolantflowgauge,
            binding.batttemp,
            binding.batttemplabel,
            binding.batttempunits,
            binding.batttempgauge,
        )

    private fun sideUIViews(): Set<View> = leftSideUIViews() + rightSideUIViews()

    private fun awdOnlyViews(): Set<View> =
        setOf(
            binding.fronttemp,
            binding.fronttemplabel,
            binding.fronttempunits,
            binding.fronttempgauge,
            binding.fronttorque,
            binding.fronttorquelabel,
            binding.fronttorqueunits,
            binding.fronttorquegauge,
        )

    private fun chargingViews(): Set<View> =
        setOf(
            binding.bigsoc,
            binding.bigsocpercent,
            binding.chargerate,
            binding.chargemeter
        )

    private fun chargingHiddenViews(): Set<View> =
        setOf(
            binding.powerBar,
            binding.power,
            binding.speed,
            binding.unit,
        )

    private fun centerDoorHiddenViews(): Set<View> =
        setOf(
            binding.speed,
            binding.unit,
        ) + chargingViews()

    private fun doorViews(): Set<View> =
        setOf(
            binding.modely,
            binding.frontleftdoor,
            binding.frontrightdoor,
            binding.rearleftdoor,
            binding.rearrightdoor,
            binding.hood,
            binding.hatch
        )

    private fun doorViewsCenter(): Set<View> =
        setOf(
            binding.modelyCenter,
            binding.frontleftdoorCenter,
            binding.frontrightdoorCenter,
            binding.rearleftdoorCenter,
            binding.rearrightdoorCenter,
            binding.hoodCenter,
            binding.hatchCenter
        )

    private fun minMaxChargingHiddenViews(): Set<View> =
        setOf(
            binding.maxpower,
            binding.minpower,
        )

    private fun getScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager
            ?.defaultDisplay?.getMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

    private fun getRealScreenWidth(): Int {
        val displayMetrics = DisplayMetrics()
        activity?.windowManager
            ?.defaultDisplay?.getRealMetrics(displayMetrics)
        return displayMetrics.widthPixels
    }

    private fun isSplitScreen(): Boolean {
        return getRealScreenWidth() > getScreenWidth() * 2
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        prefs = requireContext().getSharedPreferences("dash", Context.MODE_PRIVATE)
        unitConverter = UnitConverter(prefs)

        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(DashViewModel::class.java)

        if (!prefs.prefContains(Constants.gaugeMode)) {
            prefs.setPref(Constants.gaugeMode, Constants.showFullGauges)
        }

        if (prefs.getBooleanPref(Constants.tempInF)) {
            binding.frontbraketempunits.text = "°F"
            binding.rearbraketempunits.text = "°F"
            binding.fronttempunits.text = "°F"
            binding.reartempunits.text = "°F"
            binding.batttempunits.text = "°F"
        } else {
            binding.frontbraketempunits.text = "°C"
            binding.rearbraketempunits.text = "°C"
            binding.fronttempunits.text = "°C"
            binding.reartempunits.text = "°C"
            binding.batttempunits.text = "°C"
        }

        if (prefs.getBooleanPref(Constants.torqueInLbfFt)) {
            binding.fronttorqueunits.text = "lb-ft"
            binding.reartorqueunits.text = "lb-ft"
        } else {
            binding.fronttorqueunits.text = "Nm"
            binding.reartorqueunits.text = "Nm"
        }

        setupGradientOverlays()

        binding.blackout.visibility = View.GONE

        if (!isSplitScreen()) {
            for (topUIView in topUIViews()) {
                savedLayoutParams[topUIView] =
                    ConstraintLayout.LayoutParams(topUIView.layoutParams as ConstraintLayout.LayoutParams)
            }
        }
        
        setColors()
        setGaugeVisibility()
        setLayoutOrder()

        binding.minpower.setOnClickListener {
            prefs.setPref("minPower", 0f)
        }
        binding.maxpower.setOnClickListener {
            prefs.setPref("maxPower", 0f)
        }
        binding.root.setOnLongClickListener {
            viewModel.switchToInfoFragment()
            viewModel.switchToInfoFragment()
            return@setOnLongClickListener true
        }
        binding.batterypercent.setOnClickListener {
            prefs.setBooleanPref(Constants.showBattRange, prefs.getBooleanPref(Constants.showBattRange))
            processBattery()
        }
        binding.unit.setOnClickListener {
            if (prefs.getPref(Constants.gaugeMode) < Constants.showFullGauges) {
                prefs.setPref(Constants.gaugeMode, prefs.getPref(Constants.gaugeMode) + 1f)
            } else {
                prefs.setPref(Constants.gaugeMode, Constants.showSimpleGauges)
            }
            setGaugeVisibility()
        }

        binding.power.setOnClickListener {
            if (prefs.getPref(Constants.powerUnits) < Constants.powerUnitPs) {
                prefs.setPref(Constants.powerUnits, prefs.getPref(Constants.powerUnits) + 1f)
            } else {
                prefs.setPref(Constants.powerUnits, Constants.powerUnitKw)
            }
        }

        binding.PRND.setOnLongClickListener {
            prefs.setBooleanPref(Constants.forceRHD, !prefs.getBooleanPref(Constants.forceRHD))
            setLayoutOrder()
            return@setOnLongClickListener true
        }

        binding.speed.setOnLongClickListener {
            prefs.setBooleanPref(Constants.forceNightMode, !prefs.getBooleanPref(Constants.forceNightMode))
            setColors()
            return@setOnLongClickListener true
        }

        val efficiencyCalculator = EfficiencyCalculator(viewModel, prefs)

        binding.efficiency.setOnClickListener {
            binding.infoToast.text = efficiencyCalculator.changeLookBack()
            binding.infoToast.visibility = View.VISIBLE
            binding.infoToast.startAnimation(fadeOut(5000))
        }

        viewModel.getSplitScreen().observe(viewLifecycleOwner) { isSplit ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && view.windowToken != null) {
                // only needed for Android 11+
                if (isSplit) {
                    for (topUIView in topUIViews()) {
                        val params = topUIView.layoutParams as ConstraintLayout.LayoutParams
                        val savedParams = savedLayoutParams[topUIView]
                        params.setMargins(
                            savedParams!!.leftMargin,
                            savedParams.topMargin - 30.px,
                            savedParams.rightMargin,
                            savedParams.bottomMargin
                        )
                        topUIView.layoutParams = params
                    }
                } else {
                    //no split screen
                    for (topUIView in topUIViews()) {
                        val params = topUIView.layoutParams as ConstraintLayout.LayoutParams
                        val savedParams = savedLayoutParams[topUIView]
                        params.setMargins(
                            savedParams!!.leftMargin,
                            savedParams.topMargin,
                            savedParams.rightMargin,
                            savedParams.bottomMargin
                        )
                        topUIView.layoutParams = params
                    }
                }
            }
            // Update views which are affected by split screen changes
            setGaugeVisibility()
            updateDoorStateUI()
            updateSplitScreenTellTales()
            updateSpeedLimitSign()
        }

        viewModel.onSignal(viewLifecycleOwner, SName.driverOrientation) {
            prefs.setBooleanPref(
                Constants.detectedRHD,
                it in setOf(2f, 4f)
            )
            setLayoutOrder()
        }

        viewModel.onSignal(viewLifecycleOwner, SName.driveConfig) {
            if (it == SVal.rwd) {
                binding.fronttorquegauge.visibility = View.INVISIBLE
                binding.fronttorquelabel.visibility = View.INVISIBLE
                binding.fronttorque.visibility = View.INVISIBLE
                binding.fronttorqueunits.visibility = View.INVISIBLE
                binding.fronttempgauge.visibility = View.INVISIBLE
                binding.fronttemplabel.visibility = View.INVISIBLE
                binding.fronttemp.visibility = View.INVISIBLE
                binding.fronttempunits.visibility = View.INVISIBLE
            }
        }

        // set display night/day mode based on reported car status
        viewModel.onSignal(viewLifecycleOwner, SName.isSunUp) {
            setColors()
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.displayOn, SName.gearSelected)) {
            if (it[SName.gearSelected] !in setOf(SVal.gearDrive, SVal.gearReverse)
                && it[SName.displayOn] == 0f
                && prefs.getBooleanPref(Constants.blankDisplaySync)
            ) {
                binding.blackout.visibility = View.VISIBLE
                binding.infoToast.text =
                    "Display sleeping with car.\nLong-press left edge for settings."
                binding.infoToast.visibility = View.VISIBLE
                binding.infoToast.startAnimation(fadeOut(5000))
            } else {
                binding.blackout.visibility = View.GONE
            }
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.uiSpeed, SName.brakeHold)) {
            val speed = it[SName.uiSpeed]
            if (speed != null) {
                if (it[SName.brakeHold] == 1f) {
                    binding.speed.text = ""
                } else {
                    binding.speed.text = speed.roundToString(0)
                }
            } else {
                binding.speed.text = ""
            }
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.uiSpeedUnits, SName.brakeHold)) {
            val uiSpeedUnits = it[SName.uiSpeedUnits]
            if (uiSpeedUnits != null) {
                prefs.setBooleanPref(Constants.uiSpeedUnitsMPH, (uiSpeedUnits == 0f))
                if (it[SName.brakeHold] == 1f) {
                    binding.unit.text = "HOLD"
                } else {
                    binding.unit.text = unitConverter.prefSpeedUnit().tag.trim().uppercase()
                }
            } else {
                binding.unit.text = ""
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.power) {
            if (it != null) {
                if (it > prefs.getPref("maxPower")) prefs.setPref("maxPower", it)
                if (viewModel.carState[SName.chargeStatus] == SVal.chargeStatusInactive) {
                    // do not store min power if car is charging
                    if (it < prefs.getPref("minPower")) prefs.setPref("minPower", it)
                }
                if (prefs.getPref(Constants.gaugeMode) > Constants.showSimpleGauges) {
                    binding.minpower.visibility = View.VISIBLE
                    binding.maxpower.visibility = View.VISIBLE
                    binding.minpower.text = formatPower(prefs.getPref("minPower"))
                    binding.maxpower.text = formatPower(prefs.getPref("maxPower"))

                } else {
                    binding.minpower.visibility = View.INVISIBLE
                    binding.maxpower.visibility = View.INVISIBLE
                }
                binding.power.text = formatPower(it)

                if (it >= 0) {
                    binding.powerBar.setGauge(((it / prefs.getPref("maxPower")).pow(0.75f)))
                } else {
                    binding.powerBar.setGauge(
                        -((abs(it) / abs(prefs.getPref("minPower"))).pow(
                            0.75f
                        ))
                    )
                }
                binding.powerBar.visibility = View.VISIBLE
                binding.chargerate.text = it.wToKw.roundToString() + " kW"
            } else {
                binding.powerBar.setGauge(0f)
                binding.powerBar.visibility = View.INVISIBLE
                binding.power.text = ""
                binding.minpower.text = ""
                binding.maxpower.text = ""
                binding.chargerate.text = ""
            }
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.gearSelected, SName.autopilotState)) {
            updateGearView()
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.brakeTempFL, SName.brakeTempFR)) {
            val fl = it[SName.brakeTempFL]
            val fr = it[SName.brakeTempFR]
            if (fl != null && fr != null) {
                val frontBrakeTemp = max(fl, fr)
                binding.frontbraketemp.text = frontBrakeTemp.convertAndRoundToString(Units.TEMPERATURE_C, 0)
                binding.frontbraketempgauge.setGauge(frontBrakeTemp / 984f)
            } else {
                binding.frontbraketemp.text = ""
                binding.frontbraketempgauge.setGauge(0f)
            }
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.brakeTempRL, SName.brakeTempRR)) {
            val rl = it[SName.brakeTempRL]
            val rr = it[SName.brakeTempRR]
            if (rl != null && rr != null) {
                val rearBrakeTemp = max(rl, rr)
                binding.rearbraketemp.text = rearBrakeTemp.convertAndRoundToString(Units.TEMPERATURE_C, 0)
                binding.rearbraketempgauge.setGauge(rearBrakeTemp / 984f)
            } else {
                binding.rearbraketemp.text = ""
                binding.rearbraketempgauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.frontTemp) {
            if (it != null) {
                binding.fronttemp.text = it.convertAndRoundToString(Units.TEMPERATURE_C, 0)
                binding.fronttempgauge.setGauge(it / 214f)
            } else {
                binding.fronttemp.text = ""
                binding.fronttempgauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.rearTemp) {
            if (it != null) {
                binding.reartemp.text = it.convertAndRoundToString(Units.TEMPERATURE_C, 0)
                binding.reartempgauge.setGauge(it / 214f)
            } else {
                binding.reartemp.text = ""
                binding.reartempgauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.coolantFlow) {
            if (it != null) {
                binding.coolantflow.text = it.roundToString(1)
                binding.coolantflowgauge.setGauge(it / 40f)
            } else {
                binding.coolantflow.text = ""
                binding.coolantflowgauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.frontTorque) {
            if (it != null) {
                var frontTorqueVal = it
                if (viewModel.carState[SName.gearSelected] == SVal.gearReverse) {
                    frontTorqueVal = -(frontTorqueVal)
                }
                binding.fronttorque.text = frontTorqueVal.convertAndRoundToString(Units.TORQUE_NM, 0)
                if (prefs.getPref("frontTorqueMax") < abs(frontTorqueVal)) {
                    prefs.setPref("frontTorqueMax", abs(frontTorqueVal))
                }
                binding.fronttorquegauge.setGauge(frontTorqueVal / prefs.getPref("frontTorqueMax"))
            } else {
                binding.fronttorque.text = ""
                binding.fronttorquegauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.rearTorque) {
            if (it != null) {
                var rearTorqueVal = it
                if (viewModel.carState[SName.gearSelected] == SVal.gearReverse) {
                    rearTorqueVal = -(rearTorqueVal)
                }
                binding.reartorque.text = rearTorqueVal.convertAndRoundToString(Units.TORQUE_NM, 0)
                if (abs(prefs.getPref("rearTorqueMax")) < rearTorqueVal) {
                    prefs.setPref("rearTorqueMax", abs(rearTorqueVal))
                }
                binding.reartorquegauge.setGauge(rearTorqueVal / prefs.getPref("rearTorqueMax"))
            } else {
                binding.reartorque.text = ""
                binding.reartorquegauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.battBrickMin) {
            if (it != null) {
                binding.batttemp.text = it.convertAndRoundToString(Units.TEMPERATURE_C, 0)
                binding.batttempgauge.setGauge((it + 40f) / 128)
            } else {
                binding.batttemp.text = ""
                binding.batttempgauge.setGauge(0f)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.autopilotHands) {
            updateAPWarning(it ?: 0f)
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.uiRange, SName.stateOfCharge)) { processBattery() }

        viewModel.onSomeSignals(viewLifecycleOwner, SGroup.closures) { updateDoorStateUI() }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.autopilotState, SName.gearSelected, SName.brakeApplied)) {
            updateAutopilotUI()
        }

        viewModel.onSignal(viewLifecycleOwner, SName.steeringAngle) {
            updateAutopilotRotation()
        }

        // Disabled until we replace the letters with an icon
        /*viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.accState, SName.accSpeedLimit, SName.gearSelected, SName.brakeApplied)) {
            updateTaccUI()
        }*/

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.gearSelected, SName.fusedSpeedLimit)) {
            updateSpeedLimitSign()
        }

        // Basic telltales all have the same logic:
        // If second == third: show first; else: hide first
        val basicTellTalesHide = setOf(
            Triple(binding.telltaleFogRear, SName.rearFogSwitch, SVal.rearFogSwitchOn),
            Triple(binding.telltaleFogFront, SName.frontFogSwitch, SVal.frontFogSwitchOn),
            Triple(binding.speedoBrakeHold, SName.brakeHold, 1f),
            Triple(binding.telltaleTPMSFaultHard, SName.tpmsHard, 1f),
            Triple(binding.telltaleTPMSFaultSoft, SName.tpmsSoft, 1f),
            Triple(binding.leftTurnSignalDark, SName.turnSignalLeft, 1f),
            Triple(binding.leftTurnSignalLight, SName.turnSignalLeft, 2f),
            Triple(binding.rightTurnSignalDark, SName.turnSignalRight, 1f),
            Triple(binding.rightTurnSignalLight, SName.turnSignalRight, 2f),
        )

        basicTellTalesHide.forEach { triple ->
            viewModel.onSignal(viewLifecycleOwner, triple.second) {
                processBasicTellTale(triple)
            }
        }

        // Same as above but sets vis to GONE instead of INVISIBLE
        val basicTellTalesGone = setOf(
            Triple(binding.battHeat, SName.heatBattery, 1f),
            Triple(binding.battCharge, SName.chargeStatus, SVal.chargeStatusActive),
            Triple(binding.telltaleLimRegen, SName.limRegen, 1f),
        )

        basicTellTalesGone.forEach { triple ->
            viewModel.onSignal(viewLifecycleOwner, triple.second) {
                processBasicTellTale(triple, remove=true)
            }
        }

        // If any telltales need more advanced logic add them below
        // If any should be hidden when in split screen, add to nonSplitScreenTelltaleUIViews

        /*viewModel.onSignal(viewLifecycleOwner, SName.lightSwitch) {
            val lightSwitchVal = it ?: SVal.lightsOff
            if ((lightSwitchVal == SVal.lightsPark) or
                (lightSwitchVal == SVal.lightsOn) or
                (lightSwitchVal == SVal.lightsAuto)) {
                binding.telltaleDrl.visibility = View.VISIBLE
            } else {
                binding.telltaleDrl.visibility = View.INVISIBLE
            }
            if ((lightSwitchVal == SVal.lightsOn) or
                (lightSwitchVal == SVal.lightsAuto)) {
                binding.telltaleLb.visibility = View.VISIBLE
            } else {
                binding.telltaleLb.visibility = View.INVISIBLE
            }
        }*/

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.autoHighBeamEnabled, SName.highBeamRequest, SName.highLowBeamDecision)) {
            val ahbEnabledVal = it[SName.autoHighBeamEnabled]
            if (ahbEnabledVal == null) {
                binding.telltaleAhbStdby.visibility = View.INVISIBLE
                binding.telltaleAhbActive.visibility = View.INVISIBLE
            } else {
                if (ahbEnabledVal == 1f) {
                    binding.telltaleHb.visibility = View.INVISIBLE
                    if (it[SName.highBeamRequest] == 1f) {
                        if (it[SName.highLowBeamDecision] == 2f) {
                            // Auto High Beam is on, AHB decision is ON
                            binding.telltaleAhbStdby.visibility = View.INVISIBLE
                            binding.telltaleAhbActive.visibility = View.VISIBLE
                            binding.telltaleLb.visibility = View.INVISIBLE
                        } else {
                            // Auto High Beam is on, AHB decision is OFF
                            binding.telltaleAhbStdby.visibility = View.VISIBLE
                            binding.telltaleAhbActive.visibility = View.INVISIBLE
                            if (it[SName.highBeamStalkStatus] == 1f) {
                                // Pulled on left stalk, flash HB
                                binding.telltaleLb.visibility = View.INVISIBLE
                                binding.telltaleHb.visibility = View.VISIBLE
                            } else {
                                // Stalk idle, business as usual
                                if ((it[SName.lightSwitch] == SVal.lightsOn) or
                                    (it[SName.lightSwitch] == SVal.lightsAuto)) {
                                    binding.telltaleLb.visibility = View.VISIBLE
                                }
                                binding.telltaleHb.visibility = View.INVISIBLE
                            }
                        }
                    } else {
                        binding.telltaleAhbStdby.visibility = View.INVISIBLE
                        binding.telltaleAhbActive.visibility = View.INVISIBLE
                        if (it[SName.highBeamStalkStatus] == 1f) {
                            // Pulled on left stalk, flash HB
                            binding.telltaleLb.visibility = View.INVISIBLE
                            binding.telltaleHb.visibility = View.VISIBLE
                        }
                    }
                } else {
                    binding.telltaleAhbStdby.visibility = View.INVISIBLE
                    binding.telltaleAhbActive.visibility = View.INVISIBLE
                    if ((it[SName.highBeamRequest] == 1f) or
                        (it[SName.highBeamStalkStatus] == 1f)) {
                        binding.telltaleLb.visibility = View.INVISIBLE
                        binding.telltaleHb.visibility = View.VISIBLE
                    } else {
                        if ((it[SName.lightSwitch] == SVal.lightsOn) or
                            (it[SName.lightSwitch] == SVal.lightsAuto)) {
                            binding.telltaleLb.visibility = View.VISIBLE
                        }
                        binding.telltaleHb.visibility = View.INVISIBLE
                    }
                }
            }
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.gearSelected, SName.driverUnbuckled, SName.passengerUnbuckled)) {
            if (gearState() != SVal.gearInvalid &&
                ((it[SName.driverUnbuckled] == 1f) or
                        (it[SName.passengerUnbuckled] == 1f))) {
                binding.telltaleSeatbelt.visibility = View.VISIBLE
            } else {
                binding.telltaleSeatbelt.visibility = View.INVISIBLE
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.brakePark) {
            when (it) {
                SVal.brakeParkRed -> {
                    binding.telltaleBrakePark.visibility = View.VISIBLE
                    binding.telltaleBrakeParkFault.visibility = View.INVISIBLE
                }
                SVal.brakeParkAmber -> {
                    binding.telltaleBrakePark.visibility = View.INVISIBLE
                    binding.telltaleBrakeParkFault.visibility = View.VISIBLE
                }
                else -> {
                    binding.telltaleBrakePark.visibility = View.INVISIBLE
                    binding.telltaleBrakeParkFault.visibility = View.INVISIBLE
                }
            }
        }

        /*viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.odometer, SName.uiSpeedUnits)) {
            val odometerVal = it[SName.odometer]
            if (!prefs.getBooleanPref(Constants.hideOdometer) && odometerVal != null) {
                binding.odometer.visibility = View.VISIBLE
                binding.odometer.text = odometerVal.convertAndRoundToString(
                    Units.DISTANCE_KM,
                    1
                ) + unitConverter.prefDistanceUnit().tag
            } else {
                binding.odometer.visibility = View.INVISIBLE
            }
        }*/

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.odometer, SName.gearSelected)) {
            efficiencyCalculator.updateKwhHistory()
        }

        // Power is always changing, it's enough to only observe this for rapid updates to the efficiency view
        viewModel.onSignal(viewLifecycleOwner, SName.power) {
            val efficiencyText = efficiencyCalculator.getEfficiencyText()
            if (efficiencyText == null || gearState() in setOf(SVal.gearInvalid, SVal.gearPark) || prefs.getBooleanPref(Constants.hideEfficiency)) {
                binding.efficiency.visibility = View.INVISIBLE
            } else {
                binding.efficiency.text = efficiencyText
                binding.efficiency.visibility = View.VISIBLE
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.blindSpotLeft) {
            // Don't show BS warning if in AP
            if ((viewModel.carState[SName.autopilotState] ?: 0f) !in 3f..7f || it == 0f) {
                updateBSWarning(it, binding.BSWarningLeft, Orientation.LEFT_RIGHT)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.blindSpotRight) {
            // Don't show BS warning if in AP
            if ((viewModel.carState[SName.autopilotState] ?: 0f) !in 3f..7f || it == 0f) {
                updateBSWarning(it, binding.BSWarningRight, Orientation.RIGHT_LEFT)
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.forwardCollisionWarning) {
            updateFCWWarning(it)
        }

        viewModel.onSignal(viewLifecycleOwner, SName.leftVehicle) {
            val distance = it ?: 99999f
            val l1Distance = viewModel.carState[SName.l1Distance] ?: Constants.l1DistanceLowSpeed
            val l2Distance = viewModel.carState[SName.l2Distance] ?: Constants.l2DistanceLowSpeed
            if (distance > l1Distance || gearState() in setOf(SVal.gearPark, SVal.gearInvalid) || prefs.getBooleanPref(Constants.hideBs)) {
                binding.blindSpotLeft1.visibility = View.INVISIBLE
                binding.blindSpotLeft2.visibility = View.INVISIBLE
            } else {
                if (distance in l2Distance..l1Distance) {
                    binding.blindSpotLeft1.visibility = View.VISIBLE
                    binding.blindSpotLeft2.visibility = View.INVISIBLE
                } else {
                    binding.blindSpotLeft1.visibility = View.INVISIBLE
                    binding.blindSpotLeft2.visibility = View.VISIBLE
                }
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.rightVehicle) {
            val distance = it ?: 99999f
            val l1Distance = viewModel.carState[SName.l1Distance] ?: Constants.l1DistanceLowSpeed
            val l2Distance = viewModel.carState[SName.l2Distance] ?: Constants.l2DistanceLowSpeed
            if (distance > l1Distance || gearState() in setOf(SVal.gearPark, SVal.gearInvalid) || prefs.getBooleanPref(Constants.hideBs)) {
                binding.blindSpotRight1.visibility = View.INVISIBLE
                binding.blindSpotRight2.visibility = View.INVISIBLE
            } else {
                if (distance in l2Distance..l1Distance) {
                    binding.blindSpotRight1.visibility = View.VISIBLE
                    binding.blindSpotRight2.visibility = View.INVISIBLE
                } else {
                    binding.blindSpotRight1.visibility = View.INVISIBLE
                    binding.blindSpotRight2.visibility = View.VISIBLE
                }
            }
        }

        viewModel.onSomeSignals(viewLifecycleOwner, listOf(SName.PINpassed, SName.brakeApplied)) {
            if (it[SName.PINenabled] == 1f) {
                if (it[SName.PINpassed] == 0f &&
                    binding.PINWarning.visibility != View.VISIBLE &&
                    it[SName.brakeApplied] == 2f) {
                    binding.PINWarning.clearAnimation()
                    binding.PINWarning.startAnimation(fadeIn())
                    binding.PINWarning.visibility = View.VISIBLE
                } else if(it[SName.PINpassed] == 1f) {
                    binding.PINWarning.clearAnimation()
                    if (binding.PINWarning.visibility != View.GONE) {
                        binding.PINWarning.startAnimation(fadeOut())
                        binding.PINWarning.visibility = View.GONE
                    }
                }
            }
        }

        viewModel.onSignal(viewLifecycleOwner, SName.chargeStatus) { value ->
            val chargeStatusVal = value ?: SVal.chargeStatusInactive
            if (chargeStatusVal != SVal.chargeStatusInactive) {
                binding.batteryOverlay.setChargeMode(1)
                minMaxChargingHiddenViews().forEach { it.visibility = View.GONE }
            } else {
                binding.batteryOverlay.setChargeMode(0)
                if (prefs.getPref(Constants.gaugeMode) > Constants.showSimpleGauges) {
                    minMaxChargingHiddenViews().forEach { it.visibility = View.VISIBLE }
                }
            }
            // All of the charging view visibility is handled here
            setGaugeVisibility()
        }

        // Holy gauges Batman, we're done!
    }

    private fun processBattery() {
        val socVal = viewModel.carState[SName.stateOfCharge]
        if (prefs.getBooleanPref(Constants.showBattRange)) {
            val range = viewModel.carState[SName.uiRange]
            binding.batterypercent.text = if (range != null) range.convertAndRoundToString(
                Units.DISTANCE_MI,
                0
            ) + unitConverter.prefDistanceUnit().tag else ""
        } else {
            binding.batterypercent.text = if (socVal != null) socVal.roundToString(0) + " %" else ""
        }
        binding.batteryOverlay.setGauge(socVal ?: 0f)
        binding.battery.visibility = if (socVal == null) View.INVISIBLE else View.VISIBLE

        // Set charge meter stuff too, although they may be hidden
        if (socVal != null) {
            binding.chargemeter.setGauge(socVal / 100f, 4f, true)
            binding.bigsoc.text = socVal.roundToString(0)
        }
    }

    /**
     * This contains all the show/hide logic for various conflicting views.
     * Call this when changing gauge mode, charging, doors, or split screen.
     */
    private fun setGaugeVisibility() {
        // subtract AWD only views from side views before showing them
        val leftSideUIViews = leftSideUIViews().toMutableSet()
        val rightSideUIViews = rightSideUIViews().toMutableSet()
        if (viewModel.carState[SName.driveConfig] == SVal.rwd) {
            leftSideUIViews -= awdOnlyViews()
            rightSideUIViews -= awdOnlyViews()
            awdOnlyViews().forEach { it.visibility = View.INVISIBLE }
        }
        // hide performance gauges if user has elected to hide them or if split screen mode
        if ((prefs.getPref(Constants.gaugeMode) < Constants.showFullGauges) || isSplitScreen()) {
            sideUIViews().forEach { it.visibility = View.INVISIBLE }
        } else {
            // show them only if not covered by the door views
            if (!anyDoorOpen() || driverOrientRHD()) {
                leftSideUIViews.forEach { it.visibility = View.VISIBLE }
            }
            if (!anyDoorOpen() || !driverOrientRHD()) {
                rightSideUIViews.forEach { it.visibility = View.VISIBLE }
            }
        }
        // Hide some gauges when doors are open
        if (anyDoorOpen()) {
            when {
                isSplitScreen() -> centerDoorHiddenViews()
                driverOrientRHD() -> rightSideUIViews
                else -> leftSideUIViews
            }.forEach { it.visibility = View.INVISIBLE }
        }

        val charging = ((viewModel.carState[SName.chargeStatus] ?: SVal.chargeStatusInactive) != SVal.chargeStatusInactive)
        // Show center views after door is closed or switch to fullscreen
        if (!isSplitScreen() || !anyDoorOpen()) {
            (if (charging) chargingViews() else (centerDoorHiddenViews() - chargingViews())).forEach {
                it.visibility = View.VISIBLE
            }
        }
        // Always hide some views depending on charging state
        (if (charging) chargingHiddenViews() else chargingViews()).forEach {
            it.visibility = View.INVISIBLE
        }
        // Always hide unused doors in case of split screen change
        (if (isSplitScreen()) doorViews() else doorViewsCenter()).forEach {
            it.visibility = View.GONE
        }
        // Battery percent is always hidden in split screen
        binding.batterypercent.visibility = if (isSplitScreen()) View.GONE else View.VISIBLE
    }

    private fun driverOrientRHD(): Boolean {
        return (prefs.getBooleanPref(Constants.forceRHD) || prefs.getBooleanPref(Constants.detectedRHD))
    }

    private fun setLayoutOrder() {
        // Mirror some layout elements if RHD market vehicle or user forced RHD
        val reverse = driverOrientRHD()
        val topBar = listOf(
            binding.PRND,
            binding.autopilot,
            binding.TACC,
            null,
            binding.battHeat,
            binding.battCharge,
            binding.batterypercent,
            binding.battery
        )
        val doors = listOf(binding.modely, null)
        setHorizontalConstraints(topBar, reverse)
        setHorizontalConstraints(doors, reverse)

        // In case it's switched while doors are open
        updateDoorStateUI()
    }

    /**
     * This sets the constraints of a list of views to order them from left to right.
     *
     * A null may be used once to create a break between left-aligned and right-aligned items.
     * Starting the list with a null will make all the items right-aligned. No null will make
     * all items left aligned. More than 1 null is not supported.
     *
     * @param views The list of views (with optional null) to order horizontally
     */
    private fun setHorizontalConstraints(views: List<View?>, reverse: Boolean = false) {
        var afterBreak = false
        val viewsList = if (reverse) {views.reversed()} else {views}
        for (i in viewsList.indices) {
            val view = viewsList[i]
            if (view == null) {
                afterBreak = true
                continue
            }
            // First clear existing start and end constraints
            view.layoutParams = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
                startToEnd = ConstraintLayout.LayoutParams.UNSET
                startToStart = ConstraintLayout.LayoutParams.UNSET
                endToStart = ConstraintLayout.LayoutParams.UNSET
                endToEnd = ConstraintLayout.LayoutParams.UNSET
            }
            if (i == 0) {
                // Set the start constraint of the first view to the parent layout
                view.layoutParams = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                }
            } else if (i == viewsList.size - 1 && afterBreak) {
                // Set the end constraint of the last view to the parent layout
                view.layoutParams = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                }
            } else if (afterBreak) {
                // Set the end constraint of the current view to the start constraint of the next view
                view.layoutParams = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
                    endToStart = viewsList[i + 1]!!.id
                }
            } else {
                // Set the start constraint of the current view to the end constraint of the previous view
                view.layoutParams = (view.layoutParams as ConstraintLayout.LayoutParams).apply {
                    startToEnd = viewsList[i - 1]!!.id
                }
            }
        }
    }

    /**
     * Call this changing split screen, it shows/hides each of `nonSplitScreenTelltaleUIViews` by
     * changing the View's alpha, so it doesn't conflict with visibility changes from signal logic.
     */
    private fun updateSplitScreenTellTales() {
        // To keep from conflicting with the different visibilities of TellTales, especially as some
        // use INVISIBLE and some use GONE, for split screen we make them transparent instead of
        // changing the visibility.
        if (isSplitScreen()) {
            nonSplitScreenTelltaleUIViews().forEach { it.alpha = 0f }
        } else {
            nonSplitScreenTelltaleUIViews().forEach { it.alpha = 1f }
        }
    }

    /**
     * Sets a view visible if signal matches value, otherwise sets it invisible or gone
     * 
     * @param tt Specify a Triple with first: the view to change, second: the signal to use, and
     * third: the value which makes the view visible
     * @param remove If true, instead of making the view INVISIBLE, it will set it to GONE
     */
    private fun processBasicTellTale(tt: Triple<View, String, Float>, remove: Boolean = false) {
        if (viewModel.carState[tt.second] == tt.third) {
            tt.first.visibility = View.VISIBLE
        } else if (remove) {
            tt.first.visibility = View.GONE
        } else {
            tt.first.visibility = View.INVISIBLE
        }
    }

    /**
     * Provides the current gearSelected value with a default of gearInvalid
     */
    private fun gearState(): Float {
        return viewModel.carState[SName.gearSelected] ?: SVal.gearInvalid
    }
    
    private fun updateGearView() {
        val apState = viewModel.carState[SName.autopilotState] ?: 0f
        val gearColorSelected = when {
            apState in 3f..7f -> requireContext().getColor(R.color.autopilot_blue)
            shouldUseDarkMode() -> Color.LTGRAY
            else -> Color.DKGRAY
        }
        val gearLetterIndex = when (gearState()) {
            SVal.gearPark -> 0
            SVal.gearReverse -> 3
            SVal.gearNeutral -> 6
            SVal.gearDrive -> 9
            else -> null
        }

        if (gearState() == SVal.gearInvalid) {
            binding.PRND.visibility = View.INVISIBLE
        } else {
            val ss = SpannableString(binding.PRND.text.toString())
            if (gearLetterIndex != null) {
                ss.setSpan(
                    ForegroundColorSpan(gearColorSelected),
                    gearLetterIndex,
                    gearLetterIndex + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.PRND.text = (ss)
            binding.PRND.visibility = View.VISIBLE
        }
    }
    
    private fun shouldUseDarkMode(): Boolean {
        return (viewModel.carState[SName.isSunUp] == 0f || prefs.getBooleanPref(Constants.forceNightMode))
    }

    private fun setColors() {
        val window: Window? = activity?.window
        val circleGauges = setOf(
            binding.fronttorquegauge,
            binding.reartorquegauge,
            binding.batttempgauge,
            binding.fronttempgauge,
            binding.reartempgauge,
            binding.frontbraketempgauge,
            binding.rearbraketempgauge,
            binding.coolantflowgauge,
            binding.chargemeter
        )
        val textViewsPrimary = setOf(
            binding.speed,
            binding.bigsoc,
            binding.bigsocpercent,
            binding.power,
            binding.efficiency,

            binding.minpower,
            binding.maxpower,
            binding.fronttorque,
            binding.fronttorquelabel,
            binding.fronttorqueunits,

            binding.reartorque,
            binding.reartorquelabel,
            binding.reartorqueunits,
            binding.batttemp,
            binding.batttemplabel,
            binding.batttempunits,
            binding.fronttemp,
            binding.fronttemplabel,
            binding.fronttempunits,
            binding.frontbraketemp,
            binding.frontbraketemplabel,
            binding.frontbraketempunits,
            binding.rearbraketemp,
            binding.rearbraketemplabel,
            binding.rearbraketempunits,

            binding.reartemp,
            binding.reartemplabel,
            binding.reartempunits,
            binding.coolantflow,
            binding.coolantflowlabel,
            binding.coolantflowunits,
        )
        val textViewsSecondary = setOf(
            binding.chargerate,
            binding.unit,
            binding.batterypercent
        )
        val textViewsDisabled = setOf(
            binding.odometer,
            binding.PRND
        )
        val imageViewsSecondary = setOf(
            binding.modely,
            binding.modelyCenter
        )
        val imageViewsDisabled = setOf(
            binding.battery
        )

        // Not using dark-mode for compatibility with older version of Android (pre-29)
        if (shouldUseDarkMode()) {
            window?.statusBarColor = Color.BLACK
            binding.root.setBackgroundColor(Color.BLACK)
            textViewsPrimary.forEach { it.setTextColor(Color.WHITE) }
            textViewsSecondary.forEach { it.setTextColor(Color.LTGRAY) }
            textViewsDisabled.forEach { it.setTextColor(Color.DKGRAY) }
            imageViewsSecondary.forEach { it.setColorFilter(Color.LTGRAY) }
            imageViewsDisabled.forEach { it.setColorFilter(Color.DKGRAY) }
            circleGauges.forEach { it.setDayValue(0) }
            binding.powerBar.setDayValue(0)
            binding.batteryOverlay.setDayValue(0)
        } else {
            window?.statusBarColor = Color.parseColor("#FFEEEEEE")
            binding.root.setBackgroundColor(requireContext().getColor(R.color.day_background))
            textViewsPrimary.forEach { it.setTextColor(Color.BLACK) }
            textViewsSecondary.forEach { it.setTextColor(Color.DKGRAY) }
            textViewsDisabled.forEach { it.setTextColor(Color.LTGRAY) }
            imageViewsSecondary.forEach { it.setColorFilter(Color.DKGRAY) }
            imageViewsDisabled.forEach { it.setColorFilter(Color.LTGRAY) }
            circleGauges.forEach { it.setDayValue(1) }
            binding.powerBar.setDayValue(1)
            binding.batteryOverlay.setDayValue(1)
        }
        updateGearView()
    }

    private fun formatPower(power: Float): String {
        return power.convertAndRoundToString(Units.POWER_W) + unitConverter.prefPowerUnit().tag
    }

    private fun updateDoorStateUI() {
        val carBody = if (isSplitScreen()) binding.modelyCenter else binding.modely
        carBody.visibility = if (anyDoorOpen()) View.VISIBLE else View.GONE
        displayOpenDoors()
        setGaugeVisibility()
    }

    private fun anyDoorOpen(): Boolean {
        return (SGroup.closures.any { closureIsOpen(it) })
    }

    private fun closureIsOpen(signalName: String): Boolean {
        return (viewModel.carState[signalName] in setOf(1f, 4f, 5f))
    }

    private fun displayOpenDoors() {
        val sigToBinding = if (!isSplitScreen()) mapOf(
            SName.liftgateState to binding.hatch,
            SName.frunkState to binding.hood,
            SName.frontLeftDoorState to binding.frontleftdoor,
            SName.frontRightDoorState to binding.frontrightdoor,
            SName.rearLeftDoorState to binding.rearleftdoor,
            SName.rearRightDoorState to binding.rearrightdoor
        ) else mapOf(
            SName.liftgateState to binding.hatchCenter,
            SName.frunkState to binding.hoodCenter,
            SName.frontLeftDoorState to binding.frontleftdoorCenter,
            SName.frontRightDoorState to binding.frontrightdoorCenter,
            SName.rearLeftDoorState to binding.rearleftdoorCenter,
            SName.rearRightDoorState to binding.rearrightdoorCenter
        )
        sigToBinding.forEach {
            if (closureIsOpen(it.key)) {
                it.value.visibility = View.VISIBLE
            } else {
                it.value.visibility = View.GONE
            }
        }
    }

    private fun updateAutopilotUI() {
        val brakeApplied = (viewModel.carState[SName.brakeApplied] == 2f)
        val inDrive = (viewModel.carState[SName.gearSelected] == SVal.gearDrive)
        val autopilotState = if (brakeApplied || !inDrive) 0f else viewModel.carState[SName.autopilotState] ?: 0f

        when (autopilotState) {
            in 3f..7f -> binding.autopilot.setImageResource(R.drawable.ic_autopilot)
            else -> binding.autopilot.setImageResource(R.drawable.ic_autopilot_inactive)
        }

        val visible = (binding.autopilot.visibility == View.VISIBLE)
        when {
            autopilotState in 2f..7f && !visible -> {
                binding.autopilot.startAnimation(fadeIn(200))
                binding.autopilot.visibility = View.VISIBLE
            }
            autopilotState !in 2f..7f && visible -> {
                binding.autopilot.startAnimation(fadeOut(200))
                binding.autopilot.visibility = View.INVISIBLE
            }
        }
    }

    private fun updateAutopilotRotation() {
        // set pivot to center of image
        binding.autopilot.pivotX = (binding.autopilot.width / 2f)
        binding.autopilot.pivotY = (binding.autopilot.height / 2f)
        val steeringAngle = if ((viewModel.carState[SName.autopilotState] ?: 0f) in 3f..7f)
            (viewModel.carState[SName.steeringAngle] ?: 0f) else 0f
        binding.autopilot.rotation = steeringAngle
    }

    private fun updateTaccUI(){
        // accSpeedLimit is 204.6f (SNA) while TACC is active
        val taccActive = (viewModel.carState[SName.accSpeedLimit] == 204.6f)
        if (taccActive) {
            binding.TACC.setTextColor(requireContext().getColor(R.color.white))
            binding.TACC.setBackgroundResource(R.drawable.rounded_corner)
        } else {
            binding.TACC.setTextColor(requireContext().getColor(R.color.autopilot_inactive))
            binding.TACC.setBackgroundResource(0)
        }

        val brakeApplied = (viewModel.carState[SName.brakeApplied] == 2f)
        val inDrive = (viewModel.carState[SName.gearSelected] == SVal.gearDrive)

        val taccAvailable = (viewModel.carState[SName.accState] == 4f && !brakeApplied && inDrive)
        val visible = (binding.TACC.visibility == View.VISIBLE)
        if (visible != taccAvailable) {
            when (taccAvailable) {
                true -> {
                    binding.TACC.startAnimation(fadeIn(200))
                    binding.TACC.visibility = View.VISIBLE
                }
                false -> {
                    binding.TACC.startAnimation(fadeOut(200))
                    binding.TACC.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun updateSpeedLimitSign() {
        val speedLimitVal = viewModel.carState[SName.fusedSpeedLimit] ?: SVal.fusedSpeedNone
        if (speedLimitVal == SVal.fusedSpeedNone || gearState() != SVal.gearDrive
            || prefs.getBooleanPref(Constants.hideSpeedLimit) || isSplitScreen()
        ) {
            binding.speedLimitUs.visibility = View.INVISIBLE
            binding.speedLimitRound.visibility = View.INVISIBLE
            binding.speedLimitNolimitRound.visibility = View.INVISIBLE
        } else {
            if ((viewModel.carState[SName.mapRegion] ?: SVal.mapUS) == SVal.mapUS) {
                // There's no CA map region from the dbc, assuming that CA uses US map region and sign
                binding.speedLimitValueUs.text = speedLimitVal.roundToString(0)
                binding.speedLimitUs.visibility = View.VISIBLE
                binding.speedLimitRound.visibility = View.INVISIBLE
            } else {
                // Apologies if I wrongly assumed the rest of the world uses the round sign
                if (speedLimitVal != 155f) {
                    binding.speedLimitNolimitRound.visibility = View.INVISIBLE
                    binding.speedLimitValueRound.text = speedLimitVal.roundToString(0)
                    binding.speedLimitRound.visibility = View.VISIBLE
                    binding.speedLimitUs.visibility = View.INVISIBLE
                } else {
                    binding.speedLimitNolimitRound.visibility = View.VISIBLE
                    binding.speedLimitUs.visibility = View.INVISIBLE
                    binding.speedLimitRound.visibility = View.INVISIBLE
                }
            }
        }
    }

    private fun setupGradientOverlays() {
        // init gradient
        gradientColorFrom = requireContext().getColor(R.color.transparent_blank)
        overlayGradient = GradientDrawable().apply {
            colors = intArrayOf(
                gradientColorFrom,
                gradientColorFrom,
            )
            orientation = Orientation.TOP_BOTTOM
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        binding.warningGradientOverlay.setImageDrawable(overlayGradient)
        binding.warningGradientOverlay.visibility = View.GONE

        // init ap animation
        autopilotAnimation = ValueAnimator.ofObject(
            ArgbEvaluator(),
            gradientColorFrom,
            requireContext().getColor(R.color.autopilot_blue)
        )
        // autopilotAnimation is repeated in .doOnEnd
        // set repeatCount to 1 so that it reverses before ending
        autopilotAnimation.repeatCount = 1
        autopilotAnimation.repeatMode = ValueAnimator.REVERSE

        // init bs animation
        blindspotAnimation = ValueAnimator.ofObject(
            ArgbEvaluator(),
            gradientColorFrom,
            requireContext().getColor(R.color.very_red)
        )
        blindspotAnimation.addUpdateListener { animator ->
            overlayGradient.colors =
                intArrayOf(animator.animatedValue as Int, gradientColorFrom)
        }
        blindspotAnimation.doOnEnd {
            binding.warningGradientOverlay.visibility = View.GONE
            overlayGradient.colors = intArrayOf(gradientColorFrom, gradientColorFrom)
        }
    }

    private fun updateAPWarning(autopilotHandsVal: Float) {
        if ((autopilotHandsVal > 2f) and (autopilotHandsVal < 15f)) {
            // 3 and 4 have a ~2 second delay before starting to flash
            autopilotAnimation.startDelay = if (autopilotHandsVal in 3f..4f) 1900L else 0L

            if (binding.APWarning.visibility != View.VISIBLE) {
                // Warning toast:
                binding.APWarning.visibility = View.VISIBLE

                // Gradient overlay:
                overlayGradient.orientation = Orientation.TOP_BOTTOM
                autopilotAnimation.addUpdateListener { animator ->
                    overlayGradient.colors =
                        intArrayOf(animator.animatedValue as Int, gradientColorFrom)
                }
                autopilotAnimation.doOnEnd { anim ->
                    // Duration is from low to high, a full cycle is duration * 2
                    anim.duration = max(250L, (anim.duration * 0.9).toLong())
                    anim.startDelay = 0L
                    anim.start()
                }
                autopilotAnimation.duration = 750L
                autopilotAnimation.start()
                binding.warningGradientOverlay.visibility = View.VISIBLE
            }
        } else {
            // Warning toast:
            binding.APWarning.visibility = View.GONE

            // Gradient overlay:
            binding.warningGradientOverlay.visibility = View.GONE
            autopilotAnimation.removeAllListeners()
            autopilotAnimation.cancel()
            overlayGradient.colors = intArrayOf(gradientColorFrom, gradientColorFrom)
        }
    }

    private fun updateBSWarning(bsValue: Float?, bsBinding: View, orientation: Orientation) {
        when (bsValue) {
            1f -> blindspotAnimation.duration = 300
            2f -> blindspotAnimation.duration = 150
        }
        if (bsValue in setOf(1f, 2f)) {
            // Warning toast:
            bsBinding.clearAnimation()
            bsBinding.visibility = View.VISIBLE

            // Gradient overlay:
            overlayGradient.orientation = orientation
            blindspotAnimation.repeatCount = ValueAnimator.INFINITE
            blindspotAnimation.repeatMode = ValueAnimator.REVERSE
            blindspotAnimation.start()
            binding.warningGradientOverlay.visibility = View.VISIBLE
        } else {
            // Warning toast:
            if (bsBinding.visibility != View.GONE) {
                bsBinding.startAnimation(fadeOut())
                bsBinding.visibility = View.GONE
            }
            // Gradient overlay:
            // let it fade out naturally by setting repeat to 1 (so it reverses) then change visibility on end
            blindspotAnimation.repeatCount = 1
        }
    }

    private fun updateFCWWarning(fcwVal: Float?) {
        if (fcwVal == 1f) {
            overlayGradient.orientation = Orientation.TOP_BOTTOM
            blindspotAnimation.duration = 125

            // Warning toast:
            binding.FCWarning.clearAnimation()
            binding.FCWarning.visibility = View.VISIBLE

            // Gradient overlay:
            // Reuse blindspot animation as it's basically the same
            blindspotAnimation.repeatCount = 4
            blindspotAnimation.repeatMode = ValueAnimator.RESTART
            blindspotAnimation.reverse()
            binding.warningGradientOverlay.visibility = View.VISIBLE
        } else {
            // Warning toast:
            if (binding.FCWarning.visibility != View.GONE) {
                binding.FCWarning.startAnimation(fadeOut())
                binding.FCWarning.visibility = View.GONE
            }
            // Gradient overlay stops by itself after a fixed repeat count
        }
    }

    private fun fadeIn(duration: Long = 500L): Animation {
        val fadeIn = AnimationUtils.loadAnimation(activity, R.anim.fade_in)
        fadeIn.duration = duration
        return fadeIn
    }

    private fun fadeOut(duration: Long = 500L): Animation {
        val fadeOut = AnimationUtils.loadAnimation(activity, R.anim.fade_out)
        fadeOut.duration = duration
        return fadeOut
    }

    // If the discoveryService finds a different ip address, save the new
    // address and restart
    private fun setupZeroConfListener() {
        viewModel.zeroConfIpAddress.observe(viewLifecycleOwner) { ipAddress ->
            if (viewModel.serverIpAddress() != ipAddress && !ipAddress.equals("0.0.0.0")) {
                viewModel.saveSettings(ipAddress)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupZeroConfListener()
        binding.root.postDelayed({
            viewModel.startDiscoveryService()
        }, 2000)
    }
    override fun onDestroy() {
        viewModel.stopDiscoveryService()
        super.onDestroy()
    }

    override fun onPause() {
        viewModel.stopDiscoveryService()
        super.onPause()
    }
}