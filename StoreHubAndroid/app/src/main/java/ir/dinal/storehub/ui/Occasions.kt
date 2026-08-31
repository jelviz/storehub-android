package ir.dinal.storehub.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import ir.dinal.storehub.data.Jalali
import ir.dinal.storehub.ui.theme.DinalGold
import ir.dinal.storehub.ui.theme.DinalMint
import ir.dinal.storehub.ui.theme.DinalPurple
import ir.dinal.storehub.ui.theme.DinalRose
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class OccasionScope { IRAN, WORLD }
enum class OccasionKind { FAMILY, ROMANTIC, CHILDREN, EDUCATION, RELIGIOUS, FESTIVAL, SALES, SPECIAL }

data class GiftOccasion(
    val id: String,
    val title: String,
    val date: LocalDate,
    val scope: OccasionScope,
    val kind: OccasionKind,
    val audience: String,
    val giftHint: String,
    val campaignLeadDays: Int = 14,
    val priority: Int = 2
) {
    val persian: Jalali.Date get() = Jalali.fromGregorian(date.year, date.monthValue, date.dayOfMonth)
    val persianText: String get() = Jalali.format(persian)
}

object GiftOccasionCatalog {
    private fun p(year: Int, month: Int, day: Int): LocalDate = Jalali.toGregorian(year, month, day)

    private fun fixedPersian(year: Int) = listOf(
        GiftOccasion("nowruz-$year", "نوروز و عیدی", p(year,1,1), OccasionScope.IRAN, OccasionKind.FESTIVAL, "خانواده، دوستان و کودکان", "باکس هدیه، عروسک، تراول‌ماگ، اکسسوری و هدیه‌های نوروزی", 30, 3),
        GiftOccasion("teacher-ir-$year", "روز معلم ایران", p(year,2,12), OccasionScope.IRAN, OccasionKind.EDUCATION, "معلم و استاد", "ماگ و تراول‌ماگ، ست هدیه، اکسسوری رومیزی و هدیه اقتصادی", 21, 3),
        GiftOccasion("child-ir-$year", "روز ملی کودک / جشن مهرگان", p(year,7,16), OccasionScope.IRAN, OccasionKind.CHILDREN, "کودکان", "اسباب‌بازی، عروسک، فیگور و پک‌های هدیه کودک", 21, 3),
        GiftOccasion("student-ir-$year", "روز دانش‌آموز", p(year,8,13), OccasionScope.IRAN, OccasionKind.EDUCATION, "دانش‌آموزان", "هدیه اقتصادی، عروسک کوچک، لوازم فانتزی و پک‌های گروهی", 14, 2),
        GiftOccasion("uni-student-ir-$year", "روز دانشجو", p(year,9,16), OccasionScope.IRAN, OccasionKind.EDUCATION, "دانشجو", "تراول‌ماگ، لوازم قهوه، فندک خاص، اکسسوری و پک کاربردی", 14, 2),
        GiftOccasion("yalda-$year", "شب یلدا", p(year,9,30), OccasionScope.IRAN, OccasionKind.FESTIVAL, "خانواده، زوج‌ها و دوستان", "باکس یلدایی، ماگ، عود و جاعودی، دکوری و هدیه قرمز", 30, 3),
        GiftOccasion("sepandar-$year", "سپندارمذگان؛ روز عشق ایرانی", p(year,12,5), OccasionScope.IRAN, OccasionKind.ROMANTIC, "همسر و پارتنر", "باکس عاشقانه، عروسک، ماگ، عود و هدیه‌های دونفره", 21, 3),
        GiftOccasion("eid-shopping-$year", "خرید هدیه و عیدی پایان سال", p(year,12,25), OccasionScope.IRAN, OccasionKind.SALES, "عمومی", "پک‌های آماده در چند بازه قیمتی و هدیه شرکتی", 35, 3)
    )

    private fun iranLunarForYear(year: Int): List<GiftOccasion> = when (year) {
        1405 -> listOf(
            GiftOccasion("fitr-1405", "عید فطر", p(1405,1,1), OccasionScope.IRAN, OccasionKind.RELIGIOUS, "خانواده و کودکان", "عیدی، شکلات و هدیه‌های کوچک خانوادگی", 10, 2),
            GiftOccasion("girl-1405", "روز دختر", p(1405,1,30), OccasionScope.IRAN, OccasionKind.CHILDREN, "دختران نوجوان و جوان", "عروسک، اکسسوری، ماگ، باکس هدیه و محصولات فانتزی", 21, 3),
            GiftOccasion("adha-1405", "عید قربان", p(1405,3,6), OccasionScope.IRAN, OccasionKind.RELIGIOUS, "خانواده", "هدیه خانوادگی و بسته‌های مناسب دید و بازدید", 10, 2),
            GiftOccasion("ghadir-1405", "عید غدیر", p(1405,3,14), OccasionScope.IRAN, OccasionKind.RELIGIOUS, "خانواده و دوستان", "هدیه و پک‌های اقتصادی برای دید و بازدید", 14, 3),
            GiftOccasion("mother-1405", "روز مادر و زن ایران", p(1405,9,9), OccasionScope.IRAN, OccasionKind.FAMILY, "مادر، همسر و بانوان", "باکس لوکس، ماگ و تراول‌ماگ، عود، دکوری و اکسسوری", 30, 3),
            GiftOccasion("boy-1405", "روز پسر", p(1405,9,29), OccasionScope.IRAN, OccasionKind.CHILDREN, "پسران نوجوان و جوان", "فیگور، اسباب‌بازی، فندک خاص، تراول‌ماگ و گجت", 21, 3),
            GiftOccasion("father-1405", "روز پدر و مرد ایران", p(1405,10,2), OccasionScope.IRAN, OccasionKind.FAMILY, "پدر، همسر و آقایان", "تجهیزات قهوه، فندک خاص، تراول‌ماگ و پک کاربردی", 30, 3)
        )
        1406 -> listOf(
            GiftOccasion("girl-1406", "روز دختر", p(1406,1,20), OccasionScope.IRAN, OccasionKind.CHILDREN, "دختران نوجوان و جوان", "عروسک، اکسسوری، ماگ، باکس هدیه و محصولات فانتزی", 21, 3),
            GiftOccasion("adha-1406", "عید قربان", p(1406,2,27), OccasionScope.IRAN, OccasionKind.RELIGIOUS, "خانواده", "هدیه خانوادگی و بسته‌های مناسب دید و بازدید", 10, 2),
            GiftOccasion("ghadir-1406", "عید غدیر", p(1406,3,4), OccasionScope.IRAN, OccasionKind.RELIGIOUS, "خانواده و دوستان", "هدیه و پک‌های اقتصادی برای دید و بازدید", 14, 3),
            GiftOccasion("mother-1406", "روز مادر و زن ایران", p(1406,8,28), OccasionScope.IRAN, OccasionKind.FAMILY, "مادر، همسر و بانوان", "باکس لوکس، ماگ و تراول‌ماگ، عود، دکوری و اکسسوری", 30, 3),
            GiftOccasion("boy-1406", "روز پسر", p(1406,9,17), OccasionScope.IRAN, OccasionKind.CHILDREN, "پسران نوجوان و جوان", "فیگور، اسباب‌بازی، فندک خاص، تراول‌ماگ و گجت", 21, 3),
            GiftOccasion("father-1406", "روز پدر و مرد ایران", p(1406,9,20), OccasionScope.IRAN, OccasionKind.FAMILY, "پدر، همسر و آقایان", "تجهیزات قهوه، فندک خاص، تراول‌ماگ و پک کاربردی", 30, 3),
            GiftOccasion("fitr-1406", "عید فطر", p(1406,12,7), OccasionScope.IRAN, OccasionKind.RELIGIOUS, "خانواده و کودکان", "عیدی، شکلات و هدیه‌های کوچک خانوادگی", 10, 2)
        )
        else -> emptyList()
    }

    private fun secondSunday(year: Int, month: Int) = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.SUNDAY))
    private fun thirdSunday(year: Int, month: Int) = LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY))
    private fun blackFriday(year: Int): LocalDate {
        val thanksgiving = LocalDate.of(year, 11, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.THURSDAY))
        return thanksgiving.plusDays(1)
    }

    private fun worldForGregorianYear(year: Int) = listOf(
        GiftOccasion("newyear-$year", "سال نو میلادی", LocalDate.of(year,1,1), OccasionScope.WORLD, OccasionKind.FESTIVAL, "عمومی", "هدیه‌های زمستانی، ماگ، دکوری و باکس جشن", 21, 2),
        GiftOccasion("valentine-$year", "ولنتاین", LocalDate.of(year,2,14), OccasionScope.WORLD, OccasionKind.ROMANTIC, "زوج‌ها و پارتنر", "باکس عاشقانه، عروسک، ماگ، اکسسوری و هدیه دونفره", 30, 3),
        GiftOccasion("women-$year", "روز جهانی زن", LocalDate.of(year,3,8), OccasionScope.WORLD, OccasionKind.FAMILY, "بانوان", "باکس هدیه، اکسسوری، ماگ و دکوری", 21, 3),
        GiftOccasion("book-$year", "روز جهانی کتاب", LocalDate.of(year,4,23), OccasionScope.WORLD, OccasionKind.SPECIAL, "دوستداران کتاب", "ماگ، بوکمارک، دکوری رومیزی و هدیه فرهنگی", 10, 1),
        GiftOccasion("mother-world-$year", "روز مادر جهانی", secondSunday(year,5), OccasionScope.WORLD, OccasionKind.FAMILY, "مادران", "باکس لوکس، ماگ، عود، اکسسوری و هدیه شخصی", 30, 3),
        GiftOccasion("family-$year", "روز جهانی خانواده", LocalDate.of(year,5,15), OccasionScope.WORLD, OccasionKind.FAMILY, "خانواده", "پک‌های خانوادگی و هدیه‌های مشترک", 14, 2),
        GiftOccasion("children-june-$year", "روز جهانی کودک؛ ۱ ژوئن", LocalDate.of(year,6,1), OccasionScope.WORLD, OccasionKind.CHILDREN, "کودکان", "اسباب‌بازی، عروسک، فیگور و هدیه فانتزی", 21, 2),
        GiftOccasion("father-world-$year", "روز پدر جهانی", thirdSunday(year,6), OccasionScope.WORLD, OccasionKind.FAMILY, "پدران", "تجهیزات قهوه، تراول‌ماگ، فندک خاص و گجت", 30, 3),
        GiftOccasion("chocolate-july-$year", "روز جهانی شکلات", LocalDate.of(year,7,7), OccasionScope.WORLD, OccasionKind.SPECIAL, "دوستان، زوج‌ها و خانواده", "باکس هدیه با شکلات، ماگ و هدیه‌های کوچک", 10, 2),
        GiftOccasion("friendship-$year", "روز جهانی دوستی", LocalDate.of(year,7,30), OccasionScope.WORLD, OccasionKind.SPECIAL, "دوستان", "هدیه‌های کوچک، ست دوستی، ماگ و اکسسوری", 14, 2),
        GiftOccasion("chocolate-sep-$year", "روز بین‌المللی شکلات", LocalDate.of(year,9,13), OccasionScope.WORLD, OccasionKind.SPECIAL, "عمومی", "باکس شکلاتی، ماگ، اکسسوری و هدیه اقتصادی", 10, 2),
        GiftOccasion("coffee-$year", "روز جهانی قهوه", LocalDate.of(year,10,1), OccasionScope.WORLD, OccasionKind.SPECIAL, "قهوه‌دوست‌ها و باریستاها", "پک باریستا، دم‌آوری، تراول‌ماگ و تجهیزات قهوه", 21, 3),
        GiftOccasion("teacher-world-$year", "روز جهانی معلم", LocalDate.of(year,10,5), OccasionScope.WORLD, OccasionKind.EDUCATION, "معلم و استاد", "ماگ، تراول‌ماگ، اکسسوری رومیزی و پک تشکر", 14, 2),
        GiftOccasion("halloween-$year", "هالووین", LocalDate.of(year,10,31), OccasionScope.WORLD, OccasionKind.FESTIVAL, "نوجوانان و جوانان", "فیگور، دکوری خاص، فندک و محصولات تم‌دار", 21, 2),
        GiftOccasion("singles-$year", "روز مجردها 11.11", LocalDate.of(year,11,11), OccasionScope.WORLD, OccasionKind.SALES, "عمومی", "کمپین تخفیف و پیشنهادهای تک‌محصولی", 14, 3),
        GiftOccasion("men-$year", "روز جهانی مرد", LocalDate.of(year,11,19), OccasionScope.WORLD, OccasionKind.FAMILY, "آقایان", "تجهیزات قهوه، فندک، تراول‌ماگ و گجت", 21, 2),
        GiftOccasion("children-nov-$year", "روز جهانی کودک؛ ۲۰ نوامبر", LocalDate.of(year,11,20), OccasionScope.WORLD, OccasionKind.CHILDREN, "کودکان", "اسباب‌بازی، عروسک و پک‌های کودک", 14, 2),
        GiftOccasion("blackfriday-$year", "بلک فرایدی", blackFriday(year), OccasionScope.WORLD, OccasionKind.SALES, "عمومی", "کمپین تخفیف گسترده، باندل و فروش موجودی پرفروش", 30, 3),
        GiftOccasion("christmas-$year", "کریسمس", LocalDate.of(year,12,25), OccasionScope.WORLD, OccasionKind.FESTIVAL, "خانواده، دوستان و زوج‌ها", "باکس زمستانی، ماگ، دکوری و هدیه‌های قرمز و سبز", 30, 3),
        GiftOccasion("newyear-eve-$year", "شب سال نو میلادی", LocalDate.of(year,12,31), OccasionScope.WORLD, OccasionKind.FESTIVAL, "عمومی", "هدیه جشن، ماگ، دکوری و پک دوستانه", 14, 2)
    )

    fun forPersianYear(year: Int): List<GiftOccasion> {
        val start = Jalali.toGregorian(year,1,1)
        val end = Jalali.toGregorian(year+1,1,1).minusDays(1)
        val worlds = (start.year..end.year).flatMap(::worldForGregorianYear).filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        return (fixedPersian(year) + iranLunarForYear(year) + worlds)
            .distinctBy { it.id }
            .sortedBy { it.date }
    }

    fun upcoming(from: LocalDate = LocalDate.now(ZoneId.of("Asia/Tehran")), count: Int = 5): List<GiftOccasion> {
        val jy = Jalali.fromGregorian(from.year, from.monthValue, from.dayOfMonth).year
        return (forPersianYear(jy) + forPersianYear(jy + 1))
            .distinctBy { it.id }
            .filter { !it.date.isBefore(from) }
            .sortedBy { it.date }
            .take(count)
    }
}

private fun kindLabel(kind: OccasionKind) = when(kind) {
    OccasionKind.FAMILY -> "خانوادگی"
    OccasionKind.ROMANTIC -> "عاشقانه"
    OccasionKind.CHILDREN -> "کودک و نوجوان"
    OccasionKind.EDUCATION -> "آموزشی"
    OccasionKind.RELIGIOUS -> "عید و مذهبی"
    OccasionKind.FESTIVAL -> "جشن"
    OccasionKind.SALES -> "کمپین فروش"
    OccasionKind.SPECIAL -> "مناسبت ویژه"
}

private fun kindColor(kind: OccasionKind): Color = when(kind) {
    OccasionKind.ROMANTIC -> DinalRose
    OccasionKind.SALES -> DinalGold
    OccasionKind.CHILDREN -> DinalMint
    OccasionKind.FAMILY -> DinalPurple
    OccasionKind.RELIGIOUS -> Color(0xFF2E7D6E)
    OccasionKind.EDUCATION -> Color(0xFF4F66B1)
    OccasionKind.FESTIVAL -> Color(0xFF9C5A2E)
    OccasionKind.SPECIAL -> Color(0xFF6C5B7B)
}

@Composable
fun OccasionsScreen(nav: NavHostController) {
    val now = remember { LocalDate.now(ZoneId.of("Asia/Tehran")) }
    val todayJ = remember { Jalali.fromGregorian(now.year, now.monthValue, now.dayOfMonth) }
    var year by remember { mutableIntStateOf(todayJ.year) }
    var month by remember { mutableIntStateOf(todayJ.month) }
    var scopeFilter by remember { mutableStateOf("همه") }
    var onlyImportant by remember { mutableStateOf(false) }

    val all = remember(year) { GiftOccasionCatalog.forPersianYear(year) }
    val shown = remember(all, month, scopeFilter, onlyImportant) {
        all.filter { o ->
            o.persian.month == month &&
                (scopeFilter == "همه" || (scopeFilter == "ایران" && o.scope == OccasionScope.IRAN) || (scopeFilter == "جهان" && o.scope == OccasionScope.WORLD)) &&
                (!onlyImportant || o.priority >= 3)
        }
    }
    val next = remember(now) { GiftOccasionCatalog.upcoming(now, 1).firstOrNull() }

    DinalScreen(nav, "مناسبت‌های هدیه و فروش") { pad ->
        LazyColumn(
            modifier = Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(14.dp, 8.dp, 14.dp, 34.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DinalHero("تقویم فرصت‌های فروش", "مناسبت‌های ایران و جهان برای برنامه‌ریزی کادو و کمپین") {
                    Icon(Icons.Rounded.Celebration, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp))
                }
            }
            next?.let { n ->
                item {
                    val days = ChronoUnit.DAYS.between(now, n.date).coerceAtLeast(0)
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.dp, DinalGold.copy(alpha = .35f)),
                        colors = CardDefaults.cardColors(containerColor = DinalGold.copy(alpha = .10f))
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.NotificationsActive, null, tint = DinalGold)
                                Spacer(Modifier.width(8.dp))
                                Text("نزدیک‌ترین فرصت فروش", fontWeight = FontWeight.Bold)
                            }
                            Text(n.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("${n.persianText} • ${if(days == 0L) "امروز" else "$days روز دیگر"}")
                            Text("پیشنهاد: ${n.giftHint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item {
                SectionCard("سال و ماه") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        IconButton({ year-- }) { Icon(Icons.Rounded.ChevronRight, "سال قبل") }
                        Text("سال $year", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton({ year++ }) { Icon(Icons.Rounded.ChevronLeft, "سال بعد") }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        (1..12).forEach { m ->
                            FilterChip(
                                selected = month == m,
                                onClick = { month = m },
                                label = { Text(Jalali.monthName(m)) }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("همه", "ایران", "جهان").forEach { f ->
                            FilterChip(selected = scopeFilter == f, onClick = { scopeFilter = f }, label = { Text(f) })
                        }
                        FilterChip(
                            selected = onlyImportant,
                            onClick = { onlyImportant = !onlyImportant },
                            label = { Text("فقط مهم‌ها") },
                            leadingIcon = if (onlyImportant) {{ Icon(Icons.Rounded.Star, null, Modifier.size(17.dp)) }} else null
                        )
                    }
                }
            }
            item {
                val important = shown.count { it.priority >= 3 }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("مناسبت این ماه", shown.size.toString(), DinalPurple, Modifier.weight(1f))
                    MetricCard("فرصت مهم فروش", important.toString(), DinalGold, Modifier.weight(1f))
                }
            }
            if (shown.isEmpty()) {
                item {
                    SectionCard("مناسبتی پیدا نشد") {
                        Text("برای این فیلتر در ${Jalali.monthName(month)} موردی ثبت نشده. فیلتر را روی «همه» بگذار یا ماه دیگری انتخاب کن.")
                        if (year !in 1405..1406) {
                            Text("مناسبت‌های قمری ایران برای این سال در نسخه فعلی ثبت نشده‌اند؛ مناسبت‌های ثابت شمسی و جهانی همچنان نمایش داده می‌شوند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                shown.forEach { occasion ->
                    item(key = occasion.id) { OccasionCard(occasion, now) }
                }
            }
            item {
                SectionCard("راهنمای برنامه‌ریزی", subtitle = "برای فروشگاه کادو، شروع کمپین چند روز زودتر از خود مناسبت مهم است") {
                    Text("• مناسبت‌های خیلی مهم: موجودی و باکس‌ها را ۳ تا ۴ هفته قبل آماده کن.")
                    Text("• مناسبت‌های متوسط: ۱۰ تا ۱۴ روز قبل تبلیغ و ویترین را شروع کن.")
                    Text("• تاریخ‌های قمری ایران سال‌به‌سال جابه‌جا می‌شوند و در نسخه‌های سالانه StoreHub به‌روزرسانی می‌شوند.")
                }
            }
        }
    }
}

@Composable
private fun OccasionCard(o: GiftOccasion, now: LocalDate) {
    val accent = kindColor(o.kind)
    val days = ChronoUnit.DAYS.between(now, o.date)
    Card(
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = .22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = .13f)) {
                    Icon(
                        when(o.kind) {
                            OccasionKind.ROMANTIC -> Icons.Rounded.Favorite
                            OccasionKind.SALES -> Icons.Rounded.LocalOffer
                            OccasionKind.CHILDREN -> Icons.Rounded.Toys
                            OccasionKind.EDUCATION -> Icons.Rounded.School
                            OccasionKind.RELIGIOUS -> Icons.Rounded.AutoAwesome
                            OccasionKind.FAMILY -> Icons.Rounded.FamilyRestroom
                            OccasionKind.FESTIVAL -> Icons.Rounded.Celebration
                            OccasionKind.SPECIAL -> Icons.Rounded.CardGiftcard
                        },
                        null,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${o.persianText} • ${o.date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (o.priority >= 3) Icon(Icons.Rounded.Star, "مهم", tint = DinalGold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AssistChip(onClick = {}, label = { Text(if(o.scope == OccasionScope.IRAN) "ایران" else "جهان") })
                AssistChip(onClick = {}, label = { Text(kindLabel(o.kind)) })
                if (days >= 0 && days <= 60) AssistChip(onClick = {}, label = { Text(if(days == 0L) "امروز" else "$days روز مانده") })
            }
            HorizontalDivider(color = accent.copy(alpha = .14f))
            Text("مخاطب: ${o.audience}", fontWeight = FontWeight.SemiBold)
            Text("ایده هدیه: ${o.giftHint}")
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Campaign, null, tint = accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("شروع پیشنهادی کمپین: ${o.campaignLeadDays} روز قبل", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
