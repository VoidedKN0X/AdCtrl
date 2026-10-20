package com.vknxworks.adctrl.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.ui.theme.Gutter

/** A main tab that isn't built yet: its title and what it will show. */
@Composable
fun TabPlaceholder(@StringRes title: Int, @StringRes hint: Int, icon: ImageVector) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(title))
        Text(
            stringResource(hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter),
        )
        EmptyState(
            icon = icon,
            title = stringResource(R.string.tab_coming_title),
            body = stringResource(R.string.tab_coming_body),
            modifier = Modifier.weight(1f).padding(bottom = LocalBottomClearance.current),
        )
        Spacer(Modifier.height(8.dp))
    }
}
