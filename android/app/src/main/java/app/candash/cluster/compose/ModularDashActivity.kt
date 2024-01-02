package app.candash.cluster.compose

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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import app.candash.cluster.DashViewModel
import app.candash.cluster.LiveCarState
import app.candash.cluster.SName
import app.candash.cluster.SVal
import app.candash.cluster.SignalState
import app.candash.cluster.compose.ui.theme.CANDashTheme
import app.candash.cluster.createCarState
import app.candash.cluster.createLiveCarState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ModularDashActivity : ComponentActivity() {

    private lateinit var viewModel: DashViewModel


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[DashViewModel::class.java]
        viewModel.startUp()


        setContent {
            CANDashTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComposeScope(viewModel.liveCarState).MainLayout3Cols()
                }
            }
        }
    }
}

class ComposeScope(val liveCarState: LiveCarState) {
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
            LiveEfficiency("500 wh/mi")

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
    private fun LiveEfficiency(efficiency: String) {
        Text(efficiency, fontSize = 32.sp)
    }

    @Composable
    private fun Speed() {
        val speed =
            "${liveCarState[SName.uiSpeed]?.observeAsState()?.value?.value} " +
                    "${liveCarState[SName.uiSpeedUnits]?.observeAsState()?.value?.value}"
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
            createLiveCarState()
        ).MainLayout3Cols()
    }
}