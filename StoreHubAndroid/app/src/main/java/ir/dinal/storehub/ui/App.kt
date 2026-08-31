package ir.dinal.storehub.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import ir.dinal.storehub.R
import ir.dinal.storehub.data.DashboardLocal
import ir.dinal.storehub.data.LocalStore
import ir.dinal.storehub.ui.theme.*

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)
private val bottomItems = listOf(
    BottomItem("home", "خانه", Icons.Rounded.Home),
    BottomItem("pos", "صندوق", Icons.Rounded.PointOfSale),
    BottomItem("products", "کالا", Icons.Rounded.Inventory2),
    BottomItem("calendar", "تقویم", Icons.Rounded.CalendarMonth),
    BottomItem("more", "بیشتر", Icons.Rounded.GridView)
)

@Composable
fun StoreHubRoot(activity: Activity, onSplashFinished: () -> Unit = {}) {
    DinalTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            if (showSplash) {
                DinalSplashScreen {
                    showSplash = false
                    onSplashFinished()
                }
                return@CompositionLocalProvider
            }

            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val route = entry?.destination?.route
            val showBottom = bottomItems.any { it.route == route }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBottom) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 10.dp
                        ) {
                            bottomItems.forEach { item ->
                                NavigationBarItem(
                                    selected = route == item.route,
                                    onClick = {
                                        if (route != item.route) {
                                            if (item.route == "home") {
                                                nav.navigate("home") {
                                                    popUpTo("home") { inclusive = false }
                                                    launchSingleTop = true
                                                    restoreState = false
                                                }
                                            } else {
                                                nav.navigate(item.route) {
                                                    popUpTo("home") { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DinalPurple,
                                        selectedTextColor = DinalPlum,
                                        indicatorColor = DinalPurple.copy(alpha = .14f),
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, fontWeight = if (route == item.route) FontWeight.Bold else FontWeight.Medium) }
                                )
                            }
                        }
                    }
                }
            ) { rootPadding ->
                NavHost(
                    navController = nav,
                    startDestination = "home",
                    modifier = Modifier.padding(bottom = if (showBottom) rootPadding.calculateBottomPadding() else 0.dp)
                ) {
                    composable("home") { HomeScreen(nav) }
                    composable("products") { ProductsScreen(activity, nav) }
                    composable("smart_product") { SmartProductScreen(nav) }
                    composable("publishing_settings") { PublishingSettingsScreen(nav) }
                    composable("assistant") { DinalAssistantScreen(nav) }
                    composable("inventory") { InventoryScreen(nav) }
                    composable("history") { HistoryScreen(nav) }
                    composable("pos") { PosScreen(activity, nav) }
                    composable("scanner") { ScannerScreen(nav) }
                    composable("sales") { SalesScreen(nav) }
                    composable("transfers") { TransfersScreen(nav) }
                    composable("purchases") { PurchasesScreen(nav) }
                    composable("checks") { ChecksScreen(nav) }
                    composable("appointments") { AppointmentsScreen(nav) }
                    composable("occasions") { OccasionsScreen(nav) }
                    composable("calendar") { CalendarScreen(nav) }
                    composable("sync") { SyncScreen(nav) }
                    composable("settings") { SettingsScreen(activity, nav) }
                    composable("printer") { PrinterSettingsScreen(activity, nav) }
                    composable("more") { MoreScreen(nav) }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<DashboardLocal?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    LaunchedEffect(refresh) {
        runCatching { LocalStore.get(ctx).dashboard() }
            .onSuccess { data = it; error = null }
            .onFailure { error = it.message }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp, 14.dp, 14.dp, 30.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = DinalPlum),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF251236),
                                    DinalPurple,
                                    DinalRose.copy(alpha = .95f),
                                    DinalGold.copy(alpha = .78f)
                                )
                            )
                        )
                        .padding(horizontal = 22.dp, vertical = 20.dp)
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = Color.White.copy(alpha = .12f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Image(
                                painter = painterResource(R.drawable.dinal_logo),
                                contentDescription = "DINAL",
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth(.72f).height(72.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("DINAL StoreHub", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("مدیریت فروشگاه روی گوشی", color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Surface(color = Color.White.copy(alpha = .14f), shape = RoundedCornerShape(99.dp)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CalendarToday, null, tint = Color.White, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(7.dp))
                                Text(todayPersian(), color = Color.White, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("وضعیت امروز", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("فروش امروز", toman(data?.todaySales ?: 0.0), DinalGold, Modifier.weight(1f))
                MetricCard("کم‌موجود", (data?.lowStock ?: 0).toString(), DinalRose, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("کالای فعال", (data?.storeProducts ?: 0).toString(), DinalPurple, Modifier.weight(1f))
                MetricCard("ناموجود", (data?.outOfStock ?: 0).toString(), MaterialTheme.colorScheme.error, Modifier.weight(1f))
            }
        }
        item { ErrorText(error) }

        item {
            SectionCard("دسترسی سریع", subtitle = "کارهای روزمره فروشگاه") {
                QuickActionRow(
                    listOf(
                        QuickAction("pos", "فروش", Icons.Rounded.PointOfSale, DinalPurple),
                        QuickAction("smart_product", "ثبت هوشمند", Icons.Rounded.AutoAwesome, DinalRose),
                        QuickAction("checks", "چک", Icons.Rounded.ReceiptLong, DinalGold),
                        QuickAction("assistant", "دستیار", Icons.Rounded.SmartToy, DinalMint)
                    ), nav
                )
            }
        }

        item {
            SectionCard("یادآوری و پیگیری") {
                ReminderLine(Icons.Rounded.ReceiptLong, "چک‌های نزدیک سررسید", (data?.dueChecks ?: 0).toString())
                ReminderLine(Icons.Rounded.Event, "قرارهای امروز", (data?.todayAppointments ?: 0).toString())
                ReminderLine(Icons.Rounded.LocalShipping, "انتقال‌های باز", (data?.pendingTransfers ?: 0).toString())
                FilledTonalButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(6.dp)); Text("تازه‌سازی داشبورد")
                }
            }
        }
    }
}

private data class QuickAction(val route: String, val label: String, val icon: ImageVector, val color: Color)

@Composable
private fun QuickActionRow(items: List<QuickAction>, nav: NavHostController) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Surface(
                modifier = Modifier.weight(1f).clickable { nav.navigate(item.route) },
                shape = RoundedCornerShape(20.dp),
                color = item.color.copy(alpha = .12f),
                tonalElevation = 1.dp
            ) {
                Column(Modifier.padding(vertical = 15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = RoundedCornerShape(14.dp), color = item.color.copy(alpha = .16f)) {
                        Icon(item.icon, null, tint = item.color, modifier = Modifier.padding(9.dp).size(23.dp))
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(item.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ReminderLine(icon: ImageVector, label: String, value: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Surface(shape = RoundedCornerShape(99.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(value, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MoreScreen(nav: NavHostController) {
    val menus = listOf(
        Triple("assistant", "دستیار هوشمند DINAL", Icons.Rounded.SmartToy),
        Triple("smart_product", "ثبت هوشمند محصول روی ۳ سایت", Icons.Rounded.AutoAwesome),
        Triple("publishing_settings", "اتصال ۳ سایت و هوش مصنوعی", Icons.Rounded.CloudUpload),
        Triple("inventory", "انبار و موجودی", Icons.Rounded.Warehouse),
        Triple("history", "تاریخچه موجودی", Icons.Rounded.History),
        Triple("sales", "فروش‌ها و مرجوعی", Icons.Rounded.Receipt),
        Triple("transfers", "انتقال دپو به مغازه", Icons.Rounded.SwapHoriz),
        Triple("purchases", "خریدهای بازار", Icons.Rounded.ShoppingCart),
        Triple("checks", "چک‌ها و سررسید", Icons.Rounded.ReceiptLong),
        Triple("appointments", "قرار ملاقات‌ها", Icons.Rounded.Event),
        Triple("occasions", "مناسبت‌های ایران و جهان", Icons.Rounded.Celebration),
        Triple("sync", "سینک WooCommerce", Icons.Rounded.Sync),
        Triple("printer", "چاپگر لیبل", Icons.Rounded.Print),
        Triple("settings", "تنظیمات و بکاپ", Icons.Rounded.Settings)
    )
    DinalScreen(nav, "بیشتر", showBack = false) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { DinalHero("DINAL StoreHub", "همه ابزارهای مدیریت فروشگاه، داخل همین گوشی") }
            items(menus) { (route, title, icon) ->
                Card(
                    onClick = { nav.navigate(route) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(15.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Icon(icon, null, modifier = Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}
