package com.vknxworks.adctrl.ui.queries

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.QueryFilter
import com.vknxworks.adctrl.data.api.QueryKind
import com.vknxworks.adctrl.data.api.ResultRule
import com.vknxworks.adctrl.ui.theme.Health
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun kindLabel(kind: QueryKind): Int = when (kind) {
    QueryKind.BLOCKED -> R.string.kind_blocked
    QueryKind.BLOCKED_SERVICE -> R.string.kind_blocked_service
    QueryKind.SAFE_BROWSING -> R.string.kind_safe_browsing
    QueryKind.PARENTAL -> R.string.kind_parental
    QueryKind.SAFE_SEARCH -> R.string.kind_safe_search
    QueryKind.ALLOWED -> R.string.kind_allowed
    QueryKind.REWRITTEN -> R.string.kind_rewritten
    QueryKind.PROCESSED -> R.string.kind_processed
    QueryKind.ERROR -> R.string.kind_error
}

/** Shape as well as colour tells the kinds apart. */
fun kindIcon(kind: QueryKind): ImageVector = when (kind) {
    QueryKind.BLOCKED -> Icons.Filled.Block
    QueryKind.BLOCKED_SERVICE -> Icons.Filled.Apps
    QueryKind.SAFE_BROWSING -> Icons.Filled.GppBad
    QueryKind.PARENTAL -> Icons.Filled.ChildCare
    QueryKind.SAFE_SEARCH -> Icons.Filled.Search
    QueryKind.ALLOWED -> Icons.Filled.Verified
    QueryKind.REWRITTEN -> Icons.Filled.SwapHoriz
    QueryKind.PROCESSED -> Icons.Filled.CheckCircle
    QueryKind.ERROR -> Icons.Filled.Error
}

@Composable
fun kindColor(kind: QueryKind): Color = when {
    kind.isBlocked -> Health.critical
    kind == QueryKind.ALLOWED -> Health.ok
    kind == QueryKind.REWRITTEN || kind == QueryKind.SAFE_SEARCH -> MaterialTheme.colorScheme.tertiary
    kind == QueryKind.ERROR -> Health.warn
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun filterLabel(f: QueryFilter): Int = when (f) {
    QueryFilter.ALL -> R.string.qf_all
    QueryFilter.BLOCKED -> R.string.qf_blocked
    QueryFilter.ALLOWED -> R.string.qf_allowed
    QueryFilter.PROCESSED -> R.string.qf_processed
    QueryFilter.REWRITTEN -> R.string.qf_rewritten
    QueryFilter.SAFE_BROWSING -> R.string.qf_safe_browsing
    QueryFilter.PARENTAL -> R.string.qf_parental
}

/** Which list a rule came from: 0 is the custom rules, unknown IDs (e.g. blocked services) show their number. */
@Composable
fun ruleSource(rule: ResultRule, names: Map<Long, String>): String = when {
    rule.filterListId == 0L -> stringResource(R.string.source_custom_rules)
    names[rule.filterListId] != null -> names.getValue(rule.filterListId)
    rule.filterListId < 0 -> stringResource(R.string.source_builtin)
    else -> stringResource(R.string.source_list_id, rule.filterListId)
}

private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/** "14:02:31" today, "21 Oct 2026, 14:02" before, in the phone's time zone. */
fun queryTime(raw: String): String = runCatching {
    val t = OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault())
    if (t.toLocalDate() == LocalDate.now()) t.format(timeFormat) else t.format(dateTimeFormat)
}.getOrDefault(raw)

/** The upstream without its scheme, for the row: "tls://1.1.1.1" → "1.1.1.1". */
fun shortUpstream(u: String): String = u.substringAfter("://").trimEnd('/')
