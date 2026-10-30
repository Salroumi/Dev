package com.salroumi.smscleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salroumi.smscleaner.analysis.Flow
import com.salroumi.smscleaner.analysis.SpendCategory
import com.salroumi.smscleaner.analysis.TransactionSummary
import com.salroumi.smscleaner.analysis.summarizeTransactions
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val IncomeColor = Color(0xFF2E7D32)
private val ExpenseColor = Color(0xFFC62828)

/** Calendar month of a timestamp as yyyyMM, e.g. 202405. */
private fun monthKey(date: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = date }
    return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
}

private fun monthLabel(key: Int): String {
    val c = Calendar.getInstance().apply { clear(); set(key / 100, key % 100 - 1, 1) }
    return SimpleDateFormat("MMMM yyyy", Locale("ar")).format(c.time)
}

private fun formatMoney(amount: Double, currency: String): String {
    val unit = if (currency == "SAR") "ر.س" else currency
    return String.format(Locale.US, "%,.2f", amount) + " " + unit
}

private enum class FlowFilter(val label: String) { ALL("الكل"), OUT("مصروفات"), IN("دخل"), DECLINED("مرفوضة") }

@Composable
fun TransactionsList(items: List<ReviewItem>) {
    val transactions = remember(items) {
        items.filter { it.verdict.transaction != null }.sortedByDescending { it.sms.date }
    }
    val months = remember(transactions) {
        transactions.groupingBy { monthKey(it.sms.date) }.eachCount().toList().sortedByDescending { it.first }
    }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    var category by rememberSaveable { mutableStateOf<SpendCategory?>(null) }
    var flow by rememberSaveable { mutableStateOf(FlowFilter.ALL) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }

    val inMonth = if (month == null) transactions else transactions.filter { monthKey(it.sms.date) == month }
    val summary = summarizeTransactions(inMonth.map { it.verdict.transaction!! })
    val shown = inMonth.filter { item ->
        val tx = item.verdict.transaction!!
        (category == null || (tx.category == category && tx.type.flow == Flow.OUT)) &&
            when (flow) {
                FlowFilter.ALL -> true
                FlowFilter.OUT -> tx.type.flow == Flow.OUT
                FlowFilter.IN -> tx.type.flow == Flow.IN
                FlowFilter.DECLINED -> tx.type.flow == Flow.NONE
            }
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (transactions.isEmpty()) {
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
            return@LazyColumn
        }
        if (months.size > 1) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = month == null,
                            onClick = { month = null },
                            label = { Text("كل الأشهر") },
                        )
                    }
                    items(months, key = { it.first }) { (key, count) ->
                        FilterChip(
                            selected = month == key,
                            onClick = { month = if (month == key) null else key },
                            label = { Text("${monthLabel(key)} ($count)") },
                        )
                    }
                }
            }
        }
        item { SummaryCard(summary, inMonth.size) }
        if (summary.byCategory.isNotEmpty()) {
            item {
                CategoryBreakdown(summary, selected = category, onSelect = {
                    category = if (category == it) null else it
                    if (category != null) flow = FlowFilter.ALL
                })
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FlowFilter.entries) { f ->
                    FilterChip(selected = flow == f, onClick = { flow = f }, label = { Text(f.label) })
                }
                category?.let { c ->
                    item {
                        FilterChip(
                            selected = true,
                            onClick = { category = null },
                            label = { Text(c.label) },
                            trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "إلغاء") },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "${shown.size} عملية",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(shown, key = { it.sms.id }) { item ->
            TransactionCard(
                item,
                expanded = expanded == item.sms.id,
                onClick = { expanded = if (expanded == item.sms.id) null else item.sms.id },
            )
        }
    }
}

@Composable
private fun SummaryCard(summary: TransactionSummary, count: Int) {
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

@Composable
private fun SummaryValue(label: String, amount: Double, currency: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(formatMoney(amount, currency), color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun CategoryBreakdown(summary: TransactionSummary, selected: SpendCategory?, onSelect: (SpendCategory) -> Unit) {
    Card {
        Column(Modifier.padding(12.dp)) {
            Text("المصروفات حسب الفئة", fontWeight = FontWeight.Bold)
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
                        Text(
                            category.label,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (category == selected) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            "${formatMoney(amount, summary.currency)} • ${(share * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun TransactionCard(item: ReviewItem, expanded: Boolean, onClick: () -> Unit) {
    val tx = item.verdict.transaction!!
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
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.sms.date)) +
                    " • " + (item.sms.contactName ?: item.sms.address) +
                    (tx.balance?.let { " • الرصيد " + formatMoney(it, tx.currency) } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(item.sms.body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
