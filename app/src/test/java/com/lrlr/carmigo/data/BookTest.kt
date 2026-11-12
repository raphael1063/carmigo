package com.lrlr.carmigo.data

import com.lrlr.carmigo.core.AppState
import com.lrlr.carmigo.core.Counters
import com.lrlr.carmigo.core.LastDrive
import com.lrlr.carmigo.core.Moment
import com.lrlr.carmigo.core.MomentKind
import com.lrlr.carmigo.core.Rules
import com.lrlr.carmigo.core.Setup
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class BookTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val rules = Rules(zone)
    private fun at(date: String, h: Int, m: Int) = LocalDate.parse(date).atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    private val state = AppState(
        setup = Setup(LocalDate.parse("2023-07-30").toEpochDay(), 38_120.0, LocalDate.parse("2026-07-05").toEpochDay(), 72_963.0),
        counters = Counters(arrivals = 636, companionDays = 80, fullDays = 9, firstFullDay = LocalDate.parse("2026-08-02").toEpochDay(), energyWh = 810_400.0),
        moments = listOf(
            Moment("odo-77777", MomentKind.ODOMETER, "77,777 km", "같은 숫자 다섯 개가 나란히 선 순간", "다음 계기판 숫자는 80,000km입니다.",
                at("2026-11-14", 17, 8), odoKm = 77_777.0, outsideTempC = 6f, seats = 3),
            Moment("days-1200", MomentKind.DAYS, "함께한 지 1,200일", "이 차를 받은 지 1,200일", "차를 받은 날을 한 번 알려주면 차가 날짜를 셉니다.",
                at("2026-11-11", 12, 0), hasTime = false),
        ),
        lastOdoKm = 78_157.4,
    )

    @Test fun bookCarriesOnlyDatesNumbersAndMoments() {
        val b = Books.build(state, rules, at("2026-11-20", 9, 0))!!
        assertEquals("2023-07-30", b.receivedDate)
        assertEquals("2026-11-20", b.untilDate)
        assertEquals(1209, b.days)
        assertEquals(40_037.4, b.kmTogether!!, 0.001)
        assertFalse(b.isLast)
        assertEquals(810L, b.energyKwh)
        assertEquals("2026-08-02", b.firstFullDate)
        assertEquals("17:08", b.moments[0].time)
        assertEquals(1028, b.moments[0].minuteOfDay)
        // 날짜만 아는 순간에는 시각을 적지 않는다
        assertNull(b.moments[1].time)
        assertNull(b.moments[1].minuteOfDay)
        val json = AppJson.encodeToString(b)
        assertFalse(json.contains("writeKey"))
        assertFalse(json.contains("null"))
    }

    @Test fun lastDriveBookEndsOnTheLastDay() {
        val done = state.copy(last = LastDrive.DONE, lastDoneAt = at("2026-12-03", 18, 30))
        val b = Books.build(done, rules, at("2027-01-10", 9, 0))!!
        assertTrue(b.isLast)
        assertEquals("2026-12-03", b.untilDate)
        assertEquals(1222, b.days)
    }

    @Test fun nothingToSendBeforeTheFirstInput() {
        assertNull(Books.build(AppState(), rules, 0))
    }

    @Test fun stateSurvivesSaveAndLoad() {
        val text = AppJson.encodeToString(state)
        assertEquals(state, AppJson.decodeFromString<AppState>(text))
    }
}
