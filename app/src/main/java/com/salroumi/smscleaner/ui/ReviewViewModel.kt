package com.salroumi.smscleaner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.salroumi.smscleaner.analysis.Category
import com.salroumi.smscleaner.analysis.SmsAnalyzer
import com.salroumi.smscleaner.analysis.SmsRecord
import com.salroumi.smscleaner.analysis.Verdict
import com.salroumi.smscleaner.sms.DefaultSmsHelper
import com.salroumi.smscleaner.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class ReviewItem(val sms: SmsRecord, val verdict: Verdict) {
    /** Normalised once so text search ignores Arabic letter/digit variants. */
    val searchableBody: String = SmsAnalyzer.normalize(sms.body)
    val year: Int = Calendar.getInstance().apply { timeInMillis = sms.date }.get(Calendar.YEAR)
    private val searchableSender: String = SmsAnalyzer.normalize(sms.address + " " + sms.contactName.orEmpty())
    private val senderDigits: String = sms.address.filter { it.isDigit() }

    /** Groups "+966551234567" and "0551234567" as the same sender. */
    val senderKey: String = senderKeyOf(sms.address)

    fun matchesSender(query: String, queryDigits: String?): Boolean =
        searchableSender.contains(query) ||
            (!queryDigits.isNullOrEmpty() && senderDigits.contains(queryDigits))
}

fun senderKeyOf(address: String): String {
    val trimmed = address.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isLetter() }) return trimmed.lowercase()
    var digits = trimmed.filter { it.isDigit() }.trimStart('0')
    if (digits.startsWith("966") && digits.length > 9) digits = digits.removePrefix("966")
    return digits
}

data class SenderSummary(
    val key: String,
    val name: String,
    val address: String,
    val total: Int,
    val suggested: Int,
    val lastDate: Long,
)

fun summarizeSenders(items: List<ReviewItem>): List<SenderSummary> =
    items.groupBy { it.senderKey }.map { (key, list) ->
        val address = list.first().sms.address
        SenderSummary(
            key = key,
            name = list.firstNotNullOfOrNull { it.sms.contactName } ?: address.ifBlank { "مجهول" },
            address = address,
            total = list.size,
            suggested = list.count { it.verdict.suggestDelete },
            lastDate = list.maxOf { it.sms.date },
        )
    }.sortedWith(compareByDescending<SenderSummary> { it.total }.thenByDescending { it.lastDate })

enum class ResultTab(val label: String) {
    MESSAGES("الرسائل"),
    SENDERS("المرسلون"),
    TRANSACTIONS("العمليات المالية"),
}

data class UiState(
    val hasPermission: Boolean = false,
    val isDefaultApp: Boolean = false,
    val loading: Boolean = false,
    val scanned: Boolean = false,
    val items: List<ReviewItem> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val filter: Category? = null,
    /** Sender search text. */
    val query: String = "",
    /** Message-text search. */
    val textQuery: String = "",
    /** When set, only messages from this sender (see [senderKeyOf]) are listed. */
    val senderFilter: String? = null,
    /** When set, only messages from this calendar year are listed and selected. */
    val year: Int? = null,
    val tab: ResultTab = ResultTab.MESSAGES,
    val message: String? = null,
    /** Set after a deletion so the user is reminded to restore their usual SMS app. */
    val showRestoreHint: Boolean = false,
    val previousAppLabel: String? = null,
    /** Incremented each time the "make default SMS app" request returns. */
    val roleResults: Int = 0,
    val currentDefaultLabel: String = "",
) {
    val visibleItems: List<ReviewItem>
        get() {
            val q = SmsAnalyzer.normalize(query)
            // Phone numbers: "0551234567" should match "+966551234567", so compare digits without leading zeros.
            val qDigits = if (q.isNotEmpty() && q.all { it.isDigit() || it == '+' || it == ' ' }) {
                q.filter { it.isDigit() }.trimStart('0')
            } else {
                null
            }
            val t = SmsAnalyzer.normalize(textQuery)
            return items.filter { item ->
                (filter == null || item.verdict.category == filter) &&
                    (year == null || item.year == year) &&
                    (senderFilter == null || item.senderKey == senderFilter) &&
                    (q.isEmpty() || item.matchesSender(q, qDigits)) &&
                    (t.isEmpty() || item.searchableBody.contains(t))
            }
        }

    /** Only what is on screen gets deleted, so a filter or search never deletes hidden messages. */
    val selectedVisible: List<ReviewItem>
        get() = visibleItems.filter { it.sms.id in selected }

    /** Messages in the selected year (all messages when no year is selected). */
    val itemsInYear: List<ReviewItem>
        get() = if (year == null) items else items.filter { it.year == year }

    val counts: Map<Category, Int>
        get() = itemsInYear.groupingBy { it.verdict.category }.eachCount()

    /** Years that have messages, newest first, with message counts. */
    val years: List<Pair<Int, Int>>
        get() = items.groupingBy { it.year }.eachCount().toList().sortedByDescending { it.first }
}

class ReviewViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmsRepository(app)
    private val analyzer = SmsAnalyzer()

    private var scanJob: Job? = null
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun refreshStatus(hasPermission: Boolean) {
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                hasPermission = hasPermission,
                isDefaultApp = DefaultSmsHelper.isDefault(app),
                previousAppLabel = DefaultSmsHelper.previousAppLabel(app),
                currentDefaultLabel = DefaultSmsHelper.currentDefaultLabel(app),
            )
        }
    }

    fun onRoleResult(hasPermission: Boolean) {
        refreshStatus(hasPermission)
        _state.update { it.copy(roleResults = it.roleResults + 1) }
    }

    /**
     * Reads and analyses all messages. A [silent] refresh (e.g. when returning to the app)
     * keeps the user's selection and only pre-selects suggestions for new messages.
     */
    fun scan(silent: Boolean = false) {
        if (_state.value.loading || scanJob?.isActive == true) return
        if (silent && !_state.value.scanned) return
        _state.update { it.copy(loading = !silent, message = null) }
        scanJob = viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { loadAndAnalyze() }
                _state.update { s ->
                    val suggested = items.filter { it.verdict.suggestDelete }.map { it.sms.id }.toSet()
                    val selected = if (silent) {
                        val known = s.items.map { it.sms.id }.toSet()
                        val ids = items.map { it.sms.id }.toSet()
                        (s.selected intersect ids) + (suggested - known)
                    } else {
                        suggested
                    }
                    s.copy(
                        loading = false,
                        scanned = true,
                        items = items,
                        selected = selected,
                        message = if (silent) null
                        else "تمت مراجعة ${items.size} رسالة، يُقترح حذف ${suggested.size}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = "تعذّرت قراءة الرسائل: ${e.message}") }
            }
        }
    }

    private fun loadAndAnalyze(): List<ReviewItem> {
        val messages = repository.loadAll()
        val verdicts = analyzer.analyze(messages)
        return messages.map { ReviewItem(it, verdicts.getValue(it.id)) }
            .sortedWith(compareBy<ReviewItem> { it.verdict.category.ordinal }.thenByDescending { it.sms.date })
    }

    fun toggle(id: Long) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun setFilter(category: Category?) = _state.update { it.copy(filter = category) }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun setTextQuery(text: String) = _state.update { it.copy(textQuery = text) }

    fun setTab(tab: ResultTab) = _state.update { it.copy(tab = tab) }

    /** Shows only [key]'s messages; with [selectAll] also selects all of them. */
    fun openSender(key: String, selectAll: Boolean) = _state.update { s ->
        val ids = if (selectAll) s.itemsInYear.filter { it.senderKey == key }.map { it.sms.id } else emptyList()
        s.copy(
            tab = ResultTab.MESSAGES,
            senderFilter = key,
            filter = null,
            query = "",
            textQuery = "",
            selected = s.selected + ids,
        )
    }

    fun setYear(year: Int?) = _state.update { it.copy(year = year) }

    fun clearSenderFilter() = _state.update { it.copy(senderFilter = null) }

    fun selectAllVisible(select: Boolean) = _state.update { s ->
        val ids = s.visibleItems.map { it.sms.id }.toSet()
        s.copy(selected = if (select) s.selected + ids else s.selected - ids)
    }

    /** Deletes the selected messages. Call only after the user confirmed. */
    fun deleteSelected() {
        val ids = _state.value.selectedVisible.map { it.sms.id }.toSet()
        if (ids.isEmpty()) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val (deleted, items) = withContext(Dispatchers.IO) {
                    repository.delete(ids) to loadAndAnalyze()
                }
                _state.update { s ->
                    s.copy(
                        loading = false,
                        items = items,
                        selected = s.selected - ids,
                        showRestoreHint = deleted > 0,
                        message = if (deleted == ids.size) "تم حذف $deleted رسالة"
                        else "تم حذف $deleted من ${ids.size} رسالة فقط. تأكد أن التطبيق هو تطبيق الرسائل الافتراضي",
                    )
                }
            } catch (e: SecurityException) {
                _state.update { it.copy(loading = false, message = "يجب تعيين التطبيق كتطبيق الرسائل الافتراضي للحذف") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = "فشل الحذف: ${e.message}") }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun dismissRestoreHint() = _state.update { it.copy(showRestoreHint = false) }
}
