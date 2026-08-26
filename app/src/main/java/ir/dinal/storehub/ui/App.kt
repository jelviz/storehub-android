package ir.dinal.storehub.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import ir.dinal.storehub.data.LocalStore

@Composable fun StoreHubRoot(activity:Activity){MaterialTheme{CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){val nav=rememberNavController();NavHost(nav,"home"){
    composable("home"){HomeScreen(nav)}
    composable("products"){ProductsScreen(activity,nav)}
    composable("inventory"){InventoryScreen(nav)}
    composable("history"){HistoryScreen(nav)}
    composable("pos"){PosScreen(activity,nav)}
    composable("sales"){SalesScreen(nav)}
    composable("transfers"){TransfersScreen(nav)}
    composable("purchases"){PurchasesScreen(nav)}
    composable("checks"){ChecksScreen(nav)}
    composable("appointments"){AppointmentsScreen(nav)}
    composable("calendar"){CalendarScreen(nav)}
    composable("sync"){SyncScreen(nav)}
    composable("settings"){SettingsScreen(nav)}
}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HomeScreen(nav:androidx.navigation.NavHostController){val ctx=LocalContext.current;var d by remember{mutableStateOf<ir.dinal.storehub.data.DashboardLocal?>(null)};var err by remember{mutableStateOf<String?>(null)};var refresh by remember{mutableIntStateOf(0)};LaunchedEffect(refresh){runCatching{LocalStore.get(ctx).dashboard()}.onSuccess{d=it}.onFailure{err=it.message}}
    Scaffold(topBar={TopAppBar(title={Text("StoreHub — فقط روی گوشی")},actions={TextButton({refresh++}){Text("تازه‌سازی")}})}){pad->LazyColumn(Modifier.padding(pad).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){item{d?.let{Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text("فروش امروز: ${money(it.todaySales)}",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text("کالاهای مغازه: ${it.storeProducts} | کم‌موجود: ${it.lowStock} | ناموجود: ${it.outOfStock}");Text("چک نزدیک موعد: ${it.dueChecks} | قرار امروز: ${it.todayAppointments}")}}};ErrorText(err)};val menus=listOf("products" to "کالاها و لیبل","inventory" to "انبار و موجودی","history" to "تاریخچه ورود/خروج","pos" to "صندوق و اسکن","sales" to "فروش‌ها و مرجوعی","transfers" to "انتقال دپو → مغازه","purchases" to "خریدهای بازار","checks" to "چک‌ها و سررسید","appointments" to "قرار ملاقات‌ها","calendar" to "تقویم شمسی","sync" to "سینک مستقیم ووکامرس","settings" to "تنظیمات ووکامرس و بکاپ");items(menus){m->Card(onClick={nav.navigate(m.first)},modifier=Modifier.fillMaxWidth()){Text(m.second,Modifier.padding(18.dp),style=MaterialTheme.typography.titleMedium)}}}}
}
