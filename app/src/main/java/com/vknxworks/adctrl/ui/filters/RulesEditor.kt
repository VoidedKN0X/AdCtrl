package com.vknxworks.adctrl.ui.filters

import com.vknxworks.adctrl.ui.theme.JetBrainsMono
import com.vknxworks.adctrl.ui.components.CardBorder
import androidx.activity.compose.BackHandler
import com.vknxworks.adctrl.ui.components.CtrlAlertDialog
import com.vknxworks.adctrl.ui.components.CtrlTextButton
import com.vknxworks.adctrl.ui.components.CtrlTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vknxworks.adctrl.R
import com.vknxworks.adctrl.data.filters.DiffLine
import com.vknxworks.adctrl.data.filters.HintKind
import com.vknxworks.adctrl.data.filters.LineKind
import com.vknxworks.adctrl.data.filters.RuleHint
import com.vknxworks.adctrl.data.filters.RulesSyntax
import com.vknxworks.adctrl.ui.components.LocalBottomClearance
import com.vknxworks.adctrl.ui.theme.Gutter
import com.vknxworks.adctrl.ui.theme.Health
import kotlinx.coroutines.launch

private val LINE_HEIGHT = 20.sp
private val CODE_SIZE = 13.sp

/** Colours for the rule highlighting; fixed so allow and block always look different. */
private data class RuleColors(val comment: Color, val allow: Color, val block: Color, val hosts: Color, val modifier: Color, val anchor: Color, val match: Color)

@Composable
private fun ruleColors() = RuleColors(
    comment = MaterialTheme.colorScheme.onSurfaceVariant,
    allow = Health.ok,
    block = MaterialTheme.colorScheme.onSurface,
    hosts = MaterialTheme.colorScheme.tertiary,
    modifier = MaterialTheme.colorScheme.primary,
    anchor = Health.warn,
    match = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
)

/** Colours each line by what it is: comments, allow rules (@@), modifiers ($…), anchors (|| and ^), and search matches. */
private class RuleHighlight(private val colors: RuleColors, private val search: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val out = buildAnnotatedString {
            append(s)
            var start = 0
            for (line in s.split('\n')) {
                val end = start + line.length
                when (RulesSyntax.kind(line)) {
                    LineKind.COMMENT -> addStyle(SpanStyle(color = colors.comment), start, end)
                    LineKind.ALLOW -> addStyle(SpanStyle(color = colors.allow), start, end)
                    LineKind.HOSTS -> addStyle(SpanStyle(color = colors.hosts), start, end)
                    LineKind.BLOCK, LineKind.REGEX -> addStyle(SpanStyle(color = colors.block), start, end)
                    LineKind.BLANK -> Unit
                }
                val kind = RulesSyntax.kind(line)
                if (kind == LineKind.BLOCK || kind == LineKind.ALLOW) {
                    val dollar = line.indexOf('$')
                    if (dollar >= 0) addStyle(SpanStyle(color = colors.modifier), start + dollar, end)
                    Regex("""\|\||\^|^@@""").findAll(line).forEach { m ->
                        if (dollar < 0 || m.range.first < dollar) addStyle(SpanStyle(color = colors.anchor), start + m.range.first, start + m.range.last + 1)
                    }
                }
                start = end + 1
            }
            if (search.isNotBlank()) {
                var i = s.indexOf(search, ignoreCase = true)
                while (i >= 0) {
                    addStyle(SpanStyle(background = colors.match), i, i + search.length)
                    i = s.indexOf(search, i + search.length, ignoreCase = true)
                }
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/**
 * The custom rules editor: line numbers, highlighting, search, hints for common mistakes, and
 * saving after a look at exactly which lines changed. Unsaved edits are kept while switching tabs.
 */
@Composable
fun RulesEditor(
    saved: List<String>,
    draft: String?,
    readOnly: Boolean,
    onDraft: (String?) -> Unit,
    onSave: suspend (List<String>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val savedText = remember(saved) { saved.joinToString("\n") }
    var value by remember(savedText) { mutableStateOf(TextFieldValue(draft ?: savedText)) }
    val text = value.text
    val dirty = text != savedText && RulesSyntax.lines(text) != saved
    LaunchedEffect(text) { onDraft(if (dirty) text else null) }

    var search by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var hintsOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<List<DiffLine>?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val lines = remember(text) { text.split('\n') }
    val hints = remember(text) { RulesSyntax.hints(lines) }
    val matches = remember(text, search) {
        if (search.isBlank()) emptyList()
        else generateSequence(text.indexOf(search, ignoreCase = true).takeIf { it >= 0 }) { prev -> text.indexOf(search, prev + search.length, ignoreCase = true).takeIf { it >= 0 } }.toList()
    }
    val colors = ruleColors()
    val highlight = remember(colors, search) { RuleHighlight(colors, search) }
    val vScroll = rememberScrollState()
    val density = LocalDensity.current
    val linePx = with(density) { LINE_HEIGHT.toPx() }

    /** Scrolls to a line (1-based) and puts the cursor at [offset]. */
    fun goTo(offset: Int, length: Int = 0) {
        value = value.copy(selection = TextRange(offset, offset + length))
        val line = text.substring(0, offset.coerceIn(0, text.length)).count { it == '\n' }
        scope.launch { vScroll.animateScrollTo(((line - 3) * linePx).toInt().coerceAtLeast(0)) }
    }

    fun lineStart(line: Int): Int = lines.take(line - 1).sumOf { it.length + 1 }

    // Leaving the Filters tab with unsaved edits asks first.
    BackHandler(enabled = dirty && !readOnly) { leaving = true }

    Column(Modifier.fillMaxSize().imePadding()) {
        // Toolbar: count, search, hints, undo all, save.
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter - 8.dp), verticalAlignment = Alignment.CenterVertically) {
            val ruleCount = lines.count { RulesSyntax.kind(it).let { k -> k != LineKind.BLANK && k != LineKind.COMMENT } }
            Text(
                pluralStringResource(R.plurals.rules_count, ruleCount, ruleCount).let { count ->
                    if (dirty) stringResource(R.string.dot_pair, count, stringResource(R.string.rules_unsaved)) else count
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (dirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) search = "" }) { Icon(Icons.Outlined.Search, stringResource(R.string.rules_search)) }
            if (hints.isNotEmpty()) {
                IconButton(onClick = { hintsOpen = !hintsOpen }) { Icon(Icons.Outlined.Lightbulb, stringResource(R.string.rules_hints), tint = Health.warn) }
            }
            if (!readOnly && dirty) {
                IconButton(onClick = { value = TextFieldValue(savedText); onDraft(null) }) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.rules_discard)) }
                IconButton(onClick = { confirm = RulesSyntax.diff(saved, RulesSyntax.lines(text)) }, enabled = !saving) {
                    Icon(Icons.Outlined.Save, stringResource(R.string.rules_save), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (searchOpen) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CtrlTextField(
                    value = search,
                    onValueChange = { search = it; matchIndex = 0 },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.rules_search)) },
                    supportingText = if (search.isNotBlank()) {
                        { Text(stringResource(R.string.rules_matches, if (matches.isEmpty()) 0 else matchIndex + 1, matches.size)) }
                    } else {
                        null
                    },
                    trailingIcon = if (search.isNotEmpty()) {
                        { IconButton(onClick = { search = "" }) { Icon(Icons.Outlined.Clear, stringResource(R.string.clear)) } }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (matches.isNotEmpty()) { matchIndex = (matchIndex - 1 + matches.size) % matches.size; goTo(matches[matchIndex], search.length) }
                }, enabled = matches.isNotEmpty()) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.rules_prev)) }
                IconButton(onClick = {
                    if (matches.isNotEmpty()) { matchIndex = (matchIndex + 1) % matches.size; goTo(matches[matchIndex], search.length) }
                }, enabled = matches.isNotEmpty()) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.rules_next)) }
            }
        }
        if (hintsOpen && hints.isNotEmpty()) {
            HintsCard(hints, readOnly) { h, fix ->
                hintsOpen = false
                val start = lineStart(h.line)
                val end = start + lines[h.line - 1].length
                if (fix != null) {
                    val prefix = if (lines[h.line - 1].trimStart().startsWith("@@")) "@@" else ""
                    val replaced = text.replaceRange(start, end, prefix + fix)
                    value = TextFieldValue(replaced, TextRange(start))
                } else {
                    goTo(start, end - start)
                }
            }
        }

        // Line numbers and text scroll together; long lines scroll sideways instead of wrapping,
        // so every number stays next to its line.
        val codeStyle = TextStyle(fontFamily = JetBrainsMono, fontSize = CODE_SIZE, lineHeight = LINE_HEIGHT, color = MaterialTheme.colorScheme.onSurface)
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer, border = CardBorder,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(start = Gutter, end = Gutter, top = 4.dp, bottom = LocalBottomClearance.current + 8.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewport = maxWidth
                Row(Modifier.fillMaxSize().verticalScroll(vScroll).padding(vertical = 10.dp)) {
                    val hintLines = remember(hints) { hints.map { it.line }.toSet() }
                    Column(Modifier.padding(start = 8.dp, end = 8.dp)) {
                        lines.indices.forEach { i ->
                            Text(
                                (i + 1).toString(),
                                style = codeStyle.copy(color = if ((i + 1) in hintLines) Health.warn else MaterialTheme.colorScheme.outline),
                                maxLines = 1,
                            )
                        }
                    }
                    Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                        BasicTextField(
                            value = value,
                            onValueChange = { if (!readOnly) value = it },
                            readOnly = readOnly,
                            textStyle = codeStyle,
                            visualTransformation = highlight,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.widthIn(min = viewport - 48.dp).heightIn(min = 200.dp).padding(end = 12.dp),
                        )
                    }
                }
                if (text.isBlank()) {
                    Text(
                        stringResource(R.string.rules_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp, top = 10.dp),
                    )
                }
            }
        }
    }

    confirm?.let { diff ->
        SaveRulesDialog(
            diff = diff,
            hintCount = hints.size,
            onDismiss = { confirm = null },
            onSave = {
                confirm = null
                saving = true
                scope.launch {
                    try {
                        onSave(RulesSyntax.lines(text))
                    } finally {
                        saving = false
                    }
                }
            },
        )
    }
    if (leaving) {
        CtrlAlertDialog(
            onDismissRequest = { leaving = false },
            title = { Text(stringResource(R.string.rules_leave_title)) },
            text = { Text(stringResource(R.string.rules_leave_body)) },
            confirmButton = {
                CtrlTextButton(onClick = { leaving = false; value = TextFieldValue(savedText); onDraft(null) }) {
                    Text(stringResource(R.string.rules_discard), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { CtrlTextButton(onClick = { leaving = false }) { Text(stringResource(R.string.rules_keep_editing)) } },
        )
    }
}

@Composable
private fun HintsCard(hints: List<RuleHint>, readOnly: Boolean, onPick: (RuleHint, fix: String?) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, border = CardBorder,
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).padding(horizontal = Gutter, vertical = 4.dp),
    ) {
        LazyColumn(Modifier.padding(vertical = 6.dp)) {
            items(hints, key = { "${it.line}-${it.kind}" }) { h ->
                val fix = if (readOnly) null else RulesSyntax.suggestion(h)
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.rules_hint_line, h.line, hintText(h)), style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CtrlTextButton(onClick = { onPick(h, null) }) { Text(stringResource(R.string.rules_hint_show)) }
                        if (fix != null) CtrlTextButton(onClick = { onPick(h, fix) }) { Text(stringResource(R.string.rules_hint_fix, fix), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
private fun hintText(h: RuleHint): String = when (h.kind) {
    HintKind.HAS_SCHEME -> stringResource(R.string.hint_scheme)
    HintKind.HAS_PATH -> stringResource(R.string.hint_path)
    HintKind.LEADING_WILDCARD -> stringResource(R.string.hint_wildcard)
    HintKind.MISSING_SEPARATOR -> stringResource(R.string.hint_separator)
    HintKind.COSMETIC -> stringResource(R.string.hint_cosmetic)
    HintKind.EMPTY -> stringResource(R.string.hint_empty)
    HintKind.DUPLICATE -> stringResource(R.string.hint_duplicate, h.text)
    HintKind.SPACES -> stringResource(R.string.hint_spaces)
}

/** Shows exactly which lines are removed and added before saving. */
@Composable
private fun SaveRulesDialog(diff: List<DiffLine>, hintCount: Int, onDismiss: () -> Unit, onSave: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    CtrlAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Save, null) },
        title = { Text(stringResource(R.string.rules_save_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val added = diff.count { it.added }
                val removed = diff.size - added
                Text(stringResource(R.string.rules_save_summary, added, removed), style = MaterialTheme.typography.bodyMedium)
                if (hintCount > 0) Text(stringResource(R.string.rules_save_hints, hintCount), style = MaterialTheme.typography.bodySmall, color = Health.warn)
                Surface(shape = RoundedCornerShape(8.dp), color = cs.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    LazyColumn(Modifier.padding(8.dp)) {
                        items(diff) { d ->
                            Text(
                                (if (d.added) "+ " else "− ") + d.text,
                                fontFamily = JetBrainsMono,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (d.added) Health.ok else Health.critical,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                                    .background((if (d.added) Health.ok else Health.critical).copy(alpha = 0.08f)).padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            Spacer(Modifier.size(2.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { CtrlTextButton(onSave) { Text(stringResource(R.string.rules_save)) } },
        dismissButton = { CtrlTextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
