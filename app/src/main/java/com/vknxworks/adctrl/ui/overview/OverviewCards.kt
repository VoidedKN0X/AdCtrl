package com.vknxworks.adctrl.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.Stats
import com.vknxworks.adctrl.data.api.TopEntry
import com.vknxworks.adctrl.data.overview.AttentionItem
import com.vknxworks.adctrl.data.overview.Severity
import com.vknxworks.adctrl.data.overview.StatsRange
import com.vknxworks.adctrl.ui.components.ChartSeries
import com.vknxworks.adctrl.ui.components.LineChart
import com.vknxworks.adctrl.ui.components.formatCount
import com.vknxworks.adctrl.ui.components.formatCountdown
import com.vknxworks.adctrl.ui.components.formatMs
import com.vknxworks.adctrl.ui.components.percent
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health

/** A rounded card with a small heading above it, like the settings groups. */
@Composable
fun OverviewCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// ---------- Attention ----------

@Composable
fun AttentionCard(items: List<AttentionItem>, now: Long) {
    OverviewCard(stringResource(R.string.attention_title)) {
        Column(Modifier.padding(vertical = 4.dp)) {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    SeverityIcon(item.severity)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(attentionTitle(item, now), style = MaterialTheme.typography.bodyLarge)
                        attentionDetail(item)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun attentionTitle(item: AttentionItem, now: Long): String = when (item) {
    AttentionItem.ProtectionOff -> stringResource(R.string.att_protection_off)
    is AttentionItem.Paused -> stringResource(R.string.att_paused, formatCountdown(item.until - now))
    AttentionItem.FilteringOff -> stringResource(R.string.att_filtering_off)
    is AttentionItem.ListsNotUpdating -> pluralStringResource(R.plurals.att_lists_failing, item.lists.size, item.lists.size)
    is AttentionItem.UpdateAvailable -> stringResource(R.string.att_update, item.version)
    AttentionItem.StatsOff -> stringResource(R.string.att_stats_off)
}

@Composable
private fun attentionDetail(item: AttentionItem): String? = when (item) {
    AttentionItem.ProtectionOff -> stringResource(R.string.att_protection_off_detail)
    is AttentionItem.ListsNotUpdating -> stringResource(R.string.att_lists_failing_detail, item.lists.take(5).joinToString(", ") { it.name.ifBlank { it.url } })
    is AttentionItem.UpdateAvailable -> stringResource(if (item.canAutoupdate) R.string.att_update_detail else R.string.att_update_manual)
    AttentionItem.FilteringOff -> stringResource(R.string.att_filtering_off_detail)
    AttentionItem.StatsOff -> stringResource(R.string.att_stats_off_detail)
    is AttentionItem.Paused -> null
}

/** Severity icons differ in shape as well as colour, and carry a label for screen readers. */
@Composable
private fun SeverityIcon(severity: Severity) {
    val (icon, tint, label) = when (severity) {
        Severity.CRITICAL -> Triple(Icons.Filled.Error, Health.critical, R.string.sev_critical)
        Severity.WARNING -> Triple(Icons.Filled.Warning, Health.warn, R.string.sev_warning)
        Severity.INFO -> Triple(Icons.Filled.Info, MaterialTheme.colorScheme.onSurfaceVariant, R.string.sev_info)
    }
    Icon(icon, stringResource(label), Modifier.size(22.dp), tint = tint)
}

// ---------- Range ----------

@Composable
fun RangeChips(available: List<StatsRange>, selected: StatsRange, wholeRetentionLabel: String?, onPick: (StatsRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (available.isEmpty() && wholeRetentionLabel != null) {
            FilterChip(selected = true, onClick = {}, label = { Text(wholeRetentionLabel) })
        }
        available.forEach { r ->
            FilterChip(selected = r == selected, onClick = { onPick(r) }, label = { Text(stringResource(rangeLabel(r))) })
        }
    }
}

fun rangeLabel(r: StatsRange): Int = when (r) {
    StatsRange.DAY -> R.string.range_24h
    StatsRange.WEEK -> R.string.range_7d
    StatsRange.MONTH -> R.string.range_30d
    StatsRange.QUARTER -> R.string.range_90d
}

// ---------- Numbers ----------

@Composable
fun StatsGrid(stats: Stats) {
    val total = stats.numDnsQueries
    val blockedPct = if (total > 0) percent(stats.numBlockedFiltering.toDouble() / total) else 0
    val tiles = listOf(
        Triple(stringResource(R.string.stat_queries), formatCount(total), null),
        Triple(stringResource(R.string.stat_blocked), formatCount(stats.numBlockedFiltering), stringResource(R.string.stat_percent, blockedPct)),
        Triple(stringResource(R.string.stat_safebrowsing), formatCount(stats.numReplacedSafebrowsing), null),
        Triple(stringResource(R.string.stat_parental), formatCount(stats.numReplacedParental), null),
        Triple(stringResource(R.string.stat_safesearch), formatCount(stats.numReplacedSafesearch), null),
        Triple(stringResource(R.string.stat_avg_time), formatMs(stats.avgProcessingTime), null),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value, extra) ->
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                                if (extra != null) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(extra, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 3.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Queries and blocked queries over time, one point per hour or day as AdGuard Home reports them. */
@Composable
fun QueriesChart(stats: Stats, now: Long) {
    val n = stats.dnsQueries.size
    if (n < 2) return
    val unit = stats.unitSeconds
    val lastStart = (now / 1000) / unit * unit
    val times = List(n) { i -> lastStart - (n - 1 - i) * unit }
    fun values(list: List<Long>): List<Double?> = List(n) { i -> list.getOrNull(i)?.toDouble() }
    OverviewCard(stringResource(R.string.chart_title)) {
        LineChart(
            times = times,
            series = listOf(
                ChartSeries(stringResource(R.string.chart_queries), values(stats.dnsQueries)),
                ChartSeries(stringResource(R.string.chart_blocked), values(stats.blockedFiltering)),
            ),
            format = { formatCount(it.toLong()) },
            modifier = Modifier.padding(16.dp),
        )
    }
}

// ---------- Top lists ----------

/** A top list: name, number and a bar relative to the first entry. */
@Composable
fun TopListCard(title: String, entries: List<TopEntry>, empty: String, format: (TopEntry) -> String, label: (TopEntry) -> String = { it.key }) {
    OverviewCard(title) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (entries.isEmpty()) {
                Text(empty, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            val max = entries.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
            entries.forEach { e ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label(e), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(format(e), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        Box(
                            Modifier.fillMaxWidth((e.value / max).toFloat().coerceIn(0.02f, 1f)).fillMaxHeight()
                                .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
    }
}

/** Upstreams with their number of responses and average response time. */
@Composable
fun UpstreamsCard(stats: Stats) {
    val responses = TopEntry.parse(stats.topUpstreamsResponses)
    val times = TopEntry.parse(stats.topUpstreamsAvgTime).associate { it.key to it.value }
    TopListCard(
        title = stringResource(R.string.top_upstreams),
        entries = responses.take(TOP_COUNT),
        empty = stringResource(R.string.top_empty),
        format = { e ->
            val t = times[e.key]
            if (t != null) "${formatCount(e.value.toLong())} · ${formatMs(t)}" else formatCount(e.value.toLong())
        },
    )
}

const val TOP_COUNT = 10
