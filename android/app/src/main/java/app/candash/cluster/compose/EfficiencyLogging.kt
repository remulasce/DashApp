package app.candash.cluster.compose

import android.util.Log
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.candash.cluster.R
import app.candash.cluster.SName
import app.candash.cluster.compose.ui.theme.TitleLabelTextStyle
import app.candash.cluster.kmToMi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.convert
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Composable
fun EfficiencyLogging(
    state: ComposableCarState,
    time: TimeSource
) {
    val recentLogs: MutableState<List<HistoricalEfficiency>> = rememberSaveable {
        mutableStateOf(
            listOf()
        )
    }

    // odometer, discharge total
    val startPoint: MutableState<EfficiencyLog?> = remember { mutableStateOf(null) }

    val rememberCoroutineScope = rememberCoroutineScope()
    val snackbarHost = LocalSnackbarHost.current

    DisplayEfficiency(
        isRunning = startPoint.value != null,
        onClick = {
            onStartStopClick(
                startPoint, state, recentLogs, time,
                snackbarHost, rememberCoroutineScope
            )
        },
        recentLogs = recentLogs.value
    )
}

@OptIn(ExperimentalTime::class)
private fun onStartStopClick(
    startPoint: MutableState<EfficiencyLog?>,
    carState: ComposableCarState,
    recentLogs: MutableState<List<HistoricalEfficiency>>,
    time: TimeSource,
    snackbar: SnackbarHostState?,
    snackbarScope: CoroutineScope?
) {
    startPoint.value?.let { it ->
        startPoint.value = null
        val odoMi = carState.currentState(signalName = SName.odometerKm)?.kmToMi
        val disch = carState.currentState(signalName = SName.kwhDischargeTotal)
        if (odoMi != null && disch != null) {
            val dOdo = odoMi - it.odometer
            val dDischKwh = disch - it.dischargeKwh
            val dTime: Duration? = it.timestamp?.elapsedNow()
            val avgSpeed: Float? =
                dTime?.let {
                    convert(
                        (dOdo / it.inWholeMilliseconds).toDouble(),
                        DurationUnit.HOURS, // Reverse conversion, since 'hour' is on divident
                        DurationUnit.MILLISECONDS
                    ).toFloat()
                }
            val efficiencyWh = dDischKwh / dOdo * 1000
            val newLog = HistoricalEfficiency(
                "%.0f wh/mi".format(efficiencyWh),
                "%.0f mi".format(dOdo),
                avgSpeed?.let {
                    "%.0f mph".format(it)
                }
            )
            Log.i("EfficiencyLogger", "New efficiency reading: $newLog")
            recentLogs.value = listOf(newLog) + recentLogs.value
            snackbarScope?.launch {
                snackbar?.showSnackbar(newLog.efficiency)
            }
        }
    } ?: run {
        val odo = carState.currentState(signalName = SName.odometerKm)
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

            if (isRunning) {
                val pulse = rememberInfiniteTransition("Pulse effect")
                val scale by pulse.animateFloat(
                    initialValue = .95f,
                    targetValue = 1.01f,
                    animationSpec = infiniteRepeatable(
                        tween(1_000, easing = EaseInOutSine),
                        RepeatMode.Reverse
                    ), label = "Pulse Animation"
                )

                Text("Stop")
                Icon(
                    painter = painterResource(id = R.drawable.stop),
                    modifier = Modifier.scale(scale),
                    contentDescription = null // already has text
                )
            } else {
                Text("Start")
                Icon(
                    painter = painterResource(id = R.drawable.play),
                    contentDescription = null // already has text
                )
            }

        }
        Spacer(modifier = Modifier.size(10.dp))
        EfficiencyTable(
            tableTitle = "Recent Logs",
            efficiencies = recentLogs
        )
    }
}

data class EfficiencyLog(val odometer: Float, val dischargeKwh: Float, val timestamp: TimeMark?)