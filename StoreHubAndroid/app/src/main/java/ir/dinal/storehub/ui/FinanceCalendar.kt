package ir.dinal.storehub.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import ir.dinal.storehub.data.*
import kotlinx.coroutines.launch

private data class CheckMonthSummary(
    val year: Int,
    val month: Int,
    val checks: List<IssuedCheckEntity>
) {
    val effectiveChecks get() = checks.filter { it.status != 4 }
    val totalCount get() = effectiveChecks.size
    val totalAmount get() = effectiveChecks.sumOf { it.amount }
    val openChecks get() = checks.filter { it.status == 1 }
    val passedChecks get() = checks.filter { it.status == 2 }
    val returnedChecks get() = checks.filter { it.status == 3 }
    val cancelledChecks get() = checks.filter { it.status == 4 }
    val openAmount get() = openChecks.sumOf { it.amount }
    val passedAmount get() = passedChecks.sumOf { it.amount }
    val returnedAmount get() = returnedChecks.sumOf { it.amount }
    val cancelledAmount get() = cancelledChecks.sumOf { it.amount }
}

private fun checkYear(check: IssuedCheckEntity): Int? = Jalali.parse(check.dueDatePersian)?.year
private fun checkMonth(check: IssuedCheckEntity): Int? = Jalali.parse(check.dueDatePersian)?.month

@Composable
private fun CheckBreakdownRow(label: String, checks: List<IssuedCheckEntity>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ${checks.size} فقره", style = MaterialTheme.typography.bodyMedium)
        Text(toman(checks.sumOf { it.amount }), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MonthlyCheckCard(summary: CheckMonthSummary) {
    var expanded by remember(summary.year, summary.month) { mutableStateOf(false) }
    val hasChecks = summary.checks.isNotEmpty()
    Card(
        modifier = Modifier.fillMaxWidth().then(if (hasChecks) Modifier.clickable { expanded = !expanded } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .10f))
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Jalali.monthName(summary.month)} ${summary.year}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (hasChecks) "${summary.totalCount} فقره • ${toman(summary.totalAmount)}" else "بدون چک",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasChecks) Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
            }

            if (hasChecks) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("تعهد باز", fontWeight = FontWeight.SemiBold)
                        Text("${summary.openChecks.size} فقره • ${toman(summary.openAmount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (expanded && hasChecks) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
                CheckBreakdownRow("باز", summary.openChecks)
                CheckBreakdownRow("پاس‌شده", summary.passedChecks)
                CheckBreakdownRow("برگشتی", summary.returnedChecks)
                if (summary.cancelledChecks.isNotEmpty()) CheckBreakdownRow("لغوشده", summary.cancelledChecks)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .12f))
                summary.checks.sortedBy { it.dueEpochDay }.forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(toman(c.amount), fontWeight = FontWeight.SemiBold)
                        }
                        val status = when (c.status) { 1 -> "باز"; 2 -> "پاس‌شده"; 3 -> "برگشتی"; 4 -> "لغوشده"; else -> "نامشخص" }
                        Text("${c.dueDatePersian} • $status • ${c.bankName ?: "بدون بانک"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun ChecksScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<IssuedCheckEntity>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(todayPersian()) }
    var reminder by remember { mutableStateOf("3") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableIntStateOf(Jalali.today().year) }

    suspend fun load() { list = store.checks() }
    LaunchedEffect(Unit) { load() }

    val yearChecks = remember(list, selectedYear) { list.filter { checkYear(it) == selectedYear } }
    val effectiveYearChecks = remember(yearChecks) { yearChecks.filter { it.status != 4 } }
    val yearTotal = remember(effectiveYearChecks) { effectiveYearChecks.sumOf { it.amount } }
    val yearOpen = remember(yearChecks) { yearChecks.filter { it.status == 1 }.sumOf { it.amount } }
    val yearOpenCount = remember(yearChecks) { yearChecks.count { it.status == 1 } }
    val monthSummaries = remember(yearChecks, selectedYear) {
        (1..12).map { month -> CheckMonthSummary(selectedYear, month, yearChecks.filter { checkMonth(it) == month }) }
    }

    DinalScreen(nav, "چک‌ها و سررسید", floatingActionButton = {
        FloatingActionButton(onClick = { showForm = !showForm }) { Icon(if (showForm) Icons.Rounded.Close else Icons.Rounded.Add, null) }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DinalHero("مدیریت چک‌ها", "گزارش ماهانه شمسی، تعهد باز و سررسیدها") {
                    Icon(Icons.Rounded.ReceiptLong, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            if (showForm) item {
                SectionCard("ثبت چک جدید") {
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(bank, { bank = it }, label = { Text("بانک") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(number, { number = it }, label = { Text("شماره چک") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(payee, { payee = it }, label = { Text("در وجه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    MoneyTextField(amount, { amount = it }, "مبلغ چک", modifier = Modifier.fillMaxWidth(), supportingText = "مثال: 125,000,000 تومان")
                    PersianDateField("تاریخ سررسید", due) { due = it }
                    OutlinedTextField(reminder, { reminder = it }, label = { Text("چند روز قبل اعلان بدهد") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(note, { note = it }, label = { Text("توضیح") }, modifier = Modifier.fillMaxWidth())
                    ErrorText(err)
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    store.saveCheck(title = title, bank = bank, number = number, payee = payee, amount = parseToman(amount), duePersian = due, reminderDays = reminder.toIntOrNull() ?: 3, note = note)
                                }.onSuccess {
                                    title = ""; bank = ""; number = ""; payee = ""; amount = ""; note = ""; showForm = false; load()
                                }.onFailure { err = it.message }
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(6.dp)); Text("ثبت و فعال‌کردن یادآوری") }
                }
            }

            item {
                SectionCard("تاریخچه و گزارش ماهانه", subtitle = "مبنای گزارش، ماهِ تاریخ سررسید چک است.") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton(onClick = { selectedYear-- }) { Icon(Icons.Rounded.ChevronRight, contentDescription = "سال قبل") }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("سال $selectedYear", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${effectiveYearChecks.size} فقره مؤثر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { selectedYear++ }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "سال بعد") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MetricCard("جمع سال", toman(yearTotal), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                        MetricCard("تعهد باز", toman(yearOpen), MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                    }
                    Text("چک باز: $yearOpenCount فقره", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            items(monthSummaries, key = { "${it.year}-${it.month}" }) { summary ->
                MonthlyCheckCard(summary)
            }

            item {
                Text("همه چک‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            }
            if (list.isEmpty()) item { SectionCard("چکی ثبت نشده") { Text("با دکمه + اولین چک را ثبت کن.") } }
            items(list, key = { it.id }) { c ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(c.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(toman(c.amount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("${c.dueDatePersian} • ${c.bankName ?: "بدون بانک"}")
                        Text("اعلان ${c.reminderDaysBefore} روز قبل", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val status = when (c.status) { 1 -> "باز"; 2 -> "پاس‌شده"; 3 -> "برگشتی"; 4 -> "لغوشده"; else -> "نامشخص" }
                        AssistChip(onClick = {}, label = { Text(status) })
                        if (c.status == 1) {
                            Row {
                                TextButton({ scope.launch { store.setCheckStatus(c.id, 2); load() } }) { Text("پاس شد") }
                                TextButton({ scope.launch { store.setCheckStatus(c.id, 3); load() } }) { Text("برگشتی") }
                                TextButton({ scope.launch { store.setCheckStatus(c.id, 4); load() } }) { Text("لغو") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<AppointmentEntity>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(todayPersian()) }
    var time by remember { mutableStateOf("12:00") }
    var reminder by remember { mutableStateOf("60") }
    var note by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var showForm by remember { mutableStateOf(false) }

    suspend fun load() { list = store.appointments() }
    LaunchedEffect(Unit) { load() }

    DinalScreen(nav, "قرارها", floatingActionButton = {
        FloatingActionButton(onClick = { showForm = !showForm }) { Icon(if (showForm) Icons.Rounded.Close else Icons.Rounded.Add, null) }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                DinalHero("قرار ملاقات‌ها", "یادآوری خودکار حتی وقتی اپ بسته است") {
                    Icon(Icons.Rounded.EventAvailable, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            if (showForm) item {
                SectionCard("قرار جدید") {
                    OutlinedTextField(title, { title = it }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(person, { person = it }, label = { Text("نام شخص") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(mobile, { mobile = it }, label = { Text("موبایل") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(location, { location = it }, label = { Text("محل") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    PersianDateField("تاریخ", date) { date = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(time, { time = it }, label = { Text("ساعت؛ 14:30") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(reminder, { reminder = it }, label = { Text("دقیقه قبل") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(note, { note = it }, label = { Text("توضیح") }, modifier = Modifier.fillMaxWidth())
                    ErrorText(err)
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { store.saveAppointment(title = title, person = person, mobile = mobile, location = location, datePersian = date, time = time, reminderMinutes = reminder.toIntOrNull() ?: 60, note = note) }
                                    .onSuccess { title = ""; person = ""; mobile = ""; location = ""; note = ""; showForm = false; load() }
                                    .onFailure { err = it.message }
                            }
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Icon(Icons.Rounded.NotificationsActive, null); Spacer(Modifier.width(6.dp)); Text("ثبت قرار و یادآوری") }
                }
            }
            if (list.isEmpty()) item { SectionCard("قراری ثبت نشده") { Text("با دکمه + یک قرار جدید بساز.") } }
            items(list, key = { it.id }) { a ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(a.title, fontWeight = FontWeight.Bold)
                        Text("${a.datePersian} ساعت ${a.time}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        val extra = listOfNotNull(a.personName, a.location).filter { it.isNotBlank() }.joinToString(" • ")
                        if (extra.isNotBlank()) Text(extra)
                        Text("اعلان ${a.reminderMinutesBefore} دقیقه قبل", style = MaterialTheme.typography.bodySmall)
                        if (a.status == 1) TextButton({ scope.launch { store.setAppointmentStatus(a.id, 2); load() } }) { Icon(Icons.Rounded.Done, null); Spacer(Modifier.width(5.dp)); Text("انجام شد") }
                        else AssistChip(onClick = {}, label = { Text("انجام‌شده") })
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val store = remember { LocalStore.get(ctx) }
    val now = remember { Jalali.today() }
    var year by remember { mutableIntStateOf(now.year) }
    var month by remember { mutableIntStateOf(now.month) }
    var selectedDay by remember { mutableIntStateOf(if (year == now.year && month == now.month) now.day else 1) }
    var data by remember { mutableStateOf<CalendarDataLocal?>(null) }

    LaunchedEffect(year, month) {
        data = store.calendar(year, month)
        selectedDay = if (year == now.year && month == now.month) now.day else 1
    }

    DinalScreen(nav, "تقویم شمسی", showBack = false) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            IconButton({ if (month == 1) { month = 12; year-- } else month-- }) { Icon(Icons.Rounded.ChevronRight, "ماه قبل") }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(Jalali.monthName(month), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(year.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton({ if (month == 12) { month = 1; year++ } else month++ }) { Icon(Icons.Rounded.ChevronLeft, "ماه بعد") }
                        }
                        PersianMonthGrid(year, month, selectedDay, data) { selectedDay = it }
                    }
                }
            }
            item {
                val date = "%04d/%02d/%02d".format(year, month, selectedDay)
                val checks = data?.checks?.filter { it.dueDatePersian == date }.orEmpty()
                val appointments = data?.appointments?.filter { it.datePersian == date }.orEmpty()
                val purchases = data?.purchases?.filter { it.purchaseDatePersian == date }.orEmpty()
                val occasions = remember(year, month, selectedDay) {
                    GiftOccasionCatalog.forPersianYear(year).filter { it.persianText == date }
                }
                SectionCard("رویدادهای $date", subtitle = if (checks.isEmpty() && appointments.isEmpty() && purchases.isEmpty() && occasions.isEmpty()) "برای این روز رویدادی ثبت نشده" else null) {
                    occasions.forEach { EventLine(Icons.Rounded.Celebration, "مناسبت: ${it.title}", if(it.scope == OccasionScope.IRAN) "ایران" else "جهان") }
                    checks.forEach { EventLine(Icons.Rounded.ReceiptLong, "چک: ${it.title}", toman(it.amount)) }
                    appointments.forEach { EventLine(Icons.Rounded.Event, "قرار: ${it.title}", it.time) }
                    purchases.forEach { EventLine(Icons.Rounded.ShoppingCart, "خرید: ${it.purchaseNo}", toman(it.total)) }
                    if (checks.isEmpty() && appointments.isEmpty() && purchases.isEmpty() && occasions.isEmpty()) Text("—", color = MaterialTheme.colorScheme.outline)
                }
            }
            item {
                OutlinedButton(onClick = { year = now.year; month = now.month; selectedDay = now.day }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Today, null); Spacer(Modifier.width(6.dp)); Text("رفتن به امروز")
                }
            }
        }
    }
}

@Composable
private fun PersianMonthGrid(year: Int, month: Int, selectedDay: Int, data: CalendarDataLocal?, onSelect: (Int) -> Unit) {
    val weekdays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val days = Jalali.daysInMonth(year, month)
    val offset = Jalali.firstDaySaturdayOffset(year, month)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) { weekdays.forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        val cells = List(offset) { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { i ->
                    val day = week.getOrElse(i) { 0 }
                    if (day == 0) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else {
                        val date = "%04d/%02d/%02d".format(year, month, day)
                        val count = data?.let { d -> d.checks.count { it.dueDatePersian == date } + d.appointments.count { it.datePersian == date } + d.purchases.count { it.purchaseDatePersian == date } } ?: 0
                        Surface(
                            modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clickable { onSelect(day) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (day == selectedDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                            contentColor = if (day == selectedDay) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(day.toString(), fontWeight = if (day == selectedDay) FontWeight.Bold else FontWeight.Normal)
                                if (count > 0) Badge(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)) { Text(count.toString()) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun PersianDateField(label: String, value: String, onValue: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().clickable { open = true },
        trailingIcon = { IconButton({ open = true }) { Icon(Icons.Rounded.CalendarMonth, null) } }
    )
    if (open) PersianDatePickerDialog(value, onDismiss = { open = false }) { onValue(it); open = false }
}

@Composable
private fun PersianDatePickerDialog(initial: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val parsed = Jalali.parse(initial) ?: Jalali.today()
    var year by remember { mutableIntStateOf(parsed.year) }
    var month by remember { mutableIntStateOf(parsed.month) }
    var day by remember { mutableIntStateOf(parsed.day.coerceAtMost(Jalali.daysInMonth(parsed.year, parsed.month))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ if (month == 1) { month = 12; year-- } else month--; day = day.coerceAtMost(Jalali.daysInMonth(year, month)) }) { Icon(Icons.Rounded.ChevronRight, null) }
                    Text("${Jalali.monthName(month)} $year", fontWeight = FontWeight.Bold)
                    IconButton({ if (month == 12) { month = 1; year++ } else month++; day = day.coerceAtMost(Jalali.daysInMonth(year, month)) }) { Icon(Icons.Rounded.ChevronLeft, null) }
                }
                LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.height(270.dp)) {
                    items(Jalali.firstDaySaturdayOffset(year, month)) { Spacer(Modifier.aspectRatio(1f)) }
                    items(Jalali.daysInMonth(year, month)) { index ->
                        val d = index + 1
                        Surface(
                            modifier = Modifier.aspectRatio(1f).padding(2.dp).clickable { day = d },
                            shape = RoundedCornerShape(12.dp),
                            color = if (d == day) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f),
                            contentColor = if (d == day) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(d.toString()) } }
                    }
                }
            }
        },
        confirmButton = { Button({ onSelect("%04d/%02d/%02d".format(year, month, day)) }) { Text("انتخاب") } },
        dismissButton = { TextButton(onDismiss) { Text("انصراف") } }
    )
}
