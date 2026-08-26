package ir.dinal.storehub.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object Jalali {
    data class Date(val year:Int,val month:Int,val day:Int)
    fun today():Date { val d=LocalDate.now(ZoneId.of("Asia/Tehran")); return fromGregorian(d.year,d.monthValue,d.dayOfMonth) }
    fun format(d:Date)="%04d/%02d/%02d".format(d.year,d.month,d.day)
    fun parse(s:String):Date?=runCatching{val p=s.trim().split('/','-'); Date(p[0].toInt(),p[1].toInt(),p[2].toInt())}.getOrNull()

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
        val d=parse(persian)?:error("تاریخ شمسی نامعتبر است");val t=time.trim().split(':');val h=t.getOrNull(0)?.toIntOrNull()?:0;val m=t.getOrNull(1)?.toIntOrNull()?:0
        return toGregorian(d.year,d.month,d.day).atTime(h,m).atZone(ZoneId.of("Asia/Tehran")).toInstant().toEpochMilli()
    }
    fun persianFromEpochMillis(ms:Long):String{val z=ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms),ZoneId.of("Asia/Tehran"));return format(fromGregorian(z.year,z.monthValue,z.dayOfMonth))}
}
