package com.salroumi.smscleaner.analysis

import java.util.concurrent.TimeUnit

/** A received SMS, independent of Android types so it can be unit-tested. */
data class SmsRecord(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val fromContact: Boolean = false,
    val contactName: String? = null,
    /** True for messages the user sent (never suggested for deletion). */
    val sent: Boolean = false,
    val isMms: Boolean = false,
)

enum class Category(val label: String) {
    SUSPICIOUS("مشبوهة / احتيال"),
    DUPLICATE("مكررة"),
    OTP_EXPIRED("رمز تحقق منتهي"),
    OTP_RECENT("رمز تحقق حديث"),
    PROMOTION("إعلانية"),
    TRANSACTION("معاملة بنكية"),
    PERSONAL("شخصية"),
    SENT("مرسلة منك"),
    OTHER("أخرى"),
}

data class Verdict(
    val category: Category,
    /** Whether the app suggests deleting this message. The user always confirms first. */
    val suggestDelete: Boolean,
    val reasons: List<String>,
    /** 0 = safe; higher means more phishing indicators were found. */
    val riskScore: Int = 0,
    /** Amount, type and merchant for bank messages that could be read. */
    val transaction: Transaction? = null,
)

data class AnalyzerSettings(
    val otpMaxAgeHours: Long = 24,
    val suspiciousThreshold: Int = 3,
)

/**
 * Rule-based SMS reviewer. Everything runs on the device; no message leaves the phone.
 */
class SmsAnalyzer(private val settings: AnalyzerSettings = AnalyzerSettings()) {

    /**
     * Analyses [messages] and returns a verdict per message id.
     * Duplicates are detected across the list: the newest copy is kept, older copies are flagged.
     */
    fun analyze(messages: List<SmsRecord>, now: Long = System.currentTimeMillis()): Map<Long, Verdict> {
        val seen = HashSet<String>()
        val result = LinkedHashMap<Long, Verdict>()
        for (sms in messages.sortedByDescending { it.date }) {
            if (sms.sent) {
                result[sms.id] = Verdict(Category.SENT, false, listOf("رسالة أرسلتها أنت"))
                continue
            }
            val key = sms.address.filter { !it.isWhitespace() }.lowercase() + "|" + normalize(sms.body)
            val isDuplicate = !seen.add(key)
            result[sms.id] = analyzeOne(sms, now, isDuplicate)
        }
        return result
    }

    fun analyzeOne(sms: SmsRecord, now: Long, isDuplicate: Boolean = false): Verdict {
        val text = normalize(sms.body)

        val risk = phishingRisk(sms, text)
        if (risk.score >= settings.suspiciousThreshold) {
            return Verdict(
                Category.SUSPICIOUS,
                suggestDelete = true,
                reasons = risk.reasons + "لا تضغط على أي رابط ولا تشارك بياناتك",
                riskScore = risk.score,
            )
        }

        if (isDuplicate) {
            return Verdict(Category.DUPLICATE, true, listOf("نسخة مكررة من رسالة أحدث من نفس المرسل"), risk.score)
        }

        // Before the OTP check: a card's last 4 digits plus a word like "shopping" (contains "pin")
        // would otherwise look like an expired code and be suggested for deletion.
        if (!sms.fromContact) {
            TransactionParser.parse(sms.body, sms.address)?.let { tx ->
                return Verdict(
                    Category.TRANSACTION,
                    false,
                    listOf("عملية مالية: ${tx.type.label} • ${tx.category.label}"),
                    risk.score,
                    tx,
                )
            }
        }

        if (isOtp(text)) {
            val ageHours = TimeUnit.MILLISECONDS.toHours(now - sms.date)
            return if (ageHours >= settings.otpMaxAgeHours) {
                Verdict(
                    Category.OTP_EXPIRED,
                    true,
                    listOf("رمز تحقق عمره ${formatAge(ageHours)} ولم يعد صالحًا"),
                    risk.score,
                )
            } else {
                Verdict(Category.OTP_RECENT, false, listOf("رمز تحقق حديث، قد تحتاجه الآن"), risk.score)
            }
        }

        if (containsAny(text, TRANSACTION_KEYWORDS)) {
            return Verdict(Category.TRANSACTION, false, listOf("تبدو عملية مالية، يُنصح بالاحتفاظ بها"), risk.score)
        }

        if (sms.fromContact) {
            return Verdict(Category.PERSONAL, false, listOf("المرسل موجود في جهات الاتصال"), risk.score)
        }

        val promoHits = PROMOTION_KEYWORDS.count { text.contains(it) } + if (PERCENT.containsMatchIn(text)) 1 else 0
        if (promoHits >= 1) {
            return Verdict(Category.PROMOTION, true, listOf("رسالة تسويقية / عرض"), risk.score)
        }

        val reasons = if (risk.reasons.isEmpty()) listOf("لم تُصنَّف، راجعها يدويًا") else risk.reasons
        return Verdict(Category.OTHER, false, reasons, risk.score)
    }

    private data class Risk(val score: Int, val reasons: List<String>)

    private fun phishingRisk(sms: SmsRecord, text: String): Risk {
        var score = 0
        val reasons = mutableListOf<String>()
        val urls = URL.findAll(text).map { it.value }.toList()

        if (urls.isNotEmpty()) {
            score += 1
            if (urls.any { url -> SHORTENERS.any { url.contains(it) } }) {
                score += 2
                reasons += "تحتوي على رابط مختصر يخفي الوجهة الحقيقية"
            }
            if (urls.any { url -> SUSPICIOUS_TLD.containsMatchIn(url) }) {
                score += 2
                reasons += "رابط بنطاق شائع الاستخدام في الاحتيال"
            }
            if (urls.any { IP_URL.containsMatchIn(it) }) {
                score += 3
                reasons += "رابط لعنوان IP مباشر"
            }
        }

        val phishingHits = PHISHING_KEYWORDS.filter { text.contains(it) }
        if (phishingHits.isNotEmpty()) {
            score += minOf(4, phishingHits.size * 2)
            reasons += "عبارات احتيال شائعة: " + phishingHits.take(3).joinToString("، ")
        }

        // A private mobile number claiming to be a bank / official entity is a classic scam.
        val digits = sms.address.filter { it.isDigit() }
        val looksLikePersonalNumber = digits.length >= 9 && sms.address.none { it.isLetter() }
        if (looksLikePersonalNumber && !sms.fromContact && containsAny(text, OFFICIAL_KEYWORDS)) {
            score += 2
            reasons += "رقم شخصي يدّعي أنه جهة رسمية أو بنك"
        }

        if (urls.isNotEmpty() && containsAny(text, CREDENTIAL_KEYWORDS)) {
            score += 2
            reasons += "تطلب بيانات حساسة مع رابط"
        }

        return Risk(score, reasons)
    }

    private fun isOtp(text: String): Boolean =
        containsAny(text, OTP_KEYWORDS) && OTP_CODE.containsMatchIn(text)

    private fun containsAny(text: String, words: List<String>) = words.any { text.contains(it) }

    private fun formatAge(hours: Long): String =
        if (hours >= 48) "${hours / 24} يوم" else "$hours ساعة"

    companion object {
        /** Lowercase, convert Arabic/Persian digits to ASCII and unify common Arabic letter variants. */
        fun normalize(input: String): String {
            val sb = StringBuilder(input.length)
            for (c in input.lowercase()) {
                sb.append(
                    when (c) {
                        in '\u0660'..'\u0669' -> '0' + (c - '\u0660')
                        in '\u06F0'..'\u06F9' -> '0' + (c - '\u06F0')
                        'أ', 'إ', 'آ' -> 'ا'
                        'ى' -> 'ي'
                        'ة' -> 'ه'
                        'ـ' -> null // tatweel
                        in '\u064B'..'\u0652' -> null // tashkeel
                        else -> c
                    } ?: continue
                )
            }
            return sb.toString().replace(WHITESPACE, " ").trim()
        }

        private fun kw(vararg words: String) = words.map { normalize(it) }

        private val WHITESPACE = Regex("\\s+")
        private val OTP_CODE = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")
        private val PERCENT = Regex("[0-9]+ ?%")
        private val URL = Regex(
            "(https?://\\S+|www\\.\\S+|\\b[a-z0-9-]+(\\.[a-z0-9-]+)*\\.(com|net|org|info|xyz|top|click|link|ly|me|io|co|sa|icu|buzz|live|shop|online|site|gd|cc|gl)(/\\S*)?)"
        )
        private val IP_URL = Regex("(https?://)?[0-9]{1,3}(\\.[0-9]{1,3}){3}")
        private val SUSPICIOUS_TLD = Regex("\\.(xyz|top|click|link|icu|buzz|live|online|site|rest|cfd|sbs)(/|$|\\b)")
        private val SHORTENERS = listOf(
            "bit.ly", "tinyurl", "goo.gl", "t.co/", "cutt.ly", "is.gd", "rb.gy", "shorturl",
            "ow.ly", "tiny.cc", "rebrand.ly", "t.ly", "shorte.st", "s.id",
        )

        private val OTP_KEYWORDS = kw(
            "otp", "verification", "verify", "passcode", "one time", "one-time", "code", "pin",
            "رمز", "كود", "التحقق", "تحقق", "التفعيل", "كلمة المرور لمرة واحدة", "لمرة واحدة",
        )

        private val TRANSACTION_KEYWORDS = kw(
            "رصيد", "مبلغ", "شراء", "حوالة", "حواله", "سحب", "إيداع", "ايداع", "تم خصم", "بطاقة", "مدى",
            "عملية", "فاتورة", "ر.س", "ريال", "balance", "debit", "credit", "purchase", "withdraw",
            "deposit", "transfer", "amount", "card ending", "sar ",
        )

        private val PROMOTION_KEYWORDS = kw(
            "عرض", "عروض", "خصم", "تخفيض", "تخفيضات", "تنزيلات", "اشترك", "مجانا", "مجاناً", "مجاني",
            "لفترة محدودة", "تسوق", "كوبون", "كود الخصم", "لإلغاء الاشتراك", "لالغاء الاشتراك",
            "offer", "sale", "discount", "promo", "% off", "free", "subscribe", "limited time",
            "shop now", "coupon", "unsubscribe", "deal",
        )

        private val PHISHING_KEYWORDS = kw(
            "ربحت", "فزت", "جائزة", "مبروك لقد", "تم إيقاف", "تم ايقاف", "تم تعليق", "سيتم إيقاف",
            "سيتم ايقاف", "حسابك موقوف", "حدث بياناتك", "تحديث بياناتك", "تحديث البيانات",
            "اضغط على الرابط", "اضغط الرابط", "انقر على الرابط", "شحنتك معلقة", "رسوم جمركية",
            "you won", "winner", "prize", "congratulations", "suspended", "verify your account",
            "update your details", "update your account", "click the link", "claim your",
            "your parcel", "customs fee", "urgent",
        )

        private val OFFICIAL_KEYWORDS = kw(
            "بنك", "مصرف", "حسابك", "أبشر", "ابشر", "وزارة", "الراجحي", "الأهلي", "bank", "account",
        )

        private val CREDENTIAL_KEYWORDS = kw(
            "كلمة المرور", "رقم البطاقة", "الرقم السري", "بياناتك", "رمز التحقق", "password",
            "card number", "cvv", "login", "تسجيل الدخول",
        )
    }
}
