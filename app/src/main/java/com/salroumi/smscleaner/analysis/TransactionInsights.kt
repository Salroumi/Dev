package com.salroumi.smscleaner.analysis

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A parsed transaction together with the message it came from. */
data class TxEntry(
    val id: Long,
    val date: Long,
    val tx: Transaction,
    val sender: String = "",
    val body: String = "",
)

/** Calendar month of a timestamp as yyyyMM, e.g. 202405. */
fun monthKey(date: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = date }
    return c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) + 1
}

fun previousMonth(key: Int): Int = if (key % 100 == 1) (key / 100 - 1) * 100 + 12 else key - 1

/**
 * Groups spellings of one merchant: "HUNGERSTATION 123", "Hungerstation" and "HUNGER-STATION" are not all
 * equal, but branch numbers and punctuation are dropped so most repeats of a merchant share a key.
 */
fun merchantKey(name: String): String {
    val key = SmsAnalyzer.normalize(name).replace(NON_LETTERS, " ").replace(SPACES, " ").trim()
    return key.ifEmpty { SmsAnalyzer.normalize(name) }
}

private val NON_LETTERS = Regex("[^\\p{L} ]")
private val SPACES = Regex("\\s+")

/** Categories the user picked by hand: for every transaction of a merchant, or for one message. */
data class CategoryRules(
    val byMerchant: Map<String, SpendCategory> = emptyMap(),
    val byMessage: Map<Long, SpendCategory> = emptyMap(),
) {
    fun apply(id: Long, tx: Transaction): Transaction {
        if (tx.type.flow != Flow.OUT) return tx
        val chosen = byMessage[id] ?: tx.counterparty?.let { byMerchant[merchantKey(it)] } ?: return tx
        return tx.copy(category = chosen)
    }
}

/** Percentage change from [previous] to [current], or null when there is nothing to compare with. */
fun percentChange(current: Double, previous: Double): Int? =
    if (previous <= 0.0) null else Math.round((current - previous) / previous * 100).toInt()

data class CategoryChange(val category: SpendCategory, val current: Double, val previous: Double) {
    val percent: Int? get() = percentChange(current, previous)
}

data class MonthComparison(
    val expenses: Double,
    val previousExpenses: Double,
    /** Every category spent on in either month, largest current spending first. */
    val changes: List<CategoryChange>,
) {
    val percent: Int? get() = percentChange(expenses, previousExpenses)
}

fun compareMonths(current: List<Transaction>, previous: List<Transaction>): MonthComparison {
    val now = summarizeTransactions(current)
    val before = summarizeTransactions(previous, now.currency)
    val previousByCategory = before.byCategory.toMap()
    val currentByCategory = now.byCategory.toMap()
    val categories = (now.byCategory.map { it.first } + before.byCategory.map { it.first }).distinct()
    return MonthComparison(
        expenses = now.expenses,
        previousExpenses = before.expenses,
        changes = categories.map {
            CategoryChange(it, currentByCategory[it] ?: 0.0, previousByCategory[it] ?: 0.0)
        }.sortedByDescending { it.current },
    )
}

data class MerchantTotal(
    val key: String,
    val name: String,
    val category: SpendCategory,
    val count: Int,
    val total: Double,
)

/** Merchants with the most spending in [currency], largest first. */
fun topMerchants(transactions: List<Transaction>, currency: String, limit: Int = 10): List<MerchantTotal> =
    transactions
        .filter { it.type.flow == Flow.OUT && it.currency == currency && it.counterparty != null }
        .groupBy { merchantKey(it.counterparty!!) }
        .map { (key, list) ->
            MerchantTotal(
                key = key,
                name = list.groupingBy { it.counterparty!! }.eachCount().maxBy { it.value }.key,
                category = list.groupingBy { it.category }.eachCount().maxBy { it.value }.key,
                count = list.size,
                total = list.sumOf { it.amount },
            )
        }
        .sortedByDescending { it.total }
        .take(limit)

data class Subscription(
    val key: String,
    val name: String,
    val category: SpendCategory,
    /** Typical (median) charge. */
    val amount: Double,
    val currency: String,
    val count: Int,
    val lastDate: Long,
    val nextDate: Long,
    /** Charged recently enough that it is probably still running. */
    val active: Boolean,
)

/**
 * Finds charges that repeat about once a month from the same merchant for about the same amount
 * (streaming, phone bills, gym…). Needs 3 charges, or 2 for merchants that are subscriptions or bills anyway.
 */
fun detectSubscriptions(entries: List<TxEntry>, now: Long = System.currentTimeMillis()): List<Subscription> {
    val candidates = entries.filter {
        (it.tx.type == TransactionType.PURCHASE || it.tx.type == TransactionType.BILL) && it.tx.counterparty != null
    }
    val found = ArrayList<Subscription>()
    for ((key, group) in candidates.groupBy { merchantKey(it.tx.counterparty!!) to it.tx.currency }) {
        val median = median(group.map { it.tx.amount })
        val tolerance = maxOf(median * 0.15, 1.0)
        val charges = group.filter { kotlin.math.abs(it.tx.amount - median) <= tolerance }.sortedBy { it.date }
        val category = charges.lastOrNull()?.tx?.category ?: continue
        val usual = category == SpendCategory.SUBSCRIPTIONS || category == SpendCategory.BILLS
        if (charges.size < (if (usual) 2 else 3)) continue

        val gaps = charges.zipWithNext { a, b -> TimeUnit.MILLISECONDS.toDays(b.date - a.date) }
        val monthly = gaps.count { it in 25..36 }
        // A merchant visited often (coffee every few days) has many short gaps and is not a subscription.
        if (monthly * 3 < gaps.size * 2) continue

        val last = charges.last()
        val gapDays = median(gaps.filter { it in 25..36 }.map { it.toDouble() }).toLong()
        found += Subscription(
            key = key.first,
            name = last.tx.counterparty!!,
            category = category,
            amount = median(charges.map { it.tx.amount }),
            currency = key.second,
            count = charges.size,
            lastDate = last.date,
            nextDate = last.date + TimeUnit.DAYS.toMillis(gapDays),
            active = now - last.date <= TimeUnit.DAYS.toMillis(gapDays + 10),
        )
    }
    return found.sortedWith(compareByDescending<Subscription> { it.active }.thenByDescending { it.amount })
}

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
}

/** Spending in [currency] for each day of month [key] (index 0 = day 1). */
fun spendingByDay(transactions: List<TxEntry>, currency: String, key: Int): DoubleArray {
    val c = Calendar.getInstance().apply { clear(); set(key / 100, key % 100 - 1, 1) }
    val days = DoubleArray(c.getActualMaximum(Calendar.DAY_OF_MONTH))
    for (e in transactions) {
        if (e.tx.type.flow != Flow.OUT || e.tx.currency != currency || monthKey(e.date) != key) continue
        c.timeInMillis = e.date
        days[c.get(Calendar.DAY_OF_MONTH) - 1] += e.tx.amount
    }
    return days
}

/** Spending in [currency] per month, oldest first, including months with no spending in between. */
fun spendingByMonth(transactions: List<TxEntry>, currency: String): List<Pair<Int, Double>> {
    val out = transactions.filter { it.tx.type.flow == Flow.OUT && it.tx.currency == currency }
    if (out.isEmpty()) return emptyList()
    val totals = out.groupBy { monthKey(it.date) }.mapValues { (_, list) -> list.sumOf { it.tx.amount } }
    val result = ArrayList<Pair<Int, Double>>()
    var key = totals.keys.min()
    val last = totals.keys.max()
    while (key <= last) {
        result += key to (totals[key] ?: 0.0)
        key = if (key % 100 == 12) (key / 100 + 1) * 100 + 1 else key + 1
    }
    return result
}

/** CSV that opens correctly in Excel (UTF-8 with BOM, so Arabic is not garbled). */
fun transactionsCsv(entries: List<TxEntry>): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    val sb = StringBuilder("﻿")
    val header = listOf(
        "التاريخ", "النوع", "الفئة", "التاجر / الطرف", "المبلغ", "العملة", "البطاقة / الحساب", "الرصيد", "المرسل", "نص الرسالة",
    )
    sb.append(header.joinToString(",") { csvField(it) }).append("\r\n")
    for (e in entries.sortedBy { it.date }) {
        val tx = e.tx
        // Money out is negative so a SUM in Excel gives the net.
        val signed = when (tx.type.flow) {
            Flow.OUT -> -tx.amount
            else -> tx.amount
        }
        val row = listOf(
            dateFormat.format(Date(e.date)),
            tx.type.label,
            if (tx.type.flow == Flow.OUT) tx.category.label else "",
            tx.counterparty.orEmpty(),
            String.format(Locale.US, "%.2f", signed),
            tx.currency,
            tx.account.orEmpty(),
            tx.balance?.let { String.format(Locale.US, "%.2f", it) }.orEmpty(),
            e.sender,
            e.body,
        )
        sb.append(row.joinToString(",") { csvField(it) }).append("\r\n")
    }
    return sb.toString()
}

private fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
