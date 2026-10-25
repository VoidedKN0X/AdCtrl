package com.vknxworks.adctrl.ui.filters

import com.vknxworks.adctrl.ui.theme.CtrlColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.container
import com.vknxworks.adctrl.data.api.AghError
import com.vknxworks.adctrl.data.api.AghVersion
import com.vknxworks.adctrl.data.api.Rewrite
import com.vknxworks.adctrl.ui.components.ConnectionBanner
import com.vknxworks.adctrl.ui.components.EmptyState
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.components.TabTitle
import com.vknxworks.adctrl.ui.components.errorMessage
import kotlinx.coroutines.launch

private enum class Section(val label: Int) {
    LISTS(R.string.filters_lists),
    RULES(R.string.filters_rules),
    SERVICES(R.string.filters_services),
    REWRITES(R.string.filters_rewrites),
    PROTECTION(R.string.filters_protection),
}

/** Blocklists and allowlists, custom rules, blocked services, DNS rewrites, and safe browsing, parental control and safe search. */
@Composable
fun FiltersScreen() {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val state by c.filters.state.collectAsStateWithLifecycle()
    val active by c.sessions.active.collectAsStateWithLifecycle()
    val connection by c.sessions.connection.collectAsStateWithLifecycle()
    val server = active?.server
    val readOnly = server?.readOnly == true
    val version = AghVersion.parse(server?.version.orEmpty())
    var section by rememberSaveable { mutableStateOf(Section.LISTS) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    suspend fun load(s: Section) = when (s) {
        Section.LISTS, Section.RULES -> c.filters.loadLists()
        Section.SERVICES -> c.filters.loadServices()
        Section.REWRITES -> c.filters.loadRewrites()
        Section.PROTECTION -> c.filters.loadProtection()
    }

    LaunchedEffect(section, server?.id) { if (server != null) load(section) }

    /** Runs a change; shows [success] when it worked, or what went wrong. */
    fun act(action: suspend () -> Unit, success: String?) {
        busy = true
        scope.launch {
            try {
                action()
                success?.let { snackbar.showSnackbar(it) }
            } catch (e: AghError) {
                snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_filters))
            if (connection.error != null) ConnectionBanner()
            PrimaryScrollableTabRow(
                selectedTabIndex = section.ordinal,
                edgePadding = 12.dp,
                containerColor = CtrlColors.Background,
                contentColor = CtrlColors.Accent,
                divider = { HorizontalDivider(color = CtrlColors.Divider) },
            ) {
                Section.entries.forEach { s ->
                    Tab(
                        selected = section == s,
                        onClick = { section = s },
                        selectedContentColor = CtrlColors.Text,
                        unselectedContentColor = CtrlColors.Muted,
                        text = { Text(stringResource(s.label), fontWeight = FontWeight.SemiBold) },
                    )
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    scope.launch {
                        load(section)
                        refreshing = false
                    }
                },
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            ) {
                val loaded = when (section) {
                    Section.LISTS, Section.RULES -> state.filtering != null
                    Section.SERVICES -> state.blocked != null
                    Section.REWRITES -> state.rewrites != null
                    Section.PROTECTION -> state.safeSearch != null
                }
                when {
                    !loaded && state.error != null -> EmptyState(
                        icon = Icons.Outlined.FilterAlt,
                        title = stringResource(R.string.filters_error_title),
                        body = errorMessage(context, state.error!!, server?.host.orEmpty()),
                        modifier = Modifier.padding(bottom = LocalBottomClearance.current),
                    )
                    !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> when (section) {
                        Section.LISTS -> ListsSection(
                            status = state.filtering!!,
                            readOnly = readOnly,
                            busy = busy,
                            act = ::act,
                            actions = object : ListActions {
                                override suspend fun setFiltering(enabled: Boolean, interval: Int) = c.filters.setFiltering(enabled, interval)
                                override suspend fun refresh() {
                                    val n = c.filters.refreshLists()
                                    snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.lists_updated, n, n))
                                }
                                override suspend fun add(name: String, url: String, allowlist: Boolean) = c.filters.addList(name, url, allowlist)
                                override suspend fun setList(url: String, allowlist: Boolean, enabled: Boolean, name: String, newUrl: String) =
                                    c.filters.setList(url, allowlist, enabled, name, newUrl)
                                override suspend fun remove(url: String, allowlist: Boolean) = c.filters.removeList(url, allowlist)
                            },
                        )
                        Section.RULES -> RulesEditor(
                            saved = state.filtering!!.userRules,
                            draft = state.rulesDraft,
                            readOnly = readOnly,
                            onDraft = c.filters::setRulesDraft,
                            onSave = { rules ->
                                try {
                                    c.filters.saveRules(rules)
                                    snackbar.showSnackbar(context.getString(R.string.rules_saved))
                                } catch (e: AghError) {
                                    snackbar.showSnackbar(errorMessage(context, e, server?.host.orEmpty()))
                                }
                            },
                        )
                        Section.SERVICES -> ServicesSection(
                            services = state.services,
                            config = state.blocked!!,
                            readOnly = readOnly,
                            onToggle = { id, on -> act({ c.filters.setServiceBlocked(id, on) }, null) },
                            onSchedule = { s -> act({ c.filters.setSchedule(s) }, context.getString(R.string.schedule_saved)) },
                        )
                        Section.REWRITES -> RewritesSection(
                            rewrites = state.rewrites!!,
                            allEnabled = state.rewritesEnabled,
                            canToggle = version.rewriteToggle,
                            readOnly = readOnly,
                            actions = object : RewriteActions {
                                override fun add(r: Rewrite) = act({ c.filters.addRewrite(r) }, context.getString(R.string.rewrite_added, r.domain))
                                override fun update(old: Rewrite, new: Rewrite) = act({ c.filters.updateRewrite(old, new) }, null)
                                override fun delete(r: Rewrite) = act({ c.filters.deleteRewrite(r) }, context.getString(R.string.rewrite_deleted, r.domain))
                                override fun setAllEnabled(on: Boolean) = act({ c.filters.setRewritesEnabled(on) }, null)
                            },
                        )
                        Section.PROTECTION -> ProtectionSection(
                            safeBrowsing = state.safeBrowsing == true,
                            parental = state.parental == true,
                            safeSearch = state.safeSearch!!,
                            readOnly = readOnly,
                            onSafeBrowsing = { on -> act({ c.filters.setSafeBrowsing(on) }, null) },
                            onParental = { on -> act({ c.filters.setParental(on) }, null) },
                            onSafeSearch = { s -> act({ c.filters.setSafeSearch(s) }, null) },
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomClearance.current + 8.dp))
    }
}
