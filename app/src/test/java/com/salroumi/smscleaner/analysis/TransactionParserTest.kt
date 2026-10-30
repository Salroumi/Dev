package com.salroumi.smscleaner.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionParserTest {

    private fun parse(body: String): Transaction =
        TransactionParser.parse(body) ?: throw AssertionError("not parsed: $body")

    @Test
    fun multiLinePosPurchase() {
        val tx = parse(
            "شراء عبر نقاط البيع\nبطاقة: 1234;مدى-ابل باي\nمبلغ: SAR 45.50\nلدى: HUNGERSTATION\n" +
                "رصيد: SAR 1,200.75\nفي: 2024-05-01 13:22",
        )
        assertEquals(TransactionType.PURCHASE, tx.type)
        assertEquals(45.50, tx.amount, 0.001)
        assertEquals("SAR", tx.currency)
        assertEquals("HUNGERSTATION", tx.counterparty)
        assertEquals("1234", tx.account)
        assertEquals(1200.75, tx.balance!!, 0.001)
        assertEquals(SpendCategory.FOOD, tx.category)
    }

    @Test
    fun amountAfterCurrencyAndMerchantAfterMin() {
        val tx = parse("شراء نقاط بيع\nبـ 25.00 SAR\nمن HYPERPANDA\nمدى *4567\nفي 10:22 2024-05-01")
        assertEquals(TransactionType.PURCHASE, tx.type)
        assertEquals(25.0, tx.amount, 0.001)
        assertEquals("HYPERPANDA", tx.counterparty)
        assertEquals("4567", tx.account)
        assertEquals(SpendCategory.GROCERIES, tx.category)
    }

    @Test
    fun englishSingleLinePurchaseWithBalance() {
        val tx = parse("PoS Purchase of SAR 120.00 at JARIR BOOKSTORE on 01/05/24 using card *9876. Available balance SAR 3,400.00")
        assertEquals(TransactionType.PURCHASE, tx.type)
        assertEquals(120.0, tx.amount, 0.001)
        assertEquals("JARIR BOOKSTORE", tx.counterparty)
        assertEquals(3400.0, tx.balance!!, 0.001)
        assertEquals(SpendCategory.SHOPPING, tx.category)
    }

    @Test
    fun foreignCurrencyOnlinePurchase() {
        val tx = parse("شراء انترنت\nبطاقة: 4567;فيزا\nمبلغ: 15.99 USD\nلدى: NETFLIX.COM")
        assertEquals(15.99, tx.amount, 0.001)
        assertEquals("USD", tx.currency)
        assertEquals(SpendCategory.SUBSCRIPTIONS, tx.category)
    }

    @Test
    fun arabicDigitsAndRiyalWord() {
        val tx = parse("تم خصم مبلغ ١٬٢٥٠٫٥٠ ريال من بطاقتك ٣٣٢١ لدى محطة الدريس")
        assertEquals(TransactionType.PURCHASE, tx.type)
        assertEquals(1250.50, tx.amount, 0.001)
        assertEquals(SpendCategory.FUEL, tx.category)
    }

    @Test
    fun outgoingTransfer() {
        val tx = parse("حوالة صادرة: محلية\nمن: 3021\nمبلغ: SAR 500\nالى: محمد عبدالله\nرسوم: SAR 0.58")
        assertEquals(TransactionType.TRANSFER_OUT, tx.type)
        assertEquals(500.0, tx.amount, 0.001)
        assertEquals("محمد عبدالله", tx.counterparty)
        assertEquals(SpendCategory.TRANSFERS, tx.category)
    }

    @Test
    fun incomingTransferAndSalary() {
        val incoming = parse("حوالة واردة\nمبلغ: SAR 1,000.00\nمن: سارة احمد\nالى: 3021")
        assertEquals(TransactionType.TRANSFER_IN, incoming.type)
        assertEquals(Flow.IN, incoming.type.flow)
        assertEquals(1000.0, incoming.amount, 0.001)
        assertEquals("سارة احمد", incoming.counterparty)

        val salary = parse("إيداع راتب\nمبلغ: 12,500.00 ر.س\nحساب: **3021\nالرصيد: 14,020.10 ر.س")
        assertEquals(TransactionType.SALARY, salary.type)
        assertEquals(12500.0, salary.amount, 0.001)
        assertEquals(14020.10, salary.balance!!, 0.001)
        assertEquals(SpendCategory.INCOME, salary.category)
    }

    @Test
    fun withdrawalBillRefundDeclined() {
        assertEquals(TransactionType.WITHDRAWAL, parse("سحب صراف آلي\nمبلغ: SAR 300\nبطاقة: 1234").type)

        val bill = parse("سداد فاتورة\nالمفوتر: STC\nمبلغ: 230.00 SAR\nمن حساب: 3021")
        assertEquals(TransactionType.BILL, bill.type)
        assertEquals(SpendCategory.BILLS, bill.category)

        val refund = parse("Refund of SAR 89.00 from NOON.COM credited to card *1234")
        assertEquals(TransactionType.REFUND, refund.type)
        assertEquals(89.0, refund.amount, 0.001)

        val declined = parse("عملية مرفوضة: رصيد غير كاف لعملية شراء بمبلغ 75 ريال لدى AMAZON")
        assertEquals(TransactionType.DECLINED, declined.type)
        assertEquals(75.0, declined.amount, 0.001)
        assertEquals(Flow.NONE, declined.type.flow)
    }

    @Test
    fun stcPayWalletIsNotAPhoneBill() {
        val tx = parse("STC Pay: تم الدفع بمبلغ 18 ريال لدى STARBUCKS")
        assertEquals(SpendCategory.FOOD, tx.category)
    }

    @Test
    fun otpsAndOffersAreNotTransactions() {
        assertNull(TransactionParser.parse("رمز التحقق لعملية شراء بمبلغ 100 SAR لدى AMAZON هو 482913 لا تشاركه"))
        assertNull(TransactionParser.parse("عرض لفترة محدودة! خصم 50 ريال على مشترياتك من نون"))
        assertNull(TransactionParser.parse("مرحبا، كيف حالك؟"))
        assertNull(TransactionParser.parse("Your balance is SAR 1,000.00"))
    }

    @Test
    fun analyzerKeepsBankMessageThatLooksLikeOtp() {
        // "SHOPPING" contains "pin" and the card number looks like a code; it must stay a transaction.
        val sms = SmsRecord(1, "AlRajhiBank", "Purchase SAR 60.00 at MALL SHOPPING card 1234", date = 0)
        val verdict = SmsAnalyzer().analyzeOne(sms, now = 10L * 24 * 3600 * 1000)
        assertEquals(Category.TRANSACTION, verdict.category)
        assertEquals(false, verdict.suggestDelete)
        assertEquals(60.0, verdict.transaction!!.amount, 0.001)
    }
}

class TransactionSummaryTest {

    @Test
    fun totalsUseMainCurrencyAndSkipDeclined() {
        val summary = summarizeTransactions(
            listOf(
                Transaction(TransactionType.SALARY, 10000.0, "SAR", category = SpendCategory.INCOME),
                Transaction(TransactionType.PURCHASE, 50.0, "SAR", category = SpendCategory.FOOD),
                Transaction(TransactionType.PURCHASE, 150.0, "SAR", category = SpendCategory.GROCERIES),
                Transaction(TransactionType.PURCHASE, 30.0, "SAR", category = SpendCategory.FOOD),
                Transaction(TransactionType.PURCHASE, 15.99, "USD", category = SpendCategory.SUBSCRIPTIONS),
                Transaction(TransactionType.DECLINED, 999.0, "SAR"),
            ),
        )
        assertEquals("SAR", summary.currency)
        assertEquals(10000.0, summary.income, 0.001)
        assertEquals(230.0, summary.expenses, 0.001)
        assertEquals(9770.0, summary.net, 0.001)
        assertEquals(listOf(SpendCategory.GROCERIES to 150.0, SpendCategory.FOOD to 80.0), summary.byCategory)
        assertEquals(1, summary.otherCurrencyCount)
        assertEquals(1, summary.declinedCount)
    }
}
