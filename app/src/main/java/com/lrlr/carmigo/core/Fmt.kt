package com.lrlr.carmigo.core

import java.time.LocalDate
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToLong

/** 화면과 문장에 쓰는 숫자·날짜 표기. */
object Fmt {
    /** 차 이름에 주격 조사를 붙인다("콩이가", "은별이"). 이름이 없으면 "이 차가". */
    fun subject(name: String?): String {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return "이 차가"
        val ch = n.last()
        val closed = when {
            ch in '가'..'힣' -> (ch - '가') % 28 != 0
            ch.isDigit() -> ch in "013678"
            else -> ch in "lmnLMN"
        }
        return n + if (closed) "이" else "가"
    }

    /** 차 이름에 "와/과"를 붙인다("흰둥이와", "은별과"). 이름이 없으면 "이 차와". */
    fun with(name: String?): String {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return "이 차와"
        val ch = n.last()
        val closed = when {
            ch in '가'..'힣' -> (ch - '가') % 28 != 0
            ch.isDigit() -> ch in "013678"
            else -> ch in "lmnLMN"
        }
        return n + if (closed) "과" else "와"
    }

    /** 입력한 차 이름을 다듬는다. 비었으면 null. */
    fun cleanName(name: String?): String? = name?.trim()?.take(NAME_MAX)?.ifEmpty { null }
    const val NAME_MAX = 12

    fun n(v: Long): String = String.format(Locale.KOREA, "%,d", v)
    fun n(v: Int): String = n(v.toLong())
    /** km는 내림해서 정수로(아직 달리지 않은 거리를 달렸다고 하지 않는다) */
    fun km(v: Double): String = n(floor(v + 1e-6).toLong())
    fun km1(v: Double): String = String.format(Locale.KOREA, "%,.1f", floor(v * 10 + 1e-6) / 10)
    fun kwh(wh: Double): String = n(floor(wh / 1000 + 1e-6).toLong())

    private const val WEEK = "월화수목금토일"
    /** 2026. 11. 14 */
    fun date(d: LocalDate): String = "${d.year}. ${d.monthValue}. ${d.dayOfMonth}"
    /** 2026. 11. 14 토 */
    fun dateDow(d: LocalDate): String = "${date(d)} ${WEEK[d.dayOfWeek.value - 1]}"
    fun hm(minuteOfDay: Int): String = String.format(Locale.KOREA, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)
}
