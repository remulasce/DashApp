package app.candash.cluster.compose

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@AndroidEntryPoint
class ModularDashActivity : ComponentActivity() {

    private lateinit var viewModel: DashViewModel
    private lateinit var efficiency: EfficiencyCalculator
    private val prefs = getSharedPreferences("modular", Context.MODE_PRIVATE)


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DashViewModel::class.java]
        viewModel.startUp()
        efficiency = EfficiencyCalculator(viewModel.carState, prefs)

        setContent {
            CANDashTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComposeScope(
                        viewModel.liveCarState.createComposableCarStateFromLiveData(),
                        efficiency.toState()
                    ).MainLayout3Cols()
                }
            }
        }
    }
}

typealias ComposableCarState = Map<String, State<SignalState?>>


typealias ComposableEfficiency = MutableList<ComposeScope.HistoricalEfficiency>

class ComposeScope(val carState: ComposableCarState, efficiency: ComposableEfficiency) {

    companion object {
        @Composable
        fun LiveCarState.createComposableCarStateFromLiveData(): ComposableCarState {
            return this.mapValues {
                it.value.observeAsState()
            }
        }

        @Composable
        fun CarState.createComposableCarStateFromMap(): ComposableCarState {
            return this.mapValues {
                mutableStateOf(SignalState(it.value ?: 0f, 0))
            }
        }

        @Composable
        fun EfficiencyCalculator.toState(): ComposableEfficiency {
            val efficiency = mutableStateListOf<HistoricalEfficiency>()
            LaunchedEffect(this) {
                while (true) {
                    this@toState.updateKwhHistory()
                    val newEfficiency = mutableListOf<HistoricalEfficiency>()
                    listOf(1f, 5f, 10f).forEach {
                        newEfficiency.add(
                            HistoricalEfficiency(
                                this@toState.getEfficiencyText(it) ?: "-",
                                "$it mi",
                                "- mph"
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

    @Composable
    private fun currentState(signalName: SignalName) = carState[signalName]?.value?.value

    @Composable
    fun MainLayout3Cols() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                LiveValues()
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                EfficiencyTable(
                    listOf(
                        HistoricalEfficiency("330 wh/mi", "1 mi", "72 mph"),
                        HistoricalEfficiency("368 wh/mi", "3 mi", "76 mph")
                    ),
                    "Recent Efficiency"
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                Logging()
            }
        }
    }

    @Composable
    private fun LiveValues() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Live Values", style = titleLabelTextStyle())
            Speed()
            LiveEfficiency()
        }
    }

    @Composable
    private fun titleLabelTextStyle() = MaterialTheme.typography.labelSmall

    data class HistoricalEfficiency(
        val efficiency: String,
        val mileage: String,
        val avgSpeed: String
    )

    @Composable
    private fun EfficiencyTable(efficiencies: List<HistoricalEfficiency>, tableTitle: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tableTitle, style = titleLabelTextStyle())
            Box(
                Modifier.border(2.dp, MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    efficiencies.forEach {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Text(it.efficiency)
                            Column {
                                Text(it.avgSpeed)
                                Text(it.mileage)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LiveEfficiency() {
        val value = 486.0
        Text("${value.roundToInt()} wh/mi", fontSize = 32.sp)
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
            Text("Logging", style = titleLabelTextStyle())
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    EfficiencyLogging()
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    CANLogging()
                }
            }
        }

    }

    @Composable
    private fun EfficiencyLogging() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Efficiency Logging", style = titleLabelTextStyle())
            Button(onClick = { /*TODO*/ }) {
                Text("Start / Stop")
            }
            Spacer(modifier = Modifier.size(10.dp))
            EfficiencyTable(
                tableTitle = "Recent Logs",
                efficiencies = listOf(HistoricalEfficiency("368 wh/mi", "1 mi", "78 mph")),
            )
        }
    }

    @Composable
    private fun CANLogging() {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CAN Logging", style = titleLabelTextStyle())
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
            ).createComposableCarStateFromMap(), listOf()
        ).MainLayout3Cols()
    }
}