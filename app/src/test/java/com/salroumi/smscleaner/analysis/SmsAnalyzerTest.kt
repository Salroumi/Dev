package com.salroumi.smscleaner.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAnalyzerTest {

    private val analyzer = SmsAnalyzer()
    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private fun verdict(body: String, address: String = "BANK", ageHours: Long = 1, contact: Boolean = false) =
        analyzer.analyzeOne(SmsRecord(1, address, body, now - ageHours * hour, contact), now)

    @Test
    fun oldOtpIsSuggestedForDeletion() {
        val v = verdict("رمز التحقق الخاص بك هو ٤٨٢٩١٣ لا تشاركه مع أحد", ageHours = 30)
        assertEquals(Category.OTP_EXPIRED, v.category)
        assertTrue(v.suggestDelete)
    }

    @Test
    fun recentOtpIsKept() {
        val v = verdict("Your verification code is 482913", ageHours = 1)
        assertEquals(Category.OTP_RECENT, v.category)
        assertFalse(v.suggestDelete)
    }

    @Test
    fun bankTransactionIsKept() {
        val v = verdict("شراء عبر مدى بمبلغ 120.50 ر.س من بطاقة 1234 الرصيد المتبقي 900")
        assertEquals(Category.TRANSACTION, v.category)
        assertFalse(v.suggestDelete)
    }

    @Test
    fun promotionIsSuggestedForDeletion() {
        val v = verdict("عروض الصيف! خصم 50% على جميع المنتجات لفترة محدودة", address = "STORE")
        assertEquals(Category.PROMOTION, v.category)
        assertTrue(v.suggestDelete)
    }

    @Test
    fun phishingWithShortLinkIsSuspicious() {
        val v = verdict("تم إيقاف حسابك البنكي، حدث بياناتك عبر الرابط bit.ly/abc123", address = "+966512345678")
        assertEquals(Category.SUSPICIOUS, v.category)
        assertTrue(v.suggestDelete)
        assertTrue(v.riskScore >= 3)
    }

    @Test
    fun parcelScamIsSuspicious() {
        val v = verdict("Your parcel is on hold, pay customs fee at https://post-delivery.xyz/pay", address = "0551234567")
        assertEquals(Category.SUSPICIOUS, v.category)
    }

    @Test
    fun contactMessageIsKept() {
        val v = verdict("هلا، متى توصل؟", address = "0551234567", contact = true)
        assertEquals(Category.PERSONAL, v.category)
        assertFalse(v.suggestDelete)
    }

    @Test
    fun olderDuplicateIsFlaggedAndNewestKept() {
        val messages = listOf(
            SmsRecord(1, "STC", "باقتك تنتهي غدا", now - 5 * hour),
            SmsRecord(2, "STC", "باقتك تنتهي غدا", now - 1 * hour),
        )
        val result = analyzer.analyze(messages, now)
        assertEquals(Category.DUPLICATE, result.getValue(1).category)
        assertTrue(result.getValue(1).suggestDelete)
        assertFalse(result.getValue(2).category == Category.DUPLICATE)
    }

    @Test
    fun normalizeConvertsArabicDigitsAndLetters() {
        assertEquals("رمز 1234 ابشر", SmsAnalyzer.normalize("رمز ١٢٣٤   أبشر"))
    }
}
