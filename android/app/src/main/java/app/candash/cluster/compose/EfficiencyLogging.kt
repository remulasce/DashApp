package app.candash.cluster.compose

import android.content.Context
import android.os.Parcelable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.candash.cluster.R
import app.candash.cluster.SName
import app.candash.cluster.compose.ui.theme.TitleLabelTextStyle
import app.candash.cluster.kmToMi
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.Calendar
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
    val context = LocalContext.current


    DisplayEfficiency(
        isRunning = startPoint.value != null,
        onClick = {
            onStartStopClick(
                startPoint, state, recentLogs, time,
                snackbarHost, rememberCoroutineScope, context
            )
        },
        recentLogs = recentLogs.value
    )
}

@Parcelize
data class LoggedEfficiency(
    val efficiency: HistoricalEfficiency,
    val location: Location?,
) : Parcelable

data class EfficiencyLog(
    val odometer: Float,
    val dischargeKwh: Float,
    val timestamp: TimeMark?,
    val location: Location?
)

private fun onStartStopClick(
    startPoint: MutableState<EfficiencyLog?>,
    carState: ComposableCarState,
    recentLogs: MutableState<List<HistoricalEfficiency>>,
    time: TimeSource,
    snackbar: SnackbarHostState?,
    snackbarScope: CoroutineScope?,
    context: Context,
) {
    startPoint.value?.let {
        onStopClick(startPoint, carState, it, recentLogs, snackbarScope, snackbar, context, time)
    } ?: run {
        onStartClick(carState, startPoint, time)
    }
}

private fun onStartClick(
    carState: ComposableCarState,
    startPoint: MutableState<EfficiencyLog?>,
    time: TimeSource
) {
    val odo = carState.currentState(signalName = SName.odometer)?.kmToMi
    val disch = carState.currentState(signalName = SName.kwhDischargeTotal)

    val location = getCarLocation(carState)

    if (odo != null && disch != null) {
        startPoint.value = EfficiencyLog(
            odo,
            disch,
            time.markNow(),
            location
        )
    }
}

private fun getCarLocation(carState: ComposableCarState): Location? {
    val lat = carState.currentState(SName.gpsLatitude)
    val lon = carState.currentState(SName.gpsLongitude)
    val location = if (lat != null && lon != null) Location(lat, lon) else null
    return location
}

@OptIn(ExperimentalTime::class)
private fun onStopClick(
    startPoint: MutableState<EfficiencyLog?>,
    carState: ComposableCarState,
    startLog: EfficiencyLog,
    recentLogs: MutableState<List<HistoricalEfficiency>>,
    snackbarScope: CoroutineScope?,
    snackbar: SnackbarHostState?,
    context: Context,
    time: TimeSource
) {
    startPoint.value = null
    // New current values
    val odoMi = carState.currentState(signalName = SName.odometer)?.kmToMi
    val disch = carState.currentState(signalName = SName.kwhDischargeTotal)
    val location = getCarLocation(carState)

    // If location is missing that's fine
    if (odoMi != null && disch != null) {
        val dOdo = odoMi - startLog.odometer
        val dDischKwh = disch - startLog.dischargeKwh
        val dTime: Duration? = startLog.timestamp?.elapsedNow()
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
        GlobalScope.launch {
            csvWriter().writeAllAsync(
                listOf(
                    listOf(
                        newLog.efficiency,
                        newLog.avgSpeed,
                        newLog.mileage,
                        Calendar.getInstance().time,
                        startLog.location,
                        location
                    )
                ), File(context.filesDir, "effiency_logs.txt"), append = true
            )
            val file: File = File(context.filesDir, "effiency_logs.txt")
//            file.appendText("${newLog.efficiency}, ${newLog.avgSpeed}, ${newLog.mileage}, ${time.markNow()}, ${newL}")
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