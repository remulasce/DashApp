package app.candash.cluster.compose

import android.os.Parcelable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.candash.cluster.compose.ui.theme.TitleLabelTextStyle
import kotlinx.parcelize.Parcelize

@Composable
fun EfficiencyTable(efficiencies: List<HistoricalEfficiency>, tableTitle: String) {
    Column(
        Modifier
            .width(IntrinsicSize.Max)
            .padding(5.dp)
            .testTag("EfficiencyTable"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tableTitle, style = TitleLabelTextStyle())
        if (efficiencies.isEmpty()) {
            return
        }
        Card {
            Column(
                Modifier
                    .padding(5.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                efficiencies.forEach {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Text(
                            it.efficiency,
                            fontSize = 20.sp,
                            modifier = Modifier.weight(.5f)
                        )
                        Column(
                            modifier = Modifier.weight(.5f)
                        ) {
                            it.avgSpeed?.let {
                                Text(it)
                            }
                            Text(it.mileage)
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewEfficiency() {
    EfficiencyTable(
        listOf(
            HistoricalEfficiency("23 wh/mi", "23 mi", "91 mph"),
            HistoricalEfficiency("223 wh/mi", "3 mi", "71 mph")
            ),
        "Historical Efficiency"
    )
}

@Parcelize
data class HistoricalEfficiency(
    val efficiency: String,
    val mileage: String,
    val avgSpeed: String?
): Parcelable

val LocalSnackbarHost = compositionLocalOf<SnackbarHostState?> {
    null
}