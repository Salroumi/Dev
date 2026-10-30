package com.salroumi.smscleaner.analysis

enum class Flow { IN, OUT, NONE }

enum class TransactionType(val label: String, val flow: Flow) {
    PURCHASE("شراء", Flow.OUT),
    WITHDRAWAL("سحب نقدي", Flow.OUT),
    TRANSFER_OUT("حوالة صادرة", Flow.OUT),
    BILL("سداد فاتورة", Flow.OUT),
    FEE("رسوم بنكية", Flow.OUT),
    SALARY("راتب", Flow.IN),
    TRANSFER_IN("حوالة واردة", Flow.IN),
    DEPOSIT("إيداع", Flow.IN),
    REFUND("استرداد", Flow.IN),
    DECLINED("عملية مرفوضة", Flow.NONE),
}

/** What the money was spent on (or where it came from, for incoming money). */
enum class SpendCategory(val label: String) {
    FOOD("مطاعم ومقاهي"),
    GROCERIES("بقالة وتموينات"),
    FUEL("وقود"),
    TRANSPORT("مواصلات وسيارات"),
    SHOPPING("تسوق"),
    BILLS("فواتير واتصالات"),
    HEALTH("صحة وصيدليات"),
    SUBSCRIPTIONS("اشتراكات وترفيه"),
    TRAVEL("سفر وفنادق"),
    GOVERNMENT("خدمات حكومية"),
    CASH("سحب نقدي"),
    TRANSFERS("تحويلات"),
    FEES("رسوم بنكية"),
    INCOME("دخل"),
    OTHER("أخرى"),
}

data class Transaction(
    val type: TransactionType,
    val amount: Double,
    /** ISO-like code, e.g. "SAR". Messages without a currency are assumed to be in SAR. */
    val currency: String,
    /** Merchant for purchases, recipient/sender for transfers. */
    val counterparty: String? = null,
    /** Last digits of the card or account, when the message shows them. */
    val account: String? = null,
    val balance: Double? = null,
    val category: SpendCategory = SpendCategory.OTHER,
)

/**
 * Extracts a financial transaction (amount, type, merchant, balance…) from a bank SMS.
 * Rule-based and offline. Returns null for anything that is not clearly a completed or declined
 * transaction: OTPs for a purchase, offers that mention prices, etc.
 */
object TransactionParser {

    fun parse(body: String): Transaction? {
        val text = SmsAnalyzer.normalize(prepare(body))
        if (hasAny(text, OTP_WORDS)) return null
        if (hasAny(text, PROMO_WORDS) && ACCOUNT_REF.find(text) == null) return null

        val type = detectType(text) ?: return null
        val money = findMoney(text)
        val main = money.firstOrNull { it.label == Label.NONE }
            ?: money.firstOrNull { it.label == Label.FEE && type == TransactionType.FEE }
            ?: return null
        if (main.amount <= 0.0) return null

        val counterparty = findCounterparty(prepare(body), type)
        return Transaction(
            type = type,
            amount = main.amount,
            currency = main.currency,
            counterparty = counterparty,
            account = ACCOUNT_REF.find(text)?.groupValues?.get(1),
            balance = money.firstOrNull { it.label == Label.BALANCE }?.amount,
            category = categorize(type, counterparty, text),
        )
    }

    /** Converts Arabic digits and separators so amounts like "١٬٢٥٠٫٥٠" parse. */
    private fun prepare(body: String): String {
        val sb = StringBuilder(body.length)
        for (c in body) {
            sb.append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    '٫' -> '.'
                    '٬' -> ','
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    private fun detectType(text: String): TransactionType? = when {
        hasAny(text, DECLINED_WORDS) -> TransactionType.DECLINED
        hasAny(text, REFUND_WORDS) -> TransactionType.REFUND
        hasAny(text, SALARY_WORDS) -> TransactionType.SALARY
        hasAny(text, TRANSFER_IN_WORDS) -> TransactionType.TRANSFER_IN
        hasAny(text, TRANSFER_OUT_WORDS) -> TransactionType.TRANSFER_OUT
        hasAny(text, TRANSFER_WORDS) ->
            if (hasAny(text, CREDIT_WORDS)) TransactionType.TRANSFER_IN else TransactionType.TRANSFER_OUT
        hasAny(text, WITHDRAWAL_WORDS) -> TransactionType.WITHDRAWAL
        hasAny(text, BILL_WORDS) -> TransactionType.BILL
        hasAny(text, PURCHASE_WORDS) -> TransactionType.PURCHASE
        hasAny(text, FEE_WORDS) -> TransactionType.FEE
        hasAny(text, CREDIT_WORDS) -> TransactionType.DEPOSIT
        else -> null
    }

    private enum class Label { NONE, BALANCE, FEE }

    private data class Money(val range: IntRange, val amount: Double, val currency: String, val label: Label)

    private fun findMoney(text: String): List<Money> {
        val found = ArrayList<Money>()
        fun add(range: IntRange, number: String, currency: String?) {
            // "USD 20 SAR 75" also matches "20 SAR"; the first reading of each piece of text wins.
            if (found.any { it.range.first <= range.last && range.first <= it.range.last }) return
            val amount = number.replace(",", "").toDoubleOrNull() ?: return
            // The nearest label before the amount on the same line says whether it is a balance or a fee.
            val lineStart = text.lastIndexOf('\n', range.first - 1) + 1
            val before = text.substring(maxOf(lineStart, range.first - 40), range.first)
            val nearest = listOf(
                Label.BALANCE to lastIndexOfAny(before, BALANCE_WORDS),
                Label.FEE to lastIndexOfAny(before, FEE_WORDS),
                Label.NONE to lastIndexOfAny(before, AMOUNT_WORDS),
            ).maxBy { it.second }
            val label = if (nearest.second < 0) Label.NONE else nearest.first
            found += Money(range, amount, currency?.let(::currencyCode) ?: "SAR", label)
        }
        CUR_BEFORE.findAll(text).forEach { add(it.range, it.groupValues[2], it.groupValues[1]) }
        CUR_AFTER.findAll(text).forEach { add(it.range, it.groupValues[1], it.groupValues[2]) }
        if (found.none { it.label == Label.NONE }) {
            AMOUNT_LABEL.findAll(text).forEach { add(it.groups[1]!!.range, it.groupValues[1], null) }
        }
        return found.sortedBy { it.range.first }
    }

    private fun lastIndexOfAny(text: String, words: List<Regex>): Int =
        words.maxOf { w -> w.findAll(text).lastOrNull()?.range?.first ?: -1 }

    private fun currencyCode(raw: String): String {
        val c = raw.lowercase().replace(" ", "")
        return when (c) {
            "sar", "sr", "ر.س", "رس", "ريال", "ريالا", "ريالات" -> "SAR"
            "usd", "$", "دولار" -> "USD"
            "aed", "درهم" -> "AED"
            "eur", "€", "يورو" -> "EUR"
            "gbp", "£", "جنيه استرليني" -> "GBP"
            "kwd", "د.ك" -> "KWD"
            "bhd", "د.ب" -> "BHD"
            "qar", "ر.ق" -> "QAR"
            "omr", "ر.ع" -> "OMR"
            "egp", "جنيه" -> "EGP"
            "jod", "دينار" -> "JOD"
            else -> c.uppercase()
        }
    }

    /** Merchant / other party, read from the original text so its case and spelling are kept. */
    private fun findCounterparty(body: String, type: TransactionType): String? {
        val labels = when (type) {
            TransactionType.TRANSFER_OUT -> TO_LABELS
            TransactionType.TRANSFER_IN, TransactionType.SALARY, TransactionType.DEPOSIT -> FROM_LABELS
            else -> MERCHANT_LABELS
        }
        for (label in labels) {
            for (match in label.findAll(body)) {
                val value = cleanParty(match.groupValues[1]) ?: continue
                return value
            }
        }
        return null
    }

    private fun cleanParty(raw: String): String? {
        var v = raw.trim().trimStart('*', ':', '-', ' ')
        PARTY_END.find(v)?.let { v = v.substring(0, it.range.first) }
        v = v.trim().trimEnd('.', ',', '،', ';', '-', ':').trim()
        if (v.count { it.isLetter() } < 2) return null
        if (PARTY_REJECT.containsMatchIn(SmsAnalyzer.normalize(v))) return null
        return v.take(40)
    }

    private fun categorize(type: TransactionType, counterparty: String?, text: String): SpendCategory {
        when (type) {
            TransactionType.SALARY, TransactionType.TRANSFER_IN, TransactionType.DEPOSIT,
            TransactionType.REFUND -> return SpendCategory.INCOME
            TransactionType.WITHDRAWAL -> return SpendCategory.CASH
            TransactionType.TRANSFER_OUT -> return SpendCategory.TRANSFERS
            TransactionType.FEE -> return SpendCategory.FEES
            else -> Unit
        }
        // The merchant name decides first; the whole message is the fallback.
        // The wallet/bank name ("stc pay") is not the merchant, so it must not count as a phone bill.
        val haystacks = listOfNotNull(counterparty?.let { SmsAnalyzer.normalize(it) }, text)
            .map { it.replace("stc pay", "").replace("stc bank", "") }
        for (haystack in haystacks) {
            for ((category, words) in MERCHANT_WORDS) {
                if (hasAny(haystack, words)) return category
            }
        }
        return if (type == TransactionType.BILL) SpendCategory.BILLS else SpendCategory.OTHER
    }

    /**
     * Latin words match whole words only ("pos" must not match "deposit");
     * Arabic words match anywhere, since prefixes like "و" and "ال" attach to them.
     */
    private fun hasAny(text: String, words: List<Regex>) = words.any { it.containsMatchIn(text) }

    private fun words(vararg list: String): List<Regex> = list.map { w ->
        val n = SmsAnalyzer.normalize(w)
        val escaped = Regex.escape(n)
        if (n.any { it in 'a'..'z' }) Regex("(?<![a-z])$escaped(?![a-z])") else Regex(escaped)
    }

    /** Like [words], but short Arabic brand names ("نون", "زين") must be whole words too. */
    private fun brands(vararg list: String): List<Regex> = list.map { w ->
        val n = SmsAnalyzer.normalize(w)
        val escaped = Regex.escape(n)
        if (n.any { it in 'a'..'z' }) Regex("(?<![a-z])$escaped(?![a-z])")
        else Regex("(?<!\\p{L})(?:[وبلف]?(?:ال)?)$escaped(?!\\p{L})")
    }

    private const val NUM = "([0-9]{1,3}(?:,[0-9]{3})+(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?)"
    private const val CUR =
        "(sar|sr|usd|aed|eur|gbp|kwd|bhd|qar|omr|egp|jod|ر\\.س|ر س|رس|ريالات|ريالا|ريال|دولار|درهم|يورو|دينار|جنيه|د\\.ك|د\\.ب|ر\\.ق|ر\\.ع|\\$|€|£)"
    private val CUR_BEFORE = Regex("(?<![a-z])$CUR\\s*[:.]?\\s*$NUM(?![0-9])")
    private val CUR_AFTER = Regex("(?<![0-9.,])$NUM\\s*$CUR(?![a-z])")
    private val AMOUNT_LABEL = Regex("(?:مبلغ|بمبلغ|المبلغ|amount|amt)\\s*[:]?\\s*$NUM(?![0-9])")
    private val ACCOUNT_REF = Regex(
        "(?:بطاقه|بطاقتك|حساب|حسابك|مدي|فيزا|ماستر|ائتمانيه|card|account|acc|a/c|visa|mada|mastercard|ending|من)" +
            "[^0-9\\n]{0,15}?[*x#]*([0-9]{4})(?![0-9])"
    )

    private const val PARTY = "\\s*[:：]?\\s*([^\\n]+)"
    private fun label(vararg names: String) =
        Regex(
            "(?:^|(?<=[\\s(]))(?:${names.joinToString("|")})(?!\\p{L})$PARTY",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
        )

    private val MERCHANT_LABELS = listOf(
        label("لدى", "لدي", "التاجر", "المتجر", "merchant", "at"),
        label("عند", "في متجر", "in"),
        label("من", "from"),
        label("الى", "إلى", "to", "المفوتر", "biller"),
    )
    private val TO_LABELS = listOf(
        label("الى", "إلى", "إلي", "الي", "للمستفيد", "المستفيد", "to", "beneficiary"),
    )
    private val FROM_LABELS = listOf(
        label("من", "المرسل", "from", "sender", "by"),
    )
    private val PARTY_END = Regex(
        "(\\s(on|في|بتاريخ|بطاقة|بطاقه|بطاقتك|card|using|via|with|from|was|is|balance|رصيد|الرصيد|المتاح|" +
            "مبلغ|بمبلغ|amount|من|حساب|حسابك|ref|رقم)(?!\\p{L})|" +
            "[,;،|]|\\.\\s|\\s[0-9]{1,2}[:/-][0-9]|\\s{2,})",
        RegexOption.IGNORE_CASE,
    )
    private val PARTY_REJECT = Regex(
        "^(حساب|حسابك|بطاقه|بطاقتك|رصيد|your|account|card|the|a |بنك|مبلغ|amount|sar|ر\\.س|ريال)"
    )

    private val OTP_WORDS = words(
        "رمز التحقق", "رمز تحقق", "كود التحقق", "رمز التفعيل", "رمز الدخول", "كلمة المرور لمرة واحدة",
        "رمز لمرة واحدة", "لا تشارك", "otp", "verification code", "one time password", "one-time password",
        "passcode", "activation code", "do not share",
    )
    private val PROMO_WORDS = words(
        "عرض", "عروض", "كوبون", "لفترة محدودة", "تخفيضات", "استمتع", "offer", "promo", "coupon",
        "limited time", "shop now", "%",
    )
    private val DECLINED_WORDS = words(
        "مرفوضة", "مرفوض", "تم رفض", "رفض العملية", "لم تتم", "غير ناجحة", "رصيد غير كاف", "الرصيد غير كاف",
        "declined", "insufficient", "unsuccessful", "failed", "rejected",
    )
    private val REFUND_WORDS = words(
        "استرداد", "مسترد", "مستردة", "استرجاع", "إرجاع مبلغ", "عكس عملية", "refund", "refunded", "reversal",
        "reversed", "cashback",
    )
    private val SALARY_WORDS = words("راتب", "الراتب", "رواتب", "salary", "payroll")
    private val TRANSFER_IN_WORDS = words(
        "حوالة واردة", "حواله وارده", "تحويل وارد", "حوالة مستلمة", "استلام حوالة", "إيداع حوالة",
        "incoming transfer", "transfer received", "received transfer", "you received", "inward transfer",
    )
    private val TRANSFER_OUT_WORDS = words(
        "حوالة صادرة", "تحويل صادر", "حوالة داخلية صادرة", "حوالة دولية", "تحويل الى", "تحويل إلى",
        "outgoing transfer", "outward transfer", "transfer to", "you sent", "sent to",
    )
    private val TRANSFER_WORDS = words("حوالة", "حواله", "تحويل", "transfer", "remittance")
    private val CREDIT_WORDS = words(
        "إيداع", "ايداع", "واردة", "وارد", "تمت إضافة", "تم إضافة", "اضافة مبلغ", "إضافة مبلغ", "أضيف",
        "credited", "deposit", "deposited", "incoming", "received",
    )
    private val WITHDRAWAL_WORDS = words("سحب", "صراف", "atm", "withdrawal", "cash withdrawal")
    private val BILL_WORDS = words(
        "سداد", "فاتورة", "مدفوعات حكومية", "sadad", "bill payment", "bill", "government payment",
    )
    private val PURCHASE_WORDS = words(
        "شراء", "مشتريات", "نقاط البيع", "نقاط بيع", "دفع", "تم خصم", "خصم مبلغ", "apple pay", "mada pay",
        "purchase", "pos", "spent", "payment", "debited", "debit", "e-commerce", "online",
    )
    private val FEE_WORDS = words("رسوم", "عمولة", "fee", "fees", "commission", "charges")
    private val AMOUNT_WORDS = words("مبلغ", "بمبلغ", "قيمة", "amount", "amt", "purchase", "شراء")
    private val BALANCE_WORDS = words(
        "رصيد", "الرصيد", "المتاح", "المتبقي", "الحد", "balance", "bal", "avail", "available", "limit",
    )

    private val MERCHANT_WORDS: List<Pair<SpendCategory, List<Regex>>> = listOf(
        SpendCategory.FUEL to brands(
            "aldrees", "الدريس", "sasco", "ساسكو", "petromin", "بترومين", "naft", "نفط", "محطة", "محطه",
            "وقود", "بنزين", "fuel", "petrol", "gas station", "station",
        ),
        SpendCategory.GROCERIES to brands(
            "panda", "hyperpanda", "بنده", "هايبر بنده", "danube", "الدانوب", "tamimi", "التميمي", "othaim", "العثيم", "carrefour",
            "كارفور", "lulu", "لولو", "nana", "bindawood", "بن داود", "farm superstore", "المزرعة",
            "سوبرماركت", "سوبر ماركت", "هايبر", "تموينات", "بقالة", "supermarket", "hypermarket", "grocery",
            "market", "baqala",
        ),
        SpendCategory.FOOD to brands(
            "hungerstation", "هنقرستيشن", "jahez", "جاهز", "mrsool", "مرسول", "toyou", "the chefz", "keeta",
            "starbucks", "ستاربكس", "mcdonald", "ماكدونالدز", "kfc", "albaik", "البيك", "kudu", "كودو",
            "herfy", "هرفي", "burger", "pizza", "بيتزا", "shawarma", "شاورما", "dunkin", "barns", "بارنز",
            "tim hortons", "half million", "dr.cafe", "cafe", "coffee", "restaurant", "مطعم", "مطاعم",
            "كافيه", "قهوة", "كوفي", "مخبز", "bakery", "حلويات", "subway", "domino", "shake shack",
        ),
        SpendCategory.TRANSPORT to brands(
            "uber", "أوبر", "careem", "كريم", "bolt", "jeeny", "جيني", "saptco", "سابتكو", "riyadh metro",
            "parking", "مواقف", "تأجير سيارات", "rent a car", "yelo", "budget", "car wash", "مغسلة",
            "قطع غيار", "tire", "كفرات", "صيانة سيارات",
        ),
        SpendCategory.HEALTH to brands(
            "pharmacy", "صيدلية", "صيدليه", "nahdi", "النهدي", "aldawaa", "الدواء", "whites", "hospital",
            "مستشفى", "clinic", "عيادة", "عيادات", "مجمع طبي", "medical", "dental", "أسنان", "lab", "مختبر",
        ),
        SpendCategory.BILLS to brands(
            "stc", "mobily", "موبايلي", "zain", "زين", "virgin mobile", "salam", "سلام موبايل", "lebara",
            "كهرباء", "الكهرباء", "electricity", "sec", "مياه", "المياه", "water", "nwc", "اتصالات",
            "internet", "انترنت", "فاتورة",
        ),
        SpendCategory.SUBSCRIPTIONS to brands(
            "netflix", "نتفلكس", "shahid", "شاهد", "spotify", "anghami", "أنغامي", "osn", "apple.com",
            "itunes", "youtube", "playstation", "xbox", "steam", "disney", "amazon prime",
            "google play", "google one", "chatgpt", "openai", "icloud", "microsoft", "adobe", "tod", "starzplay", "gym", "نادي",
            "fitness time", "سينما", "cinema", "vox", "muvi",
        ),
        SpendCategory.TRAVEL to brands(
            "flynas", "طيران ناس", "saudia", "السعودية للطيران", "flyadeal", "طيران أديل", "emirates",
            "qatar airways", "airline", "airways", "airport", "مطار", "طيران", "hotel", "فندق", "booking",
            "airbnb", "almosafer", "المسافر", "agoda", "expedia", "marriott", "hilton", "سفر", "travel",
        ),
        SpendCategory.SHOPPING to brands(
            "amazon", "أمازون", "noon", "نون", "shein", "شي ان", "namshi", "نمشي", "jarir", "جرير",
            "extra", "اكسترا", "ikea", "ايكيا", "centrepoint", "سنتربوينت", "h&m", "zara", "زارا",
            "aliexpress", "temu", "trendyol", "max fashion", "redtag", "ريد تاغ", "saco", "ساكو", "home box",
            "mall", "مول", "store", "متجر", "ملابس", "عطور", "perfume", "abdul samad", "عبدالصمد",
            "سلة", "salla", "zid",
        ),
        SpendCategory.GOVERNMENT to brands(
            "absher", "أبشر", "ابشر", "moi", "وزارة", "ministry", "منصة", "ناجز", "najiz", "مرور", "traffic",
            "جوازات", "passport", "بلدية", "balady", "بلدي", "مخالفة", "مخالفات", "تأشيرة", "visa fee", "zatca",
        ),
    )
}
