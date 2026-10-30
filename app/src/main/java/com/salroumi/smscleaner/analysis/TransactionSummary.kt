package com.salroumi.smscleaner.analysis

data class TransactionSummary(
    /** The currency totals are in: the most common one, normally SAR. */
    val currency: String,
    val income: Double,
    val expenses: Double,
    /** Spending per category in [currency], largest first. */
    val byCategory: List<Pair<SpendCategory, Double>>,
    /** Completed transactions in other currencies, which are left out of the totals. */
    val otherCurrencyCount: Int,
    val declinedCount: Int,
) {
    val net: Double get() = income - expenses
}

/** Totals in [currency], or in the most common currency when it is null. */
fun summarizeTransactions(transactions: List<Transaction>, currency: String? = null): TransactionSummary {
    val completed = transactions.filter { it.type.flow != Flow.NONE }
    val main = currency
        ?: completed.groupingBy { it.currency }.eachCount().maxByOrNull { it.value }?.key ?: "SAR"
    val inCurrency = completed.filter { it.currency == main }
    val spending = inCurrency.filter { it.type.flow == Flow.OUT }
    return TransactionSummary(
        currency = main,
        income = inCurrency.filter { it.type.flow == Flow.IN }.sumOf { it.amount },
        expenses = spending.sumOf { it.amount },
        byCategory = spending.groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amount } }
            .sortedByDescending { it.second },
        otherCurrencyCount = completed.size - inCurrency.size,
        declinedCount = transactions.size - completed.size,
    )
}
