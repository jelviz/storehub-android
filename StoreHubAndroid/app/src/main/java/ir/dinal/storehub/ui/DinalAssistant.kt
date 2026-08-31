package ir.dinal.storehub.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.data.PublishingPrefs
import ir.dinal.storehub.publishing.DinalAssistantClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AssistantBubble(val role: String, val text: String)

@Composable
fun DinalAssistantScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val prefs = remember { PublishingPrefs(ctx) }
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf<AssistantBubble>() }

    var input by remember { mutableStateOf("") }
    var includeStoreData by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun send(text: String = input) {
        val q = text.trim()
        if (q.isBlank() || busy) return
        if (!prefs.hasOpenAiKey()) {
            error = "برای استفاده از دستیار DINAL ابتدا OpenAI API Key را در تنظیمات هوش مصنوعی وارد کن."
            return
        }

        input = ""
        error = null
        val oldHistory = messages.map { DinalAssistantClient.Turn(it.role, it.text) }
        messages += AssistantBubble("user", q)

        scope.launch {
            busy = true
            runCatching {
                val contextText = if (includeStoreData) store.assistantContext(q) else ""
                withContext(Dispatchers.IO) {
                    DinalAssistantClient(prefs.openAiKey(), prefs.openAiModel)
                        .ask(q, oldHistory, contextText)
                }
            }.onSuccess { answer ->
                messages += AssistantBubble("assistant", answer)
            }.onFailure { t ->
                error = t.message ?: "ارتباط با دستیار ناموفق بود."
            }
            busy = false
        }
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    DinalScreen(nav, "دستیار DINAL") { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("دستیار هوشمند فروشگاه دینال", fontWeight = FontWeight.Bold)
                            Text(
                                "به داده‌های همین StoreHub دسترسی اختیاری دارد؛ نه به چت‌های اپ ChatGPT.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Switch(includeStoreData, { includeStoreData = it })
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("استفاده از اطلاعات محلی فروشگاه")
                            Text("در حالت روشن، خلاصه مرتبط موجودی/فروش/چک‌ها برای پاسخ به OpenAI ارسال می‌شود.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!prefs.hasOpenAiKey()) {
                        FilledTonalButton(onClick = { nav.navigate("publishing_settings") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Key, null); Spacer(Modifier.width(6.dp)); Text("تنظیم اتصال OpenAI")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(onClick = { send("موجودی کم و ناموجودهای مهمم را خلاصه کن") }, label = { Text("موجودی") })
                        AssistChip(onClick = { send("چک‌های باز و نزدیک سررسید را تحلیل کن") }, label = { Text("چک‌ها") })
                        AssistChip(onClick = { send("از وضعیت فروش و خریدهای اخیر چه نکته‌ای می‌بینی؟") }, label = { Text("گزارش") })
                    }
                }
            }

            if (messages.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.SmartToy, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("مثلاً بپرس: امروز روی چه کالاهایی تمرکز کنم؟", fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = if (msg.role == "user") Arrangement.Start else Arrangement.End
                        ) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = if (msg.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(.88f)
                            ) {
                                Text(msg.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (busy) item {
                        Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp)); Text("در حال فکر کردن…")
                            }
                        }
                    }
                }
            }

            error?.let {
                Surface(
                    modifier = Modifier.padding(horizontal = 12.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) { Text(it, Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer) }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("از دستیار دینال بپرس…") },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 4
                )
                FilledIconButton(onClick = { send() }, enabled = !busy && input.isNotBlank()) {
                    Icon(Icons.Rounded.Send, "ارسال")
                }
            }
        }
    }
}
