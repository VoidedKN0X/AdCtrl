package com.vknxworks.adctrl.ui.overview

import androidx.compose.foundation.background
import com.vknxworks.adctrl.ui.components.CtrlFilterChip
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.adctrl.ui.components.CardBorder
import com.vknxworks.adctrl.ui.components.CtrlCard
import com.vknxworks.adctrl.ui.components.CtrlHeroCard
import com.vknxworks.adctrl.ui.components.CtrlLabel
import com.vknxworks.adctrl.ui.components.CtrlProgress
import com.vknxworks.adctrl.ui.theme.CtrlColors
import com.vknxworks.adctrl.ui.theme.CtrlShapes
import com.vknxworks.adctrl.ui.theme.CtrlType
import com.vknxworks.adctrl.ui.components.ChartSeries
import com.vknxworks.adctrl.ui.components.LineChart
import com.vknxworks.adctrl.ui.components.formatCount
import com.vknxworks.adctrl.ui.components.formatCountdown
import com.vknxworks.adctrl.ui.components.formatMs
import com.vknxworks.adctrl.ui.components.percent
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health

/** A Ctrl card with a mono label above it, like the settings groups. */
@Composable
fun OverviewCard(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
        if (title != null) {
            CtrlLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Surface(shape = CtrlShapes.Card, color = CtrlColors.Surface, border = CardBorder, modifier = Modifier.fillMaxWidth()) {
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
                if (i > 0) HorizontalDivider(color = CtrlColors.Outline)
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
        Severity.CRITICAL -> Triple(Icons.Outlined.Error, Health.critical, R.string.sev_critical)
        Severity.WARNING -> Triple(Icons.Outlined.Warning, Health.warn, R.string.sev_warning)
        Severity.INFO -> Triple(Icons.Outlined.Info, CtrlColors.Muted, R.string.sev_info)
    }
    Icon(icon, stringResource(label), Modifier.size(20.dp), tint = tint)
}

// ---------- Range ----------

@Composable
fun RangeChips(available: List<StatsRange>, selected: StatsRange, wholeRetentionLabel: String?, onPick: (StatsRange) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (available.isEmpty() && wholeRetentionLabel != null) {
            CtrlFilterChip(selected = true, onClick = {}, label = { Text(wholeRetentionLabel) })
        }
        available.forEach { r ->
            CtrlFilterChip(selected = r == selected, onClick = { onPick(r) }, label = { Text(stringResource(rangeLabel(r))) })
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

/**
 * The hero card: the share of queries blocked by filters as one giant number, with a bar per hour (or day)
 * of the period underneath. Each bar is that period's queries; the solid part at its foot is what was blocked.
 */
@Composable
fun BlockedHeroCard(stats: Stats, rangeLabel: String?) {
    val total = stats.numDnsQueries
    val blocked = stats.numBlockedFiltering
    val pct = if (total > 0) percent(blocked.toDouble() / total) else 0
    val queries = stats.dnsQueries.takeLast(HERO_BARS)
    val blockedSeries = stats.blockedFiltering.takeLast(HERO_BARS)
    CtrlHeroCard(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.hero_blocked).uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (rangeLabel != null) Text(rangeLabel.uppercase(), style = CtrlType.Label.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.04.em), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Text(pct.toString(), style = CtrlType.Hero)
            Text("%", style = CtrlType.Hero.copy(fontSize = 28.sp, letterSpacing = 0.em), modifier = Modifier.padding(bottom = 6.dp))
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.hero_blocked_of, formatCount(blocked), formatCount(total)),
                style = CtrlType.Data,
                textAlign = TextAlign.End,
                modifier = Modifier.padding(bottom = 6.dp, start = 12.dp),
            )
        }
        if (queries.size >= 2) {
            val max = queries.maxOrNull()?.takeIf { it > 0 } ?: 1L
            Row(
                Modifier.fillMaxWidth().height(34.dp).clearAndSetSemantics { },
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                queries.forEachIndexed { i, q ->
                    val h = (q.toFloat() / max).coerceIn(0.06f, 1f)
                    val b = blockedSeries.getOrNull(i)?.let { if (q > 0) (it.toFloat() / q).coerceIn(0f, 1f) else 0f } ?: 0f
                    Box(
                        Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(2.dp)).background(CtrlColors.OnAccent.copy(alpha = 0.30f)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (b > 0f) Box(Modifier.fillMaxWidth().fillMaxHeight(b.coerceAtLeast(0.08f)).background(CtrlColors.OnAccent))
                    }
                }
            }
        }
    }
}

private const val HERO_BARS = 24

/** The other numbers, two cards per row: big Space Grotesk value, mono label. */
@Composable
fun StatsGrid(stats: Stats) {
    val context = LocalContext.current
    val tiles = listOf(
        stringResource(R.string.stat_queries) to formatCount(stats.numDnsQueries),
        stringResource(R.string.stat_avg_time) to formatMs(context, stats.avgProcessingTime),
        stringResource(R.string.stat_safebrowsing) to formatCount(stats.numReplacedSafebrowsing),
        stringResource(R.string.stat_parental) to formatCount(stats.numReplacedParental),
        stringResource(R.string.stat_safesearch) to formatCount(stats.numReplacedSafesearch),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                row.forEach { (label, value) ->
                    CtrlCard(Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CtrlLabel(label)
                        Text(value, style = CtrlType.Metric, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
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
fun TopListCard(
    title: String,
    entries: List<TopEntry>,
    empty: String,
    format: (TopEntry) -> String,
    label: (TopEntry) -> String = { it.key },
    onClick: ((TopEntry) -> Unit)? = null,
) {
    OverviewCard(title) {
        Column(Modifier.padding(vertical = 6.dp)) {
            if (entries.isEmpty()) {
                Text(empty, style = MaterialTheme.typography.bodyMedium, color = CtrlColors.Muted, modifier = Modifier.padding(16.dp))
            }
            val max = entries.maxOfOrNull { it.value }?.takeIf { it > 0 } ?: 1.0
            entries.forEachIndexed { i, e ->
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (onClick != null) Modifier.clickable { onClick(e) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text((i + 1).toString().padStart(2, '0'), style = CtrlType.Data, color = CtrlColors.Muted, modifier = Modifier.width(28.dp))
                        Text(label(e), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(format(e), style = CtrlType.Data, color = CtrlColors.Muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    CtrlProgress((e.value / max).toFloat().coerceIn(0.02f, 1f), Modifier.padding(start = 28.dp), color = if (i == 0) CtrlColors.Accent else CtrlColors.Text)
                }
            }
        }
    }
}

/** Upstreams with their number of responses and average response time. */
@Composable
fun UpstreamsCard(stats: Stats) {
    val context = LocalContext.current
    val responses = TopEntry.parse(stats.topUpstreamsResponses)
    val times = TopEntry.parse(stats.topUpstreamsAvgTime).associate { it.key to it.value }
    TopListCard(
        title = stringResource(R.string.top_upstreams),
        entries = responses.take(TOP_COUNT),
        empty = stringResource(R.string.top_empty),
        format = { e ->
            val t = times[e.key]
            if (t != null) context.getString(R.string.dot_pair, formatCount(e.value.toLong()), formatMs(context, t)) else formatCount(e.value.toLong())
        },
    )
}

const val TOP_COUNT = 10
