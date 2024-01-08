package app.candash.cluster.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.candash.cluster.SName
import app.candash.cluster.compose.ui.theme.TitleLabelTextStyle
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Composable
fun EfficiencyLogging(
    state: ComposableCarState,
    time: TimeSource
) {
    val recentLogs: MutableState<List<HistoricalEfficiency>> = remember {
        mutableStateOf<List<HistoricalEfficiency>>(
            listOf()
        )
    }

    // odometer, discharge total
    val startPoint: MutableState<EfficiencyLog?> = remember { mutableStateOf(null) }

    DisplayEfficiency(
        isRunning = startPoint.value != null,
        onClick = { onStartStopClick(startPoint, state, recentLogs, time) },
        recentLogs = recentLogs.value
    )
}

private fun onStartStopClick(
    startPoint: MutableState<EfficiencyLog?>,
    carState: ComposableCarState,
    recentLogs: MutableState<List<HistoricalEfficiency>>,
    time: TimeSource
) {
    startPoint.value?.let { it ->
        startPoint.value = null
        val odo = carState.currentState(signalName = SName.odometer)
        val disch = carState.currentState(signalName = SName.kwhDischargeTotal)
        if (odo != null && disch != null) {
            val dOdo = odo - it.odometer
            val dDischKwh = disch - it.dischargeKwh
            val dTime: Duration? = it.timestamp?.elapsedNow()
            val avgSpeed: Float? =
                dTime?.let {
                    dOdo / it.inWholeMilliseconds
                }
            val efficiencyWh = dDischKwh / dOdo * 1000
            recentLogs.value += HistoricalEfficiency(
                "%.0f wh/mi".format(efficiencyWh),
                "%.0f mi".format(dOdo),
                avgSpeed?.let {
                    "%.0f mph".format(it)
                }
            )
        }
    } ?: run {
        val odo = carState.currentState(signalName = SName.odometer)
        val disch = carState.currentState(signalName = SName.kwhDischargeTotal)
        if (odo != null && disch != null) {
            startPoint.value = EfficiencyLog(
                odo,
                disch,
                time.markNow()
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun PreviewEmpty() {
    DisplayEfficiency(isRunning = false, onClick = { }, recentLogs = listOf())
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun PreviewRunning() {
    DisplayEfficiency(isRunning = true, onClick = { }, recentLogs = listOf())
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun PreviewLogs() {
    DisplayEfficiency(
        isRunning = false, onClick = { },
        recentLogs = listOf(
            HistoricalEfficiency("xyz wh/mi", "3.3 mi", "74.8 mph")
        )
    )
}

@Composable
private fun DisplayEfficiency(
    isRunning: Boolean,
    onClick: () -> Unit,
    recentLogs: List<HistoricalEfficiency>
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Efficiency Logging", style = TitleLabelTextStyle())
        Button(onClick = onClick) {
            Text(if (isRunning) "Stop" else "Start")
        }
        Spacer(modifier = Modifier.size(10.dp))
        EfficiencyTable(
            tableTitle = "Recent Logs",
            efficiencies = recentLogs
        )
    }
}

data class EfficiencyLog(val odometer: Float, val dischargeKwh: Float, val timestamp: TimeMark?)