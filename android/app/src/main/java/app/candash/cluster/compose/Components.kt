package app.candash.cluster.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.candash.cluster.compose.ui.theme.TitleLabelTextStyle

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

data class HistoricalEfficiency(
    val efficiency: String,
    val mileage: String,
    val avgSpeed: String?
)