package ir.dinal.storehub.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object Jalali {
    data class Date(val year:Int,val month:Int,val day:Int)
    fun today():Date { val d=LocalDate.now(ZoneId.of("Asia/Tehran")); return fromGregorian(d.year,d.monthValue,d.dayOfMonth) }


    private val monthNames = listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
    fun monthName(month:Int):String = monthNames.getOrElse(month-1) { "" }
    fun isLeapYear(year:Int):Boolean = toGregorian(year+1,1,1).toEpochDay() - toGregorian(year,1,1).toEpochDay() == 366L
    fun daysInMonth(year:Int,month:Int):Int = when(month){ in 1..6 -> 31; in 7..11 -> 30; 12 -> if(isLeapYear(year))30 else 29; else -> 30 }
    fun firstDaySaturdayOffset(year:Int,month:Int):Int {
        val dow = toGregorian(year,month,1).dayOfWeek.value
        return (dow + 1) % 7
    }
    fun format(d:Date)="%04d/%02d/%02d".format(d.year,d.month,d.day)
    fun parse(s:String):Date? = runCatching {
        val p=s.trim().split('/','-')
        if(p.size!=3) return@runCatching null
        val y=p[0].toInt(); val m=p[1].toInt(); val d=p[2].toInt()
        if(y !in 1200..1700 || m !in 1..12 || d !in 1..daysInMonth(y,m)) null else Date(y,m,d)
    }.getOrNull()

    fun fromGregorian(gyInput:Int, gm:Int, gd:Int):Date {
        val gdm=intArrayOf(0,31,59,90,120,151,181,212,243,273,304,334);var gy=gyInput;var jy:Int
        if(gy>1600){jy=979;gy-=1600}else{jy=0;gy-=621};val gy2=if(gm>2)gy+1 else gy
        var days=365*gy+(gy2+3)/4-(gy2+99)/100+(gy2+399)/400-80+gd+gdm[gm-1]
        jy+=33*(days/12053);days%=12053;jy+=4*(days/1461);days%=1461
        if(days>365){jy+=(days-1)/365;days=(days-1)%365}
        val jm:Int;val jd:Int;if(days<186){jm=1+days/31;jd=1+days%31}else{jm=7+(days-186)/30;jd=1+(days-186)%30};return Date(jy,jm,jd)
    }

    fun toGregorian(jyInput:Int,jm:Int,jd:Int):LocalDate {
        var jy=jyInput;var gy:Int
        if(jy>979){gy=1600;jy-=979}else{gy=621}
        var days=365*jy+(jy/33)*8+((jy%33)+3)/4+78+jd+(if(jm<7)(jm-1)*31 else (jm-7)*30+186)
        gy+=400*(days/146097);days%=146097
        if(days>36524){gy+=100*((--days)/36524);days%=36524;if(days>=365)days++}
        gy+=4*(days/1461);days%=1461
        if(days>365){gy+=(days-1)/365;days=(days-1)%365}
        var gd=days+1
        val leap=gy%4==0&&(gy%100!=0||gy%400==0)
        val salA=intArrayOf(0,31,if(leap)29 else 28,31,30,31,30,31,31,30,31,30,31)
        var gm=1
        while(gm<=12 && gd>salA[gm]){gd-=salA[gm];gm++}
        return LocalDate.of(gy,gm,gd)
    }

    fun epochDay(persian:String):Long { val d=parse(persian)?:error("تاریخ شمسی نامعتبر است"); return toGregorian(d.year,d.month,d.day).toEpochDay() }
    fun epochMillis(persian:String,time:String):Long {
        val d=parse(persian)?:error("تاریخ شمسی نامعتبر است")
        val t=time.trim().split(':'); val h=t.getOrNull(0)?.toIntOrNull(); val m=t.getOrNull(1)?.toIntOrNull()
        require(h != null && m != null && h in 0..23 && m in 0..59) { "ساعت باید مثل 14:30 و معتبر باشد." }
        return toGregorian(d.year,d.month,d.day).atTime(h,m).atZone(ZoneId.of("Asia/Tehran")).toInstant().toEpochMilli()
    }
    fun persianFromEpochMillis(ms:Long):String{val z=ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms),ZoneId.of("Asia/Tehran"));return format(fromGregorian(z.year,z.monthValue,z.dayOfMonth))}
}
