package com.salroumi.smscleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.salroumi.smscleaner.BuildConfig
import com.salroumi.smscleaner.analysis.Category
import com.salroumi.smscleaner.analysis.SmsAnalyzer
import java.text.DateFormat
import java.util.Date

class ReviewActions(
    val onGrantPermission: () -> Unit,
    val onScan: () -> Unit,
    val onToggle: (Long) -> Unit,
    val onFilter: (Category?) -> Unit,
    val onQuery: (String) -> Unit,
    val onTextQuery: (String) -> Unit,
    val onShowSenders: (Boolean) -> Unit,
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
        topBar = {
            TopAppBar(
                title = { Text("منظّف الرسائل", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    if (state.hasPermission) {
                        IconButton(onClick = actions.onScan, enabled = !state.loading) {
                            Icon(Icons.Default.Refresh, contentDescription = "إعادة الفحص")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.scanned && state.items.isNotEmpty() && !state.showSenders) {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            if (state.isDefaultApp) showConfirm = true else showNeedDefault = true
                        },
                        enabled = state.selectedVisible.isNotEmpty() && !state.loading,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("حذف المحدد (${state.selectedVisible.size})")
                    }
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
        title = "مرحبًا 👋",
        body = "يحتاج التطبيق إذن قراءة الرسائل لمراجعتها وتصنيفها. تتم المراجعة على جهازك فقط ولا تُرسل أي رسالة لأي جهة.",
        button = "منح الإذن",
        onClick = onGrant,
    )
}

@Composable
private fun StartPrompt(onScan: () -> Unit) {
    CenteredMessage(
        title = "جاهز للفحص",
        body = "سيتم فحص الرسائل الواردة وتحديد: الرسائل المشبوهة، المكررة، رموز التحقق القديمة، والإعلانات. لن يُحذف شيء قبل تأكيدك.",
        button = "ابدأ الفحص",
        onClick = onScan,
    )
}

@Composable
private fun CenteredMessage(title: String, body: String, button: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(button) }
    }
}

@Composable
private fun Results(state: UiState, actions: ReviewActions, onDeleteSender: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (state.showSenders) 1 else 0) {
            Tab(
                selected = !state.showSenders,
                onClick = { actions.onShowSenders(false) },
                text = { Text("الرسائل") },
            )
            Tab(
                selected = state.showSenders,
                onClick = { actions.onShowSenders(true) },
                text = { Text("المرسلون") },
            )
        }
        if (state.showSenders) {
            SendersList(state, actions, onDeleteSender)
        } else {
            MessagesList(state, actions)
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
            Card(
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
                    Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                        Text(
                            sender.total.toString(),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
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
        item {
            val all = state.items
            val received = all.count { !it.sms.sent && !it.sms.isMms }
            val sent = all.count { it.sms.sent && !it.sms.isMms }
            val mms = all.count { it.sms.isMms }
            Text(
                "الإصدار ${BuildConfig.VERSION_NAME} • تم فحص ${all.size} رسالة " +
                    "(واردة $received، مرسلة $sent، وسائط $mms)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions.onQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ابحث باسم المرسل أو رقمه") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { actions.onQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
            )
        }
        item {
            OutlinedTextField(
                value = state.textQuery,
                onValueChange = actions.onTextQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ابحث عن نص داخل الرسالة") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.textQuery.isNotEmpty()) {
                        IconButton(onClick = { actions.onTextQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح البحث")
                        }
                    }
                },
            )
        }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allSelected, onCheckedChange = { actions.onSelectAllVisible(it) })
                Text("تحديد الكل في هذه القائمة (${visible.size})")
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

@Composable
private fun RestoreCard(previousLabel: String?, actions: ReviewActions) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("انتهيت من التنظيف؟", fontWeight = FontWeight.Bold)
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
    val (badgeColor, containerColor) = categoryColors(item.verdict.category)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.sms.contactName ?: item.sms.address.ifBlank { "مجهول" },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(color = badgeColor, shape = MaterialTheme.shapes.small) {
                        Text(
                            item.verdict.category.label,
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.sms.date)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    item.sms.body.ifBlank { if (item.sms.isMms) "(رسالة وسائط بدون نص)" else "(فارغة)" },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                item.verdict.reasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.verdict.category == Category.SUSPICIOUS) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(reason, style = MaterialTheme.typography.bodySmall, color = badgeColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun categoryColors(category: Category): Pair<Color, Color> {
    val surface = MaterialTheme.colorScheme.surface
    return when (category) {
        Category.SUSPICIOUS -> Color(0xFFC62828) to Color(0xFFC62828).copy(alpha = 0.10f)
        Category.DUPLICATE -> Color(0xFF6D4C41) to surface
        Category.OTP_EXPIRED -> Color(0xFFEF6C00) to surface
        Category.PROMOTION -> Color(0xFF8E24AA) to surface
        Category.OTP_RECENT -> Color(0xFF1E88E5) to surface
        Category.TRANSACTION -> Color(0xFF2E7D32) to surface
        Category.PERSONAL -> Color(0xFF00897B) to surface
        Category.SENT -> Color(0xFF3949AB) to surface
        Category.OTHER -> Color(0xFF546E7A) to surface
    }
}
