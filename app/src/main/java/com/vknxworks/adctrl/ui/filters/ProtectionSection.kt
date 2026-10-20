package com.vknxworks.adctrl.ui.filters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.api.SafeSearch
import com.vknxworks.adctrl.ui.components.Group
import com.vknxworks.adctrl.ui.components.GroupRow
import com.vknxworks.adctrl.ui.components.GroupSwitch
import com.vknxworks.adctrl.ui.components.LocalBottomClearance

/** Safe browsing, parental control and safe search for the whole network (clients can override them later). */
@Composable
fun ProtectionSection(
    safeBrowsing: Boolean,
    parental: Boolean,
    safeSearch: SafeSearch,
    readOnly: Boolean,
    onSafeBrowsing: (Boolean) -> Unit,
    onParental: (Boolean) -> Unit,
    onSafeSearch: (SafeSearch) -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalBottomClearance.current + 16.dp)) {
        Group(footer = stringResource(R.string.protection_footer)) {
            if (readOnly) {
                GroupRow(stringResource(R.string.safe_browsing), stateText(safeBrowsing), icon = Icons.Filled.GppBad)
                GroupRow(stringResource(R.string.parental), stateText(parental), icon = Icons.Filled.ChildCare)
                GroupRow(stringResource(R.string.safe_search), stateText(safeSearch.enabled), icon = Icons.Filled.Search)
            } else {
                GroupSwitch(stringResource(R.string.safe_browsing), stringResource(R.string.safe_browsing_subtitle), checked = safeBrowsing, icon = Icons.Filled.GppBad, onChange = onSafeBrowsing)
                GroupSwitch(stringResource(R.string.parental), stringResource(R.string.parental_subtitle), checked = parental, icon = Icons.Filled.ChildCare, onChange = onParental)
                GroupSwitch(stringResource(R.string.safe_search), stringResource(R.string.safe_search_subtitle), checked = safeSearch.enabled, icon = Icons.Filled.Search) {
                    onSafeSearch(safeSearch.copy(enabled = it))
                }
            }
        }
        if (safeSearch.enabled) {
            Group(stringResource(R.string.safe_search_engines)) {
                listOf(
                    "Google" to safeSearch.google, "YouTube" to safeSearch.youtube, "Bing" to safeSearch.bing,
                    "DuckDuckGo" to safeSearch.duckduckgo, "Ecosia" to safeSearch.ecosia, "Yandex" to safeSearch.yandex, "Pixabay" to safeSearch.pixabay,
                ).forEach { (name, on) ->
                    if (readOnly) {
                        GroupRow(name, stateText(on))
                    } else {
                        GroupSwitch(name, checked = on) { v ->
                            onSafeSearch(
                                when (name) {
                                    "Google" -> safeSearch.copy(google = v)
                                    "YouTube" -> safeSearch.copy(youtube = v)
                                    "Bing" -> safeSearch.copy(bing = v)
                                    "DuckDuckGo" -> safeSearch.copy(duckduckgo = v)
                                    "Ecosia" -> safeSearch.copy(ecosia = v)
                                    "Yandex" -> safeSearch.copy(yandex = v)
                                    else -> safeSearch.copy(pixabay = v)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun stateText(on: Boolean) = stringResource(if (on) R.string.state_on else R.string.state_off)
