package com.salroumi.smscleaner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.salroumi.smscleaner.analysis.CategoryRules
import com.salroumi.smscleaner.analysis.Flow
import com.salroumi.smscleaner.analysis.MonthComparison
import com.salroumi.smscleaner.analysis.SpendCategory
import com.salroumi.smscleaner.analysis.TransactionSummary
import com.salroumi.smscleaner.analysis.TxEntry
import com.salroumi.smscleaner.analysis.compareMonths
import com.salroumi.smscleaner.analysis.detectSubscriptions
import com.salroumi.smscleaner.analysis.merchantKey
import com.salroumi.smscleaner.analysis.monthKey
import com.salroumi.smscleaner.analysis.previousMonth
import com.salroumi.smscleaner.analysis.spendingByDay
import com.salroumi.smscleaner.analysis.spendingByMonth
import com.salroumi.smscleaner.analysis.summarizeTransactions
import com.salroumi.smscleaner.analysis.topMerchants
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)

private fun monthLabel(key: Int, pattern: String = "MMMM yyyy"): String {
    val c = Calendar.getInstance().apply { clear(); set(key / 100, key % 100 - 1, 1) }
    return SimpleDateFormat(pattern, Locale("ar")).format(c.time)
}

private fun formatMoney(amount: Double, currency: String): String {
    val unit = if (currency == "SAR") "ر.س" else currency
    return String.format(Locale.US, "%,.2f", amount) + " " + unit
}

private fun formatDate(date: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(date))

private enum class TxSection(val label: String) {
    OVERVIEW("نظرة عامة"),
    LIST("العمليات"),
    MERCHANTS("التجار"),
    SUBSCRIPTIONS("الاشتراكات"),
}

private enum class FlowFilter(val label: String) { ALL("الكل"), OUT("مصروفات"), IN("دخل"), DECLINED("مرفوضة") }

/** Month picker values besides a yyyyMM key. */
private const val LATEST_MONTH = 0
private const val ALL_MONTHS = -1

@Composable
fun TransactionsList(items: List<ReviewItem>, rules: CategoryRules, actions: ReviewActions) {
    val all = remember(items, rules) {
        items.mapNotNull { item ->
            val tx = item.verdict.transaction ?: return@mapNotNull null
            if (rules.isIgnored(item.sms.id, item.sms.address)) return@mapNotNull null
            TxEntry(
                id = item.sms.id,
                date = item.sms.date,
                tx = rules.apply(item.sms.id, tx),
                sender = item.sms.contactName ?: item.sms.address,
                body = item.sms.body,
                address = item.sms.address,
            )
        }.sortedByDescending { it.date }
    }
    val months = remember(all) {
        all.groupingBy { monthKey(it.date) }.eachCount().toList().sortedByDescending { it.first }
    }
    var section by rememberSaveable { mutableStateOf(TxSection.OVERVIEW) }
    var monthPick by rememberSaveable { mutableStateOf(LATEST_MONTH) }
    var category by rememberSaveable { mutableStateOf<SpendCategory?>(null) }
    var merchant by rememberSaveable { mutableStateOf<String?>(null) }
    var flow by rememberSaveable { mutableStateOf(FlowFilter.ALL) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<TxEntry?>(null) }
    var ignoring by remember { mutableStateOf<TxEntry?>(null) }

    val month = when (monthPick) {
        LATEST_MONTH -> months.firstOrNull()?.first
        ALL_MONTHS -> null
        else -> monthPick
    }
    val inMonth = if (month == null) all else all.filter { monthKey(it.date) == month }
    val summary = summarizeTransactions(inMonth.map { it.tx })
    val comparison = month?.let { m ->
        compareMonths(inMonth.map { it.tx }, all.filter { monthKey(it.date) == previousMonth(m) }.map { it.tx })
    }

    fun showList(newCategory: SpendCategory? = null, newMerchant: String? = null) {
        category = newCategory
        merchant = newMerchant
        flow = FlowFilter.ALL
        section = TxSection.LIST
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (all.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text("لم يتم العثور على عمليات مالية", fontWeight = FontWeight.Bold)
                    Text(
                        "تُقرأ العمليات من رسائل البنوك والمحافظ (شراء، سحب، حوالة، راتب…). " +
                            "رسائل البنوك التي لم تُفهم صيغتها تبقى في تبويب الرسائل.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (rules.ignoredCount > 0) item { IgnoredRow(rules.ignoredCount, actions.onRestoreIgnored) }
            return@LazyColumn
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TxSection.entries) { s ->
                    FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s.label) })
                }
            }
        }
        // Subscriptions look at the whole history, so the month picker is hidden there.
        if (section != TxSection.SUBSCRIPTIONS && months.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = month == null,
                            onClick = { monthPick = ALL_MONTHS },
                            label = { Text("كل الأشهر") },
                        )
                    }
                    items(months, key = { it.first }) { (key, count) ->
                        FilterChip(
                            selected = month == key,
                            onClick = { monthPick = key },
                            label = { Text("${monthLabel(key)} ($count)") },
                        )
                    }
                }
            }
        }
        when (section) {
            TxSection.OVERVIEW -> overview(all, month, summary, comparison, inMonth.size, onCategory = { showList(it) })
            TxSection.LIST -> transactionList(
                entries = inMonth,
                category = category,
                merchant = merchant,
                flow = flow,
                expanded = expanded,
                onFlow = { flow = it },
                onClearCategory = { category = null },
                onClearMerchant = { merchant = null },
                onExpand = { expanded = if (expanded == it) null else it },
                onEdit = { editing = it },
                onIgnore = { ignoring = it },
                onExport = { shown ->
                    val name = "transactions-" + (month?.let { "${it / 100}-${"%02d".format(it % 100)}" } ?: "all") + ".csv"
                    actions.onExport(name, shown)
                },
            )
            TxSection.MERCHANTS -> merchants(inMonth, summary.currency, onMerchant = { showList(newMerchant = it) })
            TxSection.SUBSCRIPTIONS -> subscriptions(all)
        }
        if (section == TxSection.LIST && rules.ignoredCount > 0) {
            item { IgnoredRow(rules.ignoredCount, actions.onRestoreIgnored) }
        }
    }

    ignoring?.let { entry ->
        AlertDialog(
            onDismissRequest = { ignoring = null },
            title = { Text("ليست عملية مالية") },
            text = { Text("استبعاد هذه الرسالة من العمليات المالية؟ لن تُحذف الرسالة، وتبقى في تبويب الرسائل.") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        ignoring = null
                        actions.onIgnoreTransaction(entry.id, entry.address, false)
                    }) { Text("هذه الرسالة فقط") }
                    TextButton(onClick = {
                        ignoring = null
                        actions.onIgnoreTransaction(entry.id, entry.address, true)
                    }) { Text("كل رسائل ${entry.sender}") }
                    TextButton(onClick = { ignoring = null }) { Text("إلغاء") }
                }
            },
        )
    }

    editing?.let { entry ->
        CategoryDialog(
            entry = entry,
            onDismiss = { editing = null },
            onPick = { picked, forMerchant ->
                editing = null
                actions.onSetCategory(entry.id, entry.tx.counterparty, picked, forMerchant)
            },
        )
    }
}

private fun LazyListScope.overview(
    all: List<TxEntry>,
    month: Int?,
    summary: TransactionSummary,
    comparison: MonthComparison?,
    count: Int,
    onCategory: (SpendCategory) -> Unit,
) {
    item { SummaryCard(summary, comparison, month, count) }
    item { SpendingChart(all, summary.currency, month) }
    if (summary.byCategory.isNotEmpty()) {
        item { CategoryBreakdown(summary, comparison, month, onCategory) }
    }
}

@Composable
private fun SummaryCard(summary: TransactionSummary, comparison: MonthComparison?, month: Int?, count: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row {
                SummaryValue("الدخل", summary.income, summary.currency, IncomeColor, Modifier.weight(1f))
                SummaryValue("المصروفات", summary.expenses, summary.currency, ExpenseColor, Modifier.weight(1f))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SummaryValue(
                "الصافي",
                summary.net,
                summary.currency,
                if (summary.net >= 0) IncomeColor else ExpenseColor,
                Modifier.fillMaxWidth(),
            )
            val percent = comparison?.percent
            if (month != null && comparison != null && percent != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChangeBadge(percent)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "مقارنة بمصروفات ${monthLabel(previousMonth(month))} " +
                            "(${formatMoney(comparison.previousExpenses, summary.currency)})",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val notes = buildList {
                add("$count عملية")
                if (summary.otherCurrencyCount > 0) add("${summary.otherCurrencyCount} بعملات أخرى غير محسوبة في المجموع")
                if (summary.declinedCount > 0) add("${summary.declinedCount} مرفوضة")
            }
            Text(notes.joinToString(" • "), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Spending up is shown as bad (red ▲), down as good (green ▼); the arrow keeps it readable without color. */
@Composable
private fun ChangeBadge(percent: Int) {
    val (arrow, color) = when {
        percent > 0 -> "▲" to ExpenseColor
        percent < 0 -> "▼" to IncomeColor
        else -> "=" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text("$arrow ${abs(percent)}%", color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SummaryValue(label: String, amount: Double, currency: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(formatMoney(amount, currency), color = color, fontWeight = FontWeight.Bold)
    }
}

/** Daily spending bars for one month, or monthly bars for all months. Tap a bar for its value. */
@Composable
private fun SpendingChart(all: List<TxEntry>, currency: String, month: Int?) {
    val bars: List<Pair<String, Double>> = remember(all, currency, month) {
        if (month != null) {
            spendingByDay(all, currency, month).mapIndexed { i, v -> "${i + 1} ${monthLabel(month, "MMMM")}" to v }
        } else {
            spendingByMonth(all, currency).map { (key, v) -> monthLabel(key) to v }
        }
    }
    if (bars.isEmpty() || bars.all { it.second == 0.0 }) return
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val maxIndex = bars.indices.maxBy { bars[it].second }
    val shownIndex = selected ?: maxIndex
    val barColor = if (isSystemInDarkTheme()) Color(0xFF3987E5) else Color(0xFF2A78D6)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val max = bars[maxIndex].second

    Card {
        Column(Modifier.padding(12.dp)) {
            Text(if (month != null) "الصرف اليومي" else "الصرف الشهري", fontWeight = FontWeight.Bold)
            Text(
                (if (selected == null) "الأعلى: " else "") +
                    "${bars[shownIndex].first}: ${formatMoney(bars[shownIndex].second, currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // Time runs left to right like a calendar, even though the rest of the screen is RTL.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .pointerInput(bars) {
                                detectTapGestures { offset ->
                                    val i = (offset.x / (size.width.toFloat() / bars.size)).toInt()
                                        .coerceIn(0, bars.size - 1)
                                    selected = if (selected == i) null else i
                                }
                            },
                    ) {
                        val slot = size.width / bars.size
                        val gap = 2.dp.toPx()
                        val radius = minOf(4.dp.toPx(), (slot - gap) / 2)
                        bars.forEachIndexed { i, (_, value) ->
                            if (value <= 0.0) return@forEachIndexed
                            val h = (value / max * size.height).toFloat().coerceAtLeast(2.dp.toPx())
                            val left = i * slot + gap / 2
                            val width = slot - gap
                            val color = if (selected == null || selected == i) barColor else barColor.copy(alpha = 0.35f)
                            // Rounded top, square bottom on the baseline.
                            drawRoundRect(color, Offset(left, size.height - h), Size(width, h), CornerRadius(radius))
                            if (h > radius) {
                                drawRect(color, Offset(left, size.height - radius), Size(width, radius))
                            }
                        }
                        drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                    }
                    Row(Modifier.fillMaxWidth()) {
                        val style = MaterialTheme.typography.labelSmall
                        val color = MaterialTheme.colorScheme.onSurfaceVariant
                        if (month != null) {
                            Text("1", style = style, color = color)
                            Spacer(Modifier.weight(1f))
                            Text("${bars.size}", style = style, color = color)
                        } else {
                            Text(bars.first().first, style = style, color = color)
                            Spacer(Modifier.weight(1f))
                            if (bars.size > 1) Text(bars.last().first, style = style, color = color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryBreakdown(
    summary: TransactionSummary,
    comparison: MonthComparison?,
    month: Int?,
    onSelect: (SpendCategory) -> Unit,
) {
    val changes = comparison?.changes?.associateBy { it.category }.orEmpty()
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("المصروفات حسب الفئة", fontWeight = FontWeight.Bold)
            if (month != null && comparison != null && comparison.previousExpenses > 0) {
                Text(
                    "النسبة بجانب كل فئة مقارنة بـ ${monthLabel(previousMonth(month))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            summary.byCategory.forEach { (category, amount) ->
                val share = if (summary.expenses > 0) (amount / summary.expenses).toFloat() else 0f
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(category) }
                        .padding(vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category.label, modifier = Modifier.weight(1f))
                        changes[category]?.percent?.let {
                            ChangeBadge(it)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            "${formatMoney(amount, summary.currency)} • ${(share * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
                }
            }
            Text(
                "اضغط على فئة لعرض عملياتها",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.transactionList(
    entries: List<TxEntry>,
    category: SpendCategory?,
    merchant: String?,
    flow: FlowFilter,
    expanded: Long?,
    onFlow: (FlowFilter) -> Unit,
    onClearCategory: () -> Unit,
    onClearMerchant: () -> Unit,
    onExpand: (Long) -> Unit,
    onEdit: (TxEntry) -> Unit,
    onIgnore: (TxEntry) -> Unit,
    onExport: (List<TxEntry>) -> Unit,
) {
    val shown = entries.filter { e ->
        val tx = e.tx
        (category == null || (tx.category == category && tx.type.flow == Flow.OUT)) &&
            (merchant == null || tx.counterparty?.let(::merchantKey) == merchant) &&
            when (flow) {
                FlowFilter.ALL -> true
                FlowFilter.OUT -> tx.type.flow == Flow.OUT
                FlowFilter.IN -> tx.type.flow == Flow.IN
                FlowFilter.DECLINED -> tx.type.flow == Flow.NONE
            }
    }
    item {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FlowFilter.entries) { f ->
                FilterChip(selected = flow == f, onClick = { onFlow(f) }, label = { Text(f.label) })
            }
            category?.let { c ->
                item {
                    FilterChip(
                        selected = true,
                        onClick = onClearCategory,
                        label = { Text(c.label) },
                        trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "إلغاء") },
                    )
                }
            }
            merchant?.let { m ->
                item {
                    FilterChip(
                        selected = true,
                        onClick = onClearMerchant,
                        label = { Text(shown.firstOrNull()?.tx?.counterparty ?: m) },
                        trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "إلغاء") },
                    )
                }
            }
        }
    }
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${shown.size} عملية • اضغط على عملية لعرض الرسالة أو تغيير فئتها",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onExport(shown) }, enabled = shown.isNotEmpty()) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("تصدير Excel")
            }
        }
    }
    items(shown, key = { it.id }) { entry ->
        TransactionCard(
            entry,
            expanded = expanded == entry.id,
            onClick = { onExpand(entry.id) },
            onEdit = { onEdit(entry) },
            onIgnore = { onIgnore(entry) },
        )
    }
}

@Composable
private fun TransactionCard(
    entry: TxEntry,
    expanded: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onIgnore: () -> Unit,
) {
    val tx = entry.tx
    val (color, sign) = when (tx.type.flow) {
        Flow.IN -> IncomeColor to "+"
        Flow.OUT -> ExpenseColor to "−"
        Flow.NONE -> MaterialTheme.colorScheme.onSurfaceVariant to ""
    }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tx.counterparty ?: tx.type.label,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(sign + formatMoney(tx.amount, tx.currency), color = color, fontWeight = FontWeight.Bold)
            }
            val details = buildList {
                add(tx.type.label)
                if (tx.type.flow == Flow.OUT) add(tx.category.label)
                tx.account?.let { add("****$it") }
            }
            Text(details.joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.date)) +
                    " • " + entry.sender +
                    (tx.balance?.let { " • الرصيد " + formatMoney(it, tx.currency) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(entry.body, style = MaterialTheme.typography.bodySmall)
                Row {
                    if (tx.type.flow == Flow.OUT) {
                        TextButton(onClick = onEdit) { Text("تغيير الفئة") }
                    }
                    TextButton(onClick = onIgnore) {
                        Text("ليست عملية مالية", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun IgnoredRow(count: Int, onRestore: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$count مستبعدة يدويًا من العمليات المالية",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRestore) { Text("استرجاعها") }
    }
}

@Composable
private fun CategoryDialog(entry: TxEntry, onDismiss: () -> Unit, onPick: (SpendCategory, Boolean) -> Unit) {
    val merchantName = entry.tx.counterparty
    var picked by remember { mutableStateOf(entry.tx.category) }
    var forMerchant by remember { mutableStateOf(merchantName != null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر الفئة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SpendCategory.entries.filter { it != SpendCategory.INCOME }.forEach { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = picked == c, onClick = { picked = c }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = picked == c, onClick = { picked = c })
                        Text(c.label)
                    }
                }
                if (merchantName != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { forMerchant = !forMerchant },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = forMerchant, onCheckedChange = { forMerchant = it })
                        Text("طبّق على كل عمليات «$merchantName» (السابقة والقادمة)")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(picked, forMerchant) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun LazyListScope.merchants(entries: List<TxEntry>, currency: String, onMerchant: (String) -> Unit) {
    val top = topMerchants(entries.map { it.tx }, currency)
    if (top.isEmpty()) {
        item { Text("لا توجد مشتريات لتجار في هذه الفترة", Modifier.padding(24.dp)) }
        return
    }
    val total = top.sumOf { it.total }
    item {
        Text(
            "أكثر ${top.size} تجار صرفًا • اضغط على تاجر لعرض عملياته",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    items(top.withIndex().toList(), key = { it.value.key }) { (rank, m) ->
        Card(Modifier.fillMaxWidth().clickable { onMerchant(m.key) }) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${rank + 1}.", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${m.category.label} • ${m.count} مرة", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(formatMoney(m.total, currency), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { (m.total / top.first().total).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${(m.total / total * 100).toInt()}% من صرف أكثر التجار",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LazyListScope.subscriptions(all: List<TxEntry>) {
    val subs = detectSubscriptions(all)
    if (subs.isEmpty()) {
        item {
            Column(Modifier.padding(24.dp)) {
                Text("لم يتم العثور على اشتراكات متكررة", fontWeight = FontWeight.Bold)
                Text(
                    "يُعتبر الخصم اشتراكًا إذا تكرر من نفس التاجر بنفس المبلغ تقريبًا كل شهر.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        return
    }
    val active = subs.filter { it.active }
    item {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                Text("اشتراكاتك النشطة", style = MaterialTheme.typography.labelMedium)
                active.groupBy { it.currency }.forEach { (currency, list) ->
                    Text(
                        formatMoney(list.sumOf { it.amount }, currency) + " شهريًا",
                        fontWeight = FontWeight.Bold,
                        color = ExpenseColor,
                    )
                }
                Text(
                    "${active.size} اشتراك نشط • ${subs.size - active.size} متوقف • " +
                        "تحقق من الاشتراكات التي لم تعد تستخدمها",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
    items(subs, key = { it.key + it.currency }) { s ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.name,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(formatMoney(s.amount, s.currency), fontWeight = FontWeight.Bold)
                }
                Text(
                    "${s.category.label} • ${s.count} خصومات • آخر خصم ${formatDate(s.lastDate)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (s.active) "الخصم القادم المتوقع: ${formatDate(s.nextDate)}" else "يبدو متوقفًا (لم يُخصم مؤخرًا)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
