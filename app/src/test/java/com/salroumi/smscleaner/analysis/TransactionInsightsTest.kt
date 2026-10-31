package com.salroumi.smscleaner.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TransactionInsightsTest {

    private fun day(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month - 1, day, 12, 0) }.timeInMillis

    private fun buy(merchant: String, amount: Double, category: SpendCategory = SpendCategory.OTHER) =
        Transaction(TransactionType.PURCHASE, amount, "SAR", counterparty = merchant, category = category)

    @Test
    fun merchantKeyIgnoresBranchNumbersAndCase() {
        assertEquals(merchantKey("HUNGERSTATION 123"), merchantKey("Hungerstation"))
        assertEquals("amazon sa", merchantKey("AMAZON.SA"))
    }

    @Test
    fun rulesOverrideCategoryByMerchantOrMessage() {
        val rules = CategoryRules(
            byMerchant = mapOf(merchantKey("Al Fakhamah") to SpendCategory.FOOD),
            byMessage = mapOf(7L to SpendCategory.HEALTH),
        )
        assertEquals(SpendCategory.FOOD, rules.apply(1, buy("AL FAKHAMAH 02", 10.0)).category)
        assertEquals(SpendCategory.HEALTH, rules.apply(7, buy("AL FAKHAMAH", 10.0)).category)
        assertEquals(SpendCategory.OTHER, rules.apply(2, buy("Someone else", 10.0)).category)
        // Income keeps its category.
        val salary = Transaction(TransactionType.SALARY, 100.0, "SAR", category = SpendCategory.INCOME)
        assertEquals(SpendCategory.INCOME, CategoryRules(byMessage = mapOf(3L to SpendCategory.FOOD)).apply(3, salary).category)
    }

    @Test
    fun comparesCategoriesWithPreviousMonth() {
        val c = compareMonths(
            current = listOf(buy("A", 135.0, SpendCategory.FOOD), buy("B", 50.0, SpendCategory.FUEL)),
            previous = listOf(buy("A", 100.0, SpendCategory.FOOD), buy("C", 20.0, SpendCategory.TRAVEL)),
        )
        assertEquals(185.0, c.expenses, 0.001)
        assertEquals(120.0, c.previousExpenses, 0.001)
        assertEquals(54, c.percent)
        val food = c.changes.first { it.category == SpendCategory.FOOD }
        assertEquals(35, food.percent)
        assertNull(c.changes.first { it.category == SpendCategory.FUEL }.percent)
        assertEquals(0.0, c.changes.first { it.category == SpendCategory.TRAVEL }.current, 0.001)
        assertEquals(202312, previousMonth(202401))
    }

    @Test
    fun topMerchantsGroupsSpellings() {
        val top = topMerchants(
            listOf(buy("JARIR 12", 100.0), buy("Jarir", 50.0), buy("NOON", 120.0), buy("X", 5.0)),
            "SAR",
            limit = 2,
        )
        assertEquals(listOf("jarir" to 150.0, "noon" to 120.0), top.map { it.key to it.total })
        assertEquals(2, top[0].count)
    }

    @Test
    fun detectsMonthlySubscriptionButNotFrequentCoffee() {
        val entries = ArrayList<TxEntry>()
        var id = 0L
        listOf(day(2024, 1, 5), day(2024, 2, 5), day(2024, 3, 6), day(2024, 4, 5)).forEach {
            entries += TxEntry(id++, it, buy("NETFLIX.COM", 56.0, SpendCategory.SUBSCRIPTIONS))
        }
        for (d in 1..28 step 3) entries += TxEntry(id++, day(2024, 3, d), buy("STARBUCKS", 18.0, SpendCategory.FOOD))
        entries += TxEntry(id++, day(2024, 1, 10), buy("GYM", 300.0, SpendCategory.SUBSCRIPTIONS))
        entries += TxEntry(id++, day(2024, 2, 10), buy("GYM", 300.0, SpendCategory.SUBSCRIPTIONS))

        val subs = detectSubscriptions(entries, now = day(2024, 4, 20))
        assertEquals(setOf("netflix com", "gym"), subs.map { it.key }.toSet())
        val netflix = subs.first { it.key == "netflix com" }
        assertEquals(56.0, netflix.amount, 0.001)
        assertEquals(4, netflix.count)
        assertTrue(netflix.active)
        assertTrue(!subs.first { it.key == "gym" }.active)
    }

    @Test
    fun spendingPerDayAndMonth() {
        val entries = listOf(
            TxEntry(1, day(2024, 2, 1), buy("A", 10.0)),
            TxEntry(2, day(2024, 2, 1), buy("B", 5.0)),
            TxEntry(3, day(2024, 2, 29), buy("C", 7.0)),
            TxEntry(4, day(2024, 4, 3), buy("D", 1.0)),
            TxEntry(5, day(2024, 2, 3), Transaction(TransactionType.SALARY, 999.0, "SAR")),
        )
        val days = spendingByDay(entries, "SAR", 202402)
        assertEquals(29, days.size)
        assertEquals(15.0, days[0], 0.001)
        assertEquals(7.0, days[28], 0.001)
        assertEquals(listOf(202402 to 22.0, 202403 to 0.0, 202404 to 1.0), spendingByMonth(entries, "SAR"))
    }

    @Test
    fun csvQuotesFieldsAndSignsAmounts() {
        val csv = transactionsCsv(
            listOf(TxEntry(1, day(2024, 5, 1), buy("Shop, Inc", 12.5, SpendCategory.SHOPPING), "Bank", "line1\nline2")),
        )
        assertTrue(csv.startsWith("﻿"))
        val row = csv.split("\r\n")[1]
        assertTrue(row, row.startsWith("2024-05-01 12:00,شراء,تسوق,\"Shop, Inc\",-12.50,SAR,"))
        assertTrue(row.endsWith("Bank,\"line1\nline2\""))
    }
}

class CategoryRulesIgnoreTest {
    @Test
    fun ignoresMessagesAndWholeSenders() {
        val rules = CategoryRules(ignoredMessages = setOf(5L), ignoredSenders = setOf(senderRuleKey(" STC ")))
        assertTrue(rules.isIgnored(5, "AlRajhiBank"))
        assertTrue(rules.isIgnored(9, "stc"))
        assertTrue(!rules.isIgnored(9, "AlRajhiBank"))
        assertEquals(2, rules.ignoredCount)
    }
}
