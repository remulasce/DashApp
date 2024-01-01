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
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.candash.cluster.compose.ui.theme.CANDashTheme

class ModularDashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CANDashTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainLayout3Cols()
                }
            }
        }
    }
}

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
                )
            )
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
            Logging()
        }
    }
}

@Composable
fun UnusedCol() {
    Box {
        Text(text = "Unused", style = titleLabelTextStyle())
    }
}

@Composable
private fun LiveValues() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Live Values", style = titleLabelTextStyle())
        Speed("66 mph")
        LiveEfficiency("500 wh/mi")

    }

}

@Composable
private fun titleLabelTextStyle() = MaterialTheme.typography.labelSmall

data class HistoricalEfficiency(val efficiency: String, val mileage: String, val avgSpeed: String)

@Composable
private fun EfficiencyTable(efficiencies: List<HistoricalEfficiency>) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Historical Efficiencies", style = titleLabelTextStyle())
        Box(Modifier.border(2.dp, MaterialTheme.colorScheme.secondaryContainer)) {
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                efficiencies.forEach {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
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
private fun Speed(speed: String) {
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
    Column(horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Efficiency Logging", style = titleLabelTextStyle())
            Button(onClick = { /*TODO*/ }) {
                Text("Start / Stop")
            }
        Spacer(modifier = Modifier.size(10.dp))
        Text("Recent Logs", style = titleLabelTextStyle())
        EfficiencyTable(efficiencies = listOf(HistoricalEfficiency("368 wh/mi", "1 mi", "78 mph")))

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

@Preview(showBackground = true, device = Devices.PIXEL_3, showSystemUi = true)
@Composable
fun DashPreview() {
    CANDashTheme {
        MainLayout3Cols()
    }
}