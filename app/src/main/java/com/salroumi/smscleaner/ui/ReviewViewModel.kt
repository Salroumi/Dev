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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReviewItem(val sms: SmsRecord, val verdict: Verdict)

data class UiState(
    val hasPermission: Boolean = false,
    val isDefaultApp: Boolean = false,
    val loading: Boolean = false,
    val scanned: Boolean = false,
    val items: List<ReviewItem> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val filter: Category? = null,
    val message: String? = null,
    /** Set after a deletion so the user is reminded to restore their usual SMS app. */
    val showRestoreHint: Boolean = false,
    val previousAppLabel: String? = null,
) {
    val visibleItems: List<ReviewItem>
        get() = if (filter == null) items else items.filter { it.verdict.category == filter }

    val counts: Map<Category, Int>
        get() = items.groupingBy { it.verdict.category }.eachCount()
}

class ReviewViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = SmsRepository(app)
    private val analyzer = SmsAnalyzer()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun refreshStatus(hasPermission: Boolean) {
        val app = getApplication<Application>()
        _state.update {
            it.copy(
                hasPermission = hasPermission,
                isDefaultApp = DefaultSmsHelper.isDefault(app),
                previousAppLabel = DefaultSmsHelper.previousAppLabel(app),
            )
        }
    }

    fun scan() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, message = null) }
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) { loadAndAnalyze() }
                _state.update { s ->
                    s.copy(
                        loading = false,
                        scanned = true,
                        items = items,
                        selected = items.filter { it.verdict.suggestDelete }.map { it.sms.id }.toSet(),
                        message = "تمت مراجعة ${items.size} رسالة، يُقترح حذف ${items.count { it.verdict.suggestDelete }}",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, message = "تعذّرت قراءة الرسائل: ${e.message}") }
            }
        }
    }

    private fun loadAndAnalyze(): List<ReviewItem> {
        val messages = repository.loadInbox()
        val verdicts = analyzer.analyze(messages)
        return messages.map { ReviewItem(it, verdicts.getValue(it.id)) }
            .sortedWith(compareBy<ReviewItem> { it.verdict.category.ordinal }.thenByDescending { it.sms.date })
    }

    fun toggle(id: Long) = _state.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    fun setFilter(category: Category?) = _state.update { it.copy(filter = category) }

    fun selectAllVisible(select: Boolean) = _state.update { s ->
        val ids = s.visibleItems.map { it.sms.id }.toSet()
        s.copy(selected = if (select) s.selected + ids else s.selected - ids)
    }

    /** Deletes the selected messages. Call only after the user confirmed. */
    fun deleteSelected() {
        val ids = _state.value.selected
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
                        selected = emptySet(),
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
