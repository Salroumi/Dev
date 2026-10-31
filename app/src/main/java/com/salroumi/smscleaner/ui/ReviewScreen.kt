package com.salroumi.smscleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salroumi.smscleaner.BuildConfig
import com.salroumi.smscleaner.analysis.Category
import com.salroumi.smscleaner.analysis.SmsAnalyzer
import com.salroumi.smscleaner.analysis.SpendCategory
import com.salroumi.smscleaner.analysis.TxEntry
import java.text.DateFormat
import java.util.Date

class ReviewActions(
    val onGrantPermission: () -> Unit,
    val onScan: () -> Unit,
    val onToggle: (Long) -> Unit,
    val onFilter: (Category?) -> Unit,
    val onQuery: (String) -> Unit,
    val onTextQuery: (String) -> Unit,
    val onTab: (ResultTab) -> Unit,
    val onOpenSender: (key: String, selectAll: Boolean) -> Unit,
    val onClearSender: () -> Unit,
    val onYear: (Int?) -> Unit,
    val onSelectAllVisible: (Boolean) -> Unit,
    val onRequestDefault: () -> Unit,
    val onDelete: () -> Unit,
    val onRestoreDefault: () -> Unit,
    val onDismissRestore: () -> Unit,
    val onOpenAppSettings: () -> Unit,
    val onMessageShown: () -> Unit,
    val onSetCategory: (id: Long, counterparty: String?, category: SpendCategory, forMerchant: Boolean) -> Unit,
    val onExport: (fileName: String, entries: List<TxEntry>) -> Unit,
    val onIgnoreTransaction: (id: Long, address: String, wholeSender: Boolean) -> Unit,
    val onRestoreIgnored: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(state: UiState, actions: ReviewActions) {
    val snackbar = remember { SnackbarHostState() }
    var showConfirm by rememberSaveable { mutableStateOf(false) }
    var showNeedDefault by rememberSaveable { mutableStateOf(false) }
    var awaitingRole by rememberSaveable { mutableStateOf(false) }
    var showRoleDenied by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            actions.onMessageShown()
        }
    }
    // After the user made this app the default, continue straight to the confirmation step.
    LaunchedEffect(state.roleResults) {
        if (awaitingRole && state.roleResults > 0) {
            awaitingRole = false
            if (state.isDefaultApp) showConfirm = true else showRoleDenied = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("منظّف الرسائل", style = MaterialTheme.typography.titleLarge)
                            if (state.scanned) {
                                Text(
                                    state.tab.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    actions = {
                        if (state.hasPermission) {
                            IconButton(onClick = actions.onScan, enabled = !state.loading) {
                                Icon(Icons.Default.Refresh, contentDescription = "إعادة الفحص")
                            }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                // The delete bar only appears once something is selected, so it never covers the list for nothing.
                if (state.tab == ResultTab.MESSAGES && state.selectedVisible.isNotEmpty()) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${state.selectedVisible.size} محددة",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                DgaButton(
                                    text = "حذف المحدد",
                                    icon = Icons.Default.Delete,
                                    destructive = true,
                                    enabled = !state.loading,
                                    onClick = {
                                        if (state.isDefaultApp) showConfirm = true else showNeedDefault = true
                                    },
                                )
                            }
                        }
                    }
                }
                if (state.hasPermission && state.scanned) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AppNavigationBar(state.tab, actions.onTab)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.hasPermission -> PermissionPrompt(actions.onGrantPermission)
                !state.scanned && !state.loading -> StartPrompt(actions.onScan)
                else -> Results(
                    state,
                    actions,
                    onDeleteSender = { key ->
                        actions.onOpenSender(key, true)
                        if (state.isDefaultApp) showConfirm = true else showNeedDefault = true
                    },
                )
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (showNeedDefault) {
        AlertDialog(
            onDismissRequest = { showNeedDefault = false },
            title = { Text("مطلوب إذن الحذف") },
            text = {
                Text(
                    "نظام أندرويد لا يسمح بحذف الرسائل إلا لتطبيق الرسائل الافتراضي.\n\n" +
                        "سيُطلب منك تعيين هذا التطبيق كافتراضي مؤقتًا، وبعد الحذف يمكنك إرجاع تطبيقك المعتاد.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showNeedDefault = false
                    awaitingRole = true
                    actions.onRequestDefault()
                }) { Text("متابعة") }
            },
            dismissButton = { TextButton(onClick = { showNeedDefault = false }) { Text("إلغاء") } },
        )
    }

    if (showRoleDenied) {
        AlertDialog(
            onDismissRequest = { showRoleDenied = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("لم يتم تعيين التطبيق كافتراضي") },
            text = {
                Text(
                    "غالبًا منع أندرويد ذلك لأن التطبيق مثبّت من خارج المتجر. لحلها:\n\n" +
                        "١. اضغط «فتح الإعدادات» بالأسفل\n" +
                        "٢. اضغط النقاط الثلاث ⋮ أعلى الشاشة\n" +
                        "٣. اختر «السماح بالإعدادات المقيّدة» وأكّد بالبصمة\n" +
                        "٤. ارجع هنا واضغط «حذف المحدد» مرة أخرى\n\n" +
                        "تطبيق الرسائل الافتراضي حاليًا حسب النظام: ${state.currentDefaultLabel}",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRoleDenied = false
                    actions.onOpenAppSettings()
                }) { Text("فتح الإعدادات") }
            },
            dismissButton = { TextButton(onClick = { showRoleDenied = false }) { Text("إغلاق") } },
        )
    }

    if (showConfirm) {
        val selectedItems = state.selectedVisible
        val breakdown = selectedItems.groupingBy { it.verdict.category }.eachCount()
            .entries.joinToString("\n") { "• ${it.key.label}: ${it.value}" }
        val senders = selectedItems.groupingBy { it.sms.contactName ?: it.sms.address.ifBlank { "مجهول" } }.eachCount()
            .entries.sortedByDescending { it.value }
        val senderText = senders.take(5).joinToString("\n") { "• ${it.key}: ${it.value}" } +
            if (senders.size > 5) "\n• و${senders.size - 5} مرسلين آخرين" else ""
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("تأكيد الحذف") },
            text = {
                Text(
                    "سيتم حذف ${selectedItems.size} رسالة نهائيًا ولا يمكن استرجاعها.\n\n" +
                        "حسب التصنيف:\n$breakdown\n\nحسب المرسل:\n$senderText",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    actions.onDelete()
                }) { Text("نعم، احذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    CenteredMessage(
        icon = Icons.Default.Lock,
        title = "مرحبًا بك",
        body = "يحتاج التطبيق إذن قراءة الرسائل لمراجعتها وتصنيفها. تتم المراجعة على جهازك فقط ولا تُرسل أي رسالة لأي جهة.",
        button = "منح الإذن",
        onClick = onGrant,
    )
}

@Composable
private fun StartPrompt(onScan: () -> Unit) {
    CenteredMessage(
        icon = Icons.Default.Search,
        title = "جاهز للفحص",
        body = "سيتم فحص الرسائل الواردة وتحديد: الرسائل المشبوهة، المكررة، رموز التحقق القديمة، والإعلانات، " +
            "واستخراج العمليات المالية من رسائل البنوك وتصنيفها. لن يُحذف شيء قبل تأكيدك.",
        button = "ابدأ الفحص",
        onClick = onScan,
    )
}

@Composable
private fun CenteredMessage(icon: ImageVector, title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FeaturedIcon(icon)
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalAppColors.current.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        DgaButton(text = button, onClick = onClick, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(
            "🔒 كل الفحص يتم على جهازك فقط",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Results(state: UiState, actions: ReviewActions, onDeleteSender: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        when (state.tab) {
            ResultTab.MESSAGES -> MessagesList(state, actions)
            ResultTab.SENDERS -> SendersList(state, actions, onDeleteSender)
            ResultTab.TRANSACTIONS -> TransactionsList(state.items, state.categoryRules, actions)
        }
    }
}

@Composable
private fun SendersList(state: UiState, actions: ReviewActions, onDeleteSender: (String) -> Unit) {
    val senders = remember(state.items, state.year) { summarizeSenders(state.itemsInYear) }
    var search by rememberSaveable { mutableStateOf("") }
    val q = SmsAnalyzer.normalize(search)
    val shown = if (q.isEmpty()) senders else senders.filter {
        SmsAnalyzer.normalize(it.name + " " + it.address).contains(q)
    }
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ابحث عن مرسل") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        item { YearChips(state, actions) }
        item {
            Text(
                "${senders.size} مرسل" + (state.year?.let { " في $it" } ?: "") + " • مرتبون حسب عدد الرسائل",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(shown, key = { it.key }) { sender ->
            DgaCard(
                modifier = Modifier.fillMaxWidth().clickable { actions.onOpenSender(sender.key, false) },
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sender.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (sender.name != sender.address && sender.address.isNotBlank()) {
                            Text(
                                sender.address,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "${sender.total} رسالة" +
                                if (sender.suggested > 0) " • ${sender.suggested} مقترح حذفها" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    DgaTag(sender.total.toString(), TagKind.BRAND)
                    IconButton(onClick = { onDeleteSender(sender.key) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف كل رسائل ${sender.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Year filter shared by the messages and senders tabs. */
@Composable
private fun YearChips(state: UiState, actions: ReviewActions) {
    val years = state.years
    if (years.size < 2) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.year == null,
                onClick = { actions.onYear(null) },
                label = { Text("كل السنوات") },
            )
        }
        items(years, key = { it.first }) { (year, count) ->
            FilterChip(
                selected = state.year == year,
                onClick = { actions.onYear(if (state.year == year) null else year) },
                label = { Text("$year ($count)") },
            )
        }
    }
}

@Composable
private fun MessagesList(state: UiState, actions: ReviewActions) {
    val counts = state.counts
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.showRestoreHint && state.isDefaultApp) {
            item { RestoreCard(state.previousAppLabel, actions) }
        }
        item { ScanSummary(state) }
        state.senderFilter?.let { key ->
            item {
                val name = state.items.firstOrNull { it.senderKey == key }
                    ?.let { it.sms.contactName ?: it.sms.address } ?: key
                FilterChip(
                    selected = true,
                    onClick = actions.onClearSender,
                    label = { Text("رسائل: $name") },
                    trailingIcon = { Icon(Icons.Default.Clear, contentDescription = "إلغاء") },
                )
            }
        }
        item { SearchBox(state, actions) }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.filter == null,
                        onClick = { actions.onFilter(null) },
                        label = { Text("الكل (${state.itemsInYear.size})") },
                    )
                }
                items(Category.entries.filter { (counts[it] ?: 0) > 0 }) { category ->
                    FilterChip(
                        selected = state.filter == category,
                        onClick = { actions.onFilter(category) },
                        label = { Text("${category.label} (${counts[category]})") },
                    )
                }
            }
        }
        item { YearChips(state, actions) }
        item {
            val visible = state.visibleItems
            val allSelected = visible.isNotEmpty() && visible.all { it.sms.id in state.selected }
            Row(
                Modifier.fillMaxWidth().clickable { actions.onSelectAllVisible(!allSelected) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allSelected, onCheckedChange = { actions.onSelectAllVisible(it) })
                Text("تحديد الكل في هذه القائمة (${visible.size})", style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (state.visibleItems.isEmpty()) {
            item {
                Column(Modifier.padding(24.dp)) {
                    Text("لا توجد رسائل هنا ✨")
                    val filter = state.filter
                    if (filter != null) {
                        Text("البحث يتم داخل تصنيف «${filter.label}» فقط", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { actions.onFilter(null) }) { Text("ابحث في كل التصنيفات") }
                    }
                    val year = state.year
                    if (year != null) {
                        Text("البحث يتم في رسائل سنة $year فقط", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { actions.onYear(null) }) { Text("ابحث في كل السنوات") }
                    }
                }
            }
        }
        items(state.visibleItems, key = { it.sms.id }) { item ->
            MessageCard(item, selected = item.sms.id in state.selected, onToggle = { actions.onToggle(item.sms.id) })
        }
    }
}

/** Scan result at the top of the messages list: what was read and how much can be cleaned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanSummary(state: UiState) {
    val all = state.items
    val suggested = all.count { it.verdict.suggestDelete }
    val suspicious = all.count { it.verdict.category == Category.SUSPICIOUS }
    DgaCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("تم فحص ${all.size} رسالة", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DgaTag("مقترح حذفها $suggested", TagKind.WARNING)
                if (suspicious > 0) DgaTag("مشبوهة $suspicious", TagKind.ERROR)
                DgaTag("تُحفظ ${all.size - suggested}", TagKind.SUCCESS)
            }
            Spacer(Modifier.height(8.dp))
            val received = all.count { !it.sms.sent && !it.sms.isMms }
            val sent = all.count { it.sms.sent && !it.sms.isMms }
            val mms = all.count { it.sms.isMms }
            Text(
                "واردة $received • مرسلة $sent • وسائط $mms • الإصدار ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One search field; the switch underneath chooses whether it searches senders or message text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBox(state: UiState, actions: ReviewActions) {
    var byText by rememberSaveable { mutableStateOf(state.textQuery.isNotEmpty() && state.query.isEmpty()) }
    val value = if (byText) state.textQuery else state.query
    val onChange = if (byText) actions.onTextQuery else actions.onQuery
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            placeholder = { Text(if (byText) "ابحث عن نص داخل الرسائل" else "ابحث باسم المرسل أو رقمه") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                    }
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(false to "المرسل", true to "نص الرسالة").forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = byText == mode,
                    onClick = {
                        if (byText != mode) {
                            // Carry the typed text over to the other kind of search.
                            if (mode) {
                                actions.onTextQuery(state.query)
                                actions.onQuery("")
                            } else {
                                actions.onQuery(state.textQuery)
                                actions.onTextQuery("")
                            }
                            byText = mode
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(i, 2, MaterialTheme.shapes.small),
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun RestoreCard(previousLabel: String?, actions: ReviewActions) {
    DgaCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("انتهيت من التنظيف؟", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "هذا التطبيق مخصّص للتنظيف وليس بديلًا كاملًا لتطبيق الرسائل (مثلًا لا يستقبل رسائل الوسائط MMS). " +
                    "يُنصح بإرجاع ${previousLabel ?: "تطبيق الرسائل المعتاد"} كتطبيق افتراضي.",
            )
            Row {
                TextButton(onClick = actions.onRestoreDefault) { Text("إرجاع التطبيق الافتراضي") }
                TextButton(onClick = actions.onDismissRestore) { Text("لاحقًا") }
            }
        }
    }
}

@Composable
private fun MessageCard(item: ReviewItem, selected: Boolean, onToggle: () -> Unit) {
    val category = item.verdict.category
    val suspicious = category == Category.SUSPICIOUS
    val tag = tagColors(category.tagKind())
    // Selected cards get the brand border so the selection is visible without reading the checkbox.
    DgaCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        containerColor = when {
            suspicious -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surface
        },
        borderColor = when {
            selected -> MaterialTheme.colorScheme.primary
            suspicious -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
    ) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.sms.contactName ?: item.sms.address.ifBlank { "مجهول" },
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DgaTag(category.label, category.tagKind())
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.sms.date)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    item.sms.body.ifBlank { if (item.sms.isMms) "(رسالة وسائط بدون نص)" else "(فارغة)" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                item.verdict.reasons.forEach { reason ->
                    IconLine(
                        icon = if (suspicious) Icons.Default.Warning else null,
                        text = reason,
                        color = if (suspicious) MaterialTheme.colorScheme.error else tag.content,
                    )
                }
            }
        }
    }
}

/** DGA status colors: red for danger, orange for things to clean, green for what to keep. */
private fun Category.tagKind(): TagKind = when (this) {
    Category.SUSPICIOUS -> TagKind.ERROR
    Category.DUPLICATE -> TagKind.NEUTRAL
    Category.OTP_EXPIRED -> TagKind.WARNING
    Category.PROMOTION -> TagKind.LAVENDER
    Category.OTP_RECENT -> TagKind.INFO
    Category.TRANSACTION -> TagKind.BRAND
    Category.PERSONAL -> TagKind.SUCCESS
    Category.SENT -> TagKind.GOLD
    Category.OTHER -> TagKind.NEUTRAL
}
