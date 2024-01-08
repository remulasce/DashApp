package app.candash.cluster.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import app.candash.cluster.CarState
import app.candash.cluster.DashViewModel
import app.candash.cluster.EfficiencyCalculator
import app.candash.cluster.LiveCarState
import app.candash.cluster.SName
import app.candash.cluster.SVal
import app.candash.cluster.SignalName
import app.candash.cluster.SignalState
import app.candash.cluster.compose.ComposeScope.Companion.createComposableCarStateFromLiveData
import app.candash.cluster.compose.ComposeScope.Companion.createComposableCarStateFromMap
import app.candash.cluster.compose.ComposeScope.Companion.toState
import app.candash.cluster.compose.ui.theme.CANDashTheme
import app.candash.cluster.compose.ui.theme.TitleLabelTextStyle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.time.TimeSource

@AndroidEntryPoint
class ModularDashActivity : ComponentActivity() {

    private lateinit var viewModel: DashViewModel
    private lateinit var efficiency: EfficiencyCalculator
    private lateinit var prefs: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("modular", Context.MODE_PRIVATE)
        viewModel = ViewModelProvider(this)[DashViewModel::class.java]
        viewModel.startUp()
        efficiency = EfficiencyCalculator(viewModel.carState, prefs)

        data class SnackbarLauncher(
            val snackbarHostState: SnackbarHostState,
            val scope: CoroutineScope
        )

        setContent {
            val scope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(LocalSnackbarHost provides snackbarHostState) {
                CANDashTheme {
                    // A surface container using the 'background' color from the theme
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState)
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            ComposeScope(
                                viewModel.liveCarState.createComposableCarStateFromLiveData(),
                                efficiency.toState()
                            ).MainLayout3Cols()
                        }
                    }
                }
            }
        }
    }
}

typealias ComposableCarState = Map<String, State<SignalState?>>

fun ComposableCarState.currentState(signalName: SignalName): Float? =
    this[signalName]?.value?.value


typealias ComposableEfficiency = MutableList<HistoricalEfficiency>


class ComposeScope(val carState: ComposableCarState, val efficiency: ComposableEfficiency) {

    companion object {
        @Composable
        fun LiveCarState.createComposableCarStateFromLiveData(): ComposableCarState {
            return this.mapValues {
                it.value.observeAsState()
            }
        }

        fun CarState.createComposableCarStateFromMap(): ComposableCarState {
            return this.mapValues {
                mutableStateOf(SignalState(it.value ?: 0f, 0))
            }
        }

        fun CarState.createTestComposableCarStateFromMap(): Map<String, MutableState<SignalState?>> {
            return this.mapValues {
                mutableStateOf(SignalState(it.value ?: 0f, 0))
            }
        }

        @Composable
        fun EfficiencyCalculator.toState(): ComposableEfficiency {
            val efficiency = mutableStateListOf<HistoricalEfficiency>()
            LaunchedEffect(this) {
                while (true) {
                    Log.v("EffToState", "Recomposing efficiency occasionally")
                    this@toState.updateKwhHistory()
                    val newEfficiency = mutableListOf<HistoricalEfficiency>()
                    listOf(1f, 5f, 10f).forEach {
                        newEfficiency.add(
                            HistoricalEfficiency(
                                this@toState.getEfficiencyText(it),
                                "$it mi",
                                null
                            )
                        )
                    }

                    efficiency.clear()
                    efficiency.addAll(newEfficiency)
                    delay(100)
                }
            }
            return efficiency
        }
    }

    private fun currentState(signalName: SignalName) = carState[signalName]?.value?.value

    @Composable
    fun MainLayout3Cols() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.weight(.25f),
                contentAlignment = Alignment.TopCenter
            ) {
                LiveValues()
            }
            Box(
                modifier = Modifier
                    .weight(.25f)
                    .fillMaxWidth(.9f),
                contentAlignment = Alignment.TopCenter
            ) {
                EfficiencyTable(
                    efficiencies = efficiency,
                    "Recent Efficiency"
                )
            }
            Box(modifier = Modifier.weight(.5f), contentAlignment = Alignment.TopCenter) {
                Logging()
            }
        }
    }

    @Composable
    private fun LiveValues() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Live Values", style = TitleLabelTextStyle())
            Speed()
            LiveEfficiency()
        }
    }


    @Composable
    private fun LiveEfficiency() {
        val power = currentState(SName.power)
        val speed = currentState(SName.uiSpeed)
        if (power != null && speed != null) {
            val value = power / speed
            Text("${value.roundToInt()} wh/mi", fontSize = 32.sp)
        }
    }

    @Composable
    private fun Speed() {
        val uiSpeed = "%.0f".format(currentState(SName.uiSpeed))
        val speedUnits = "mph"

        val speed = "$uiSpeed $speedUnits"
        Text(text = speed, fontSize = 48.sp)
    }


    @Composable
    private fun Logging() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Logging", style = TitleLabelTextStyle())
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    EfficiencyLogging(carState, TimeSource.Monotonic)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    CANLogging()
                }
            }
        }

    }


    @Composable
    private fun CANLogging() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CAN Logging", style = TitleLabelTextStyle())
            Button(onClick = { /*TODO*/ }) {
                Text("Start / Stop")
            }
        }
    }

    @Composable
    private fun PreviousRuns() {

    }
}


@Preview(showBackground = true, device = Devices.PIXEL_3, showSystemUi = true)
@Composable
fun DashPreview() {
    CANDashTheme {
        ComposeScope(
            mutableMapOf<String, Float?>(
                SName.autopilotState to 3f,
                SName.accState to 4f,
                SName.accActive to 1f,
                SName.turnSignalLeft to 1.0f,
                SName.isSunUp to 1f,
                SName.autopilotHands to 1f,
                SName.driveConfig to 0f,
                SName.gearSelected to SVal.gearDrive,
                SName.stateOfCharge to 70f,
                SName.battAmps to -23f,
                SName.uiSpeedUnits to 0f,

                SName.battVolts to 390f,
                // display should stay on because gear is in drive
                SName.displayOn to 0f,

                SName.frontLeftDoorState to 2f,
                SName.lightingState to SVal.lightDRL,
                SName.passengerUnbuckled to 1f,
                SName.limRegen to 1f,
                SName.brakePark to 1f,
                SName.chargeStatus to SVal.chargeStatusInactive,
                SName.mapRegion to SVal.mapEU,
                SName.fusedSpeedLimit to 100f,

                SName.uiSpeed to 80f,
                SName.power to 50_000f,
            ).createComposableCarStateFromMap(), mutableListOf()
        ).MainLayout3Cols()
    }
}