package com.lrlr.carmigo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RulesTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val r = Rules(seoul)
    private fun at(y: Int, m: Int, d: Int, hh: Int = 12, mm: Int = 0) =
        LocalDateTime.of(y, m, d, hh, mm).atZone(seoul).toInstant().toEpochMilli()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()
    private val t0 = at(2026, 7, 5)
    /** 2023-07-30에 38,120km로 받은 차, 2026-07-05에 72,963km에서 설치 */
    private fun fresh(odo: Double = 72_963.0) = r.begin(AppState(), day(2023, 7, 30), 38_120.0, odo, t0)
    private val four = Seats(true, true, true, true)
    private val alone = Seats(true, false, false, false)

    @Test fun daysAndDistanceComeFromTheTwoInputs() {
        val s = fresh()
        assertEquals(1071, r.daysTogether(s.setup!!, t0))
        assertEquals(34_843.0, r.kmTogether(s.setup!!, 72_963.0)!!, 0.001)
        assertEquals(1, r.watchedDays(s.setup!!, t0))
        assertNull(r.kmTogether(s.setup!!, null))
    }

    @Test fun inputsAreClampedToTodayAndCurrentOdometer() {
        val s = r.begin(AppState(), day(2030, 1, 1), 99_999.0, 500.0, t0)
        assertEquals(0, r.daysTogether(s.setup!!, t0))
        assertEquals(0.0, r.kmTogether(s.setup!!, 500.0)!!, 0.001)
    }

    @Test fun nextNumberIsOnlyRareNumbers() {
        assertEquals(77_777, r.nextNumber(77_774.2))
        assertEquals(88_888, r.nextNumber(77_777.0)) // 80,000 같은 만 단위는 세지 않는다
        assertEquals(12_345, r.nextNumber(11_111.0))
        assertEquals(22_222, r.nextNumber(12_345.0))
        assertEquals(56_789, r.nextNumber(55_555.0))
        assertEquals(100_000, r.nextNumber(99_999.0))
        assertEquals(111_111, r.nextNumber(100_000.0))
        assertEquals(123_456, r.nextNumber(111_111.0))
        assertEquals("같은 숫자 다섯 개가 나란히 선 순간", r.numberLine(77_777))
        assertEquals("계기판 숫자가 1부터 5까지 차례로 선 순간", r.numberLine(12_345))
        assertEquals("계기판이 10만 km가 된 순간", r.numberLine(100_000))
    }

    @Test fun odometerNumberIsAnnouncedOnceThenRecordedWhenPassed() {
        var s = fresh(77_773.0)
        s = r.onGear(s, false, 77_773.0, alone, Snapshot(t0)).state
        val a = r.onOdometer(s, 77_774.2, Snapshot(t0, seats = 3, outsideTempC = 6f))
        assertEquals("약 3킬로미터 뒤에 계기판이 77,777킬로미터가 됩니다", a.speak)
        assertEquals("계기판 77,777 km까지 2.8 km", r.drivingLine(a.state, t0))
        val b = r.onOdometer(a.state, 77_775.0, Snapshot(t0))
        assertNull(b.speak) // 한 번만
        val c = r.onOdometer(b.state, 77_777.1, Snapshot(t0, seats = 3, outsideTempC = 6f))
        assertEquals(listOf("odo-77777"), c.added.map { it.id })
        assertEquals(3, c.added[0].seats)
        assertEquals("계기판이 77,777 km를 지났습니다", r.drivingLine(c.state, t0))
        // 같은 숫자를 두 번 남기지 않는다
        assertTrue(r.onOdometer(c.state.copy(lastOdoKm = 77_776.0), 77_777.5, Snapshot(t0)).added.isEmpty())
    }

    @Test fun togetherMilestoneIsAnnouncedOnceThenRecordedWhenPassed() {
        // 38,120km로 받은 차: 함께 40,000km는 계기판 78,120km
        var s = r.correctSetup(fresh(78_110.0), day(2023, 7, 30), 38_120.0, t0, "흰둥이")
        s = r.onGear(s, false, 78_110.0, alone, Snapshot(t0)).state
        assertEquals("40,000 km까지 10.0 km", r.drivingLine(s.copy(lastOdoKm = 78_110.0), t0))
        val a = r.onOdometer(r.onOdometer(s, 78_115.0, Snapshot(t0)).state, 78_117.0, Snapshot(t0))
        assertEquals("약 3킬로미터 뒤에 흰둥이와 함께 달린 거리가 40,000킬로미터가 됩니다", a.speak)
        assertEquals("40,000 km까지 3.0 km", r.drivingLine(a.state, t0))
        assertNull(r.onOdometer(a.state, 78_118.0, Snapshot(t0)).speak) // 한 번만
        val c = r.onOdometer(a.state, 78_120.4, Snapshot(t0))
        assertEquals(listOf("dist-40000"), c.added.map { it.id })
        assertEquals("40,000 km를 넘었습니다", r.drivingLine(c.state, t0))
    }

    @Test fun earthAndMoonSpeechDescribeEquivalentDistance() {
        for ((target, phrase) in listOf(40_075.0 to "지구 한 바퀴만큼", 384_400.0 to "달까지의 거리만큼")) {
            var s = r.correctSetup(fresh(target - 10), day(2023, 7, 30), 0.0, t0, "흰둥이")
            s = r.onGear(s, false, target - 10, alone, Snapshot(t0)).state
            s = r.onOdometer(s, target - 5, Snapshot(t0)).state
            val step = r.onOdometer(s, target - 3, Snapshot(t0))
            assertEquals("약 3킬로미터 뒤에 흰둥이와 $phrase 함께 달리게 됩니다", step.speak)
            assertNull(r.onOdometer(step.state, target - 2, Snapshot(t0)).speak)
        }
    }

    @Test fun nextTogetherIsTheNearestMilestone() {
        assertEquals(40_000, r.nextTogether(39_996.6))
        assertEquals(40_075, r.nextTogether(40_000.0))
        assertEquals(50_000, r.nextTogether(40_075.0))
        assertEquals(384_400, r.nextTogether(380_000.0))
        assertEquals(390_000, r.nextTogether(384_400.0))
        assertEquals("지구 두 바퀴", r.nearTogetherLabel(80_150))
        assertEquals("지구 한 바퀴만큼 함께 달렸습니다", r.passedTogetherLine(40_075))
        assertEquals("이 차와", Fmt.with(null))
        assertEquals("은별과", Fmt.with("은별"))
    }

    @Test fun jumpsWhileTheAppWasOffAreNotRecordedAsMoments() {
        val s = fresh(77_000.0)
        val step = r.onOdometer(s, 80_500.0, Snapshot(t0))
        assertTrue(step.added.isEmpty())
        assertEquals(80_500.0, step.state.lastOdoKm!!, 0.0)
    }

    @Test fun distanceTogetherMilestoneAndEarth() {
        // 함께 35,000km(계기판 73,120)는 남기지 않는다. 10,000km 단위만.
        var s = fresh(73_119.0)
        assertTrue(r.onOdometer(s, 73_120.4, Snapshot(t0)).added.isEmpty())
        // 함께 40,000km = 계기판 78,120
        s = fresh(78_119.0)
        val a = r.onOdometer(s, 78_120.4, Snapshot(t0))
        assertEquals("40,000 km", a.added.single().title)
        assertEquals("함께한 지 1,071일째", a.added.single().line)
        // 지구 한 바퀴 = 38,120 + 40,075 = 78,195 (함께 40,000도 그 전에 지남)
        s = fresh(78_194.0)
        val b = r.onOdometer(s, 78_195.2, Snapshot(t0))
        assertEquals(listOf("dist-earth"), b.added.map { it.id })
        assertEquals("지구 한 바퀴", b.added.single().title)
        // 지구 두 바퀴 = 38,120 + 80,150 = 118,270, 세 바퀴 = 158,345
        s = fresh(118_269.0)
        val c = r.onOdometer(s, 118_270.5, Snapshot(t0))
        assertEquals("지구 두 바퀴", c.added.single { it.id == "dist-earth-2" }.title)
        s = fresh(158_344.0)
        assertEquals("지구 세 바퀴", r.onOdometer(s, 158_345.5, Snapshot(t0)).added.single { it.id == "dist-earth-3" }.title)
        assertEquals("12", r.lapWord(12))
    }

    @Test fun arrivalNeedsTenMinutesInPark() {
        var s = fresh()
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 600_000)).state
        assertEquals(0, s.counters.arrivals)
        s = r.onTick(s, t0 + 600_000 + 9 * 60_000).state
        assertEquals(0, s.counters.arrivals)
        s = r.onTick(s, t0 + 600_000 + 10 * 60_000).state
        assertEquals(1, s.counters.arrivals)
        s = r.onTick(s, t0 + 600_000 + 30 * 60_000).state
        assertEquals(1, s.counters.arrivals) // 한 번만
    }

    @Test fun shortStopIsNotAnArrival() {
        var s = fresh()
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, null, alone, Snapshot(t0 + 60_000)).state
        s = r.onGear(s, false, null, alone, Snapshot(t0 + 120_000)).state
        s = r.onTick(s, t0 + 3_600_000).state
        assertEquals(0, s.counters.arrivals)
    }

    @Test fun companionDaysCountOncePerDayAndFiftiethBecomesAMoment() {
        var s = fresh().let { it.copy(counters = it.counters.copy(companionDays = 49)) }
        val two = Seats(true, true, false, false)
        s = r.onGear(s, false, null, two, Snapshot(t0)).state
        val end = r.onGear(s, true, null, two, Snapshot(t0 + 1000))
        assertEquals(50, end.state.counters.companionDays)
        assertEquals("옆자리에 누군가 탄 50번째 날", end.added.single().title)
        // 같은 날 다시 타도 하루
        var again = r.onGear(end.state, false, null, two, Snapshot(t0 + 2000)).state
        again = r.onGear(again, true, null, two, Snapshot(t0 + 3000)).state
        assertEquals(50, again.counters.companionDays)
    }

    @Test fun passengerWhoGetsInMidDriveCounts() {
        var s = fresh()
        s = r.onGear(s, false, null, alone, Snapshot(t0)).state
        s = r.onSeats(s, Seats(true, true, false, false)).state
        s = r.onSeats(s, alone).state
        s = r.onGear(s, true, null, alone, Snapshot(t0 + 1000)).state
        assertEquals(1, s.counters.companionDays)
    }

    @Test fun firstFullCarIsAMomentOnlyOnce() {
        var s = fresh()
        s = r.onGear(s, false, null, four, Snapshot(t0)).state
        val end = r.onGear(s, true, null, four, Snapshot(t0 + 1000))
        assertEquals("네 자리가 다 찬 날", end.added.single().title)
        assertEquals(1, end.state.counters.fullDays)
        val next = t0 + 86_400_000
        var s2 = r.onGear(end.state, false, null, four, Snapshot(next)).state
        val end2 = r.onGear(s2, true, null, four, Snapshot(next + 1000))
        assertTrue(end2.added.isEmpty())
        assertEquals(2, end2.state.counters.fullDays)
    }

    @Test fun unreadSeatsCountNothing() {
        var s = fresh()
        s = r.onGear(s, false, null, Seats(), Snapshot(t0)).state
        s = r.onGear(s, true, null, Seats(), Snapshot(t0 + 1000)).state
        assertEquals(0, s.counters.companionDays)
        assertEquals(0, s.counters.fullDays)
        assertNull(Seats().count)
    }

    @Test fun hundredPointZeroKmDrive() {
        var s = fresh(75_858.3)
        s = r.onGear(s, false, 75_858.3, alone, Snapshot(t0)).state
        val end = r.onGear(s, true, 75_958.3, alone, Snapshot(t0 + 1000))
        assertEquals("100.0 km 주행", end.added.single().title)
        // 1km 단위로만 오는 차에서는 만들지 않는다
        var w = fresh(75_858.0)
        w = r.onGear(w, false, 75_858.0, alone, Snapshot(t0)).state
        assertTrue(r.onGear(w, true, 75_958.0, alone, Snapshot(t0 + 1000)).added.isEmpty())
    }

    @Test fun energyAddsOnlyDecreases() {
        var s = fresh()
        s = r.onBattery(s, 60_000.0).state
        s = r.onBattery(s, 59_200.0).state
        s = r.onBattery(s, 70_000.0).state // 충전
        s = r.onBattery(s, 69_500.0).state
        assertEquals(1_300.0, s.counters.energyWh, 0.001)
        // 첫 입력 전에는 더하지 않는다
        var n = r.onBattery(AppState(), 60_000.0).state
        n = r.onBattery(n, 50_000.0).state
        assertEquals(0.0, n.counters.energyWh, 0.0)
    }

    @Test fun dayMilestoneOnlyAfterInstall() {
        // 2023-07-30에 받아 2026-07-05(1,071일)에 설치. 100일, 1,000일, 1년, 2년은 설치 전이라 남기지 않는다.
        val s = fresh()
        assertTrue(r.checkDays(s, t0).added.isEmpty())
        // 1,100일째(100일 단위)는 더는 남기지 않는다
        assertTrue(r.checkDays(s, t0 + 24L * 86_400_000).added.isEmpty())
        // 받은 날이 세 번째로 돌아온 2026-07-30
        val later = r.checkDays(s, t0 + 25L * 86_400_000)
        assertEquals("3년", later.added.single().title)
        assertFalse(later.added.single().hasTime)
        assertTrue(r.checkDays(later.state, t0 + 30L * 86_400_000).added.isEmpty())
    }

    @Test fun lastDriveFlow() {
        var s = fresh()
        s = r.armLast(s)
        assertEquals(LastDrive.ARMED, s.last)
        assertEquals(s, r.armLast(s)) // 두 번 눌러도 그대로
        assertEquals(LastDrive.NONE, r.disarmLast(s).last)
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        assertEquals(LastDrive.DRIVING, s.last)
        assertEquals(s, r.armLast(s)) // 주행 중에는 눌리지 않는다
        assertEquals("마지막 주행 · 함께한 지 1,071일째", r.drivingLine(s, t0))
        s = r.onOdometer(s, 72_970.0, Snapshot(t0 + 500)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 1000)).state
        assertEquals(LastDrive.DONE, s.last)
        assertEquals(1, s.counters.arrivals) // 즉시 도착으로 센다
        // 10분이 되기 전에 되돌리면 그 도착은 다른 도착처럼 10분을 기다린다
        val undone = r.undoLast(s, t0 + 2000)
        assertEquals(LastDrive.NONE, undone.last)
        assertEquals(0, undone.counters.arrivals)
        assertEquals(1, r.onTick(undone, t0 + 11 * 60_000).state.counters.arrivals)
        // 10분 넘게 머문 뒤에 되돌리면 도착은 그대로다
        assertEquals(1, r.undoLast(s, t0 + 11 * 60_000).counters.arrivals)
    }

    @Test fun lastDriveContinuesIfTheCarMovesAgain() {
        var s = r.armLast(fresh())
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 60_000)).state // 가는 길에 잠깐 세움
        assertEquals(LastDrive.DONE, s.last)
        // 마친 뒤에는 주행거리와 전기를 더하지 않는다
        assertEquals(s, r.onOdometer(s, 72_971.0, Snapshot(t0 + 61_000)).state)
        s = r.onGear(s, false, 72_970.0, alone, Snapshot(t0 + 90_000)).state
        assertEquals(LastDrive.DONE, s.last) // 움직이기 전에는 그대로
        s = r.onOdometer(s, 72_970.5, Snapshot(t0 + 95_000)).state
        assertEquals(LastDrive.DRIVING, s.last)
        assertNull(s.lastDoneAt)
        assertEquals(0, s.counters.arrivals) // 잠깐 세운 것은 도착이 아니었다
        s = r.onGear(s, true, 72_990.0, alone, Snapshot(t0 + 3_600_000)).state
        assertEquals(LastDrive.DONE, s.last)
        assertEquals(t0 + 3_600_000, s.lastDoneAt)
        assertEquals(1, s.counters.arrivals)
    }

    @Test fun shiftingWithoutMovingAfterTheLastDriveChangesNothing() {
        var s = r.armLast(fresh())
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_990.0, alone, Snapshot(t0 + 3_600_000)).state
        val done = s
        assertEquals(LastDrive.DONE, done.last)
        // 2분 뒤 R에 넣었다 다시 P. 계기판은 그대로.
        s = r.onGear(s, false, 72_990.0, alone, Snapshot(t0 + 3_720_000)).state
        assertTrue(r.sameBook(done, s))
        s = r.onGear(s, true, 72_990.0, alone, Snapshot(t0 + 3_725_000)).state
        assertEquals(done, s)
    }

    @Test fun shiftingWithoutMovingKeepsAPendingArrival() {
        var s = fresh()
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 600_000)).state
        // 8분째에 D에 넣었다 바로 P
        s = r.onGear(s, false, 72_970.0, alone, Snapshot(t0 + 600_000 + 8 * 60_000)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 600_000 + 8 * 60_000 + 5_000)).state
        assertEquals(1, r.onTick(s, t0 + 600_000 + 11 * 60_000).state.counters.arrivals)
    }

    @Test fun aLongDriveWithAnUnchangedWholeKmOdometerStillCounts() {
        var s = fresh()
        s = r.onGear(s, false, 72_963.0, Seats(true, true, false, false), Snapshot(t0)).state
        s = r.onGear(s, true, 72_963.0, alone, Snapshot(t0 + 30 * 60_000)).state
        assertEquals(1, s.counters.companionDays)
    }

    @Test fun batteryNoiseIsNotCountedAsUse() {
        var s = r.onBattery(fresh(), 50_000.0).state
        repeat(100) { s = r.onBattery(s, 49_990.0).state; s = r.onBattery(s, 50_000.0).state }
        assertEquals(10.0, s.counters.energyWh, 0.001)
        // 충전한 뒤에 쓴 것은 다시 센다
        s = r.onBattery(s, 60_000.0).state
        s = r.onBattery(s, 59_000.0).state
        assertEquals(1_010.0, s.counters.energyWh, 0.001)
    }

    @Test fun minutesInDriveWithoutMovingIsNotTheLastDriveWhenTheOdometerHasDecimals() {
        var s = r.armLast(fresh(72_963.4))
        s = r.onGear(s, false, 72_963.4, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_963.4, alone, Snapshot(t0 + 3 * 60_000)).state
        assertEquals(LastDrive.ARMED, s.last)
        assertEquals(0, s.counters.arrivals)
    }

    @Test fun aLateOdometerValueAfterTheLastDriveIsNotMovement() {
        var s = r.armLast(fresh())
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onOdometer(s, 72_989.9, Snapshot(t0 + 1000)).state
        s = r.onGear(s, true, 72_989.9, alone, Snapshot(t0 + 3_600_000)).state
        val done = s
        // 다시 켠 뒤, 주행거리를 읽기 전에 기어가 P를 벗어났다가 늦게 온 값과 함께 P로 돌아온다
        s = r.onGear(s, false, null, alone, Snapshot(t0 + 90_000_000)).state
        s = r.onOdometer(s, 72_990.0, Snapshot(t0 + 90_001_000)).state
        s = r.onGear(s, true, 72_990.0, alone, Snapshot(t0 + 90_005_000)).state
        assertEquals(done, s)
    }

    @Test fun shiftingWithoutMovingIsNotADrive() {
        var s = r.armLast(fresh())
        s = r.onGear(s, false, 72_963.0, Seats(true, true, false, false), Snapshot(t0)).state
        s = r.onGear(s, true, 72_963.0, alone, Snapshot(t0 + 5_000)).state
        assertEquals(LastDrive.ARMED, s.last) // 마지막 주행은 아직 시작하지 않았다
        assertEquals(0, s.counters.companionDays)
        assertEquals(0, r.onTick(s, t0 + 20 * 60_000).state.counters.arrivals)
    }

    @Test fun arrivalThatAlreadyStayedTenMinutesIsCountedWhenLeaving() {
        var s = fresh()
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 60_000)).state
        // 시동이 꺼져 있어 10분을 세지 못했고, 다음 날 켜자마자 출발했다
        s = r.onGear(s, false, 72_970.0, alone, Snapshot(t0 + 24 * 3_600_000L)).state
        assertEquals(1, s.counters.arrivals)
    }

    @Test fun driveInterruptedByShutdownIsDroppedUncounted() {
        var s = r.armLast(fresh())
        s = r.onGear(s, false, 72_963.0, Seats(true, true, false, false), Snapshot(t0)).state
        val restarted = r.dropUnseenDrive(s)
        assertNull(restarted.drive)
        assertEquals(LastDrive.ARMED, restarted.last)
        assertEquals(0, restarted.counters.companionDays)
    }

    @Test fun savedBookNoLongerCoversChangedRecords() {
        val a = fresh()
        assertTrue(r.sameBook(a, a.copy(lastOdoKm = 72_963.4)))
        assertFalse(r.sameBook(a, a.copy(lastOdoKm = 73_000.0))) // 한 권에 적힌 거리가 달라진다
        assertFalse(r.sameBook(a, a.copy(counters = a.counters.copy(arrivals = 1))))
        assertFalse(r.sameBook(a, a.copy(last = LastDrive.DONE, lastDoneAt = t0)))
    }

    @Test fun correctingTheFirstInputRecountsDaysAndDistanceCards() {
        // 설치한 뒤 3년째 되는 날을 맞았다
        var s = r.checkDays(fresh(), at(2026, 8, 5)).state
        assertEquals(listOf("year-3"), s.moments.map { it.id })
        // 받은 날이 사실은 2025-08-01이었다: 3년 카드는 지우고, 설치한 뒤에 맞은 1년(2026-08-01)을 남긴다
        s = r.correctSetup(s, day(2025, 8, 1), 30_000.0, at(2026, 8, 5))
        assertEquals(30_000.0, s.setup!!.receivedOdoKm, 0.0)
        assertEquals(listOf("year-1"), s.moments.map { it.id })
        // 설치한 날의 계기판보다 큰 값은 넣을 수 없다
        assertEquals(72_963.0, r.correctSetup(s, day(2023, 4, 21), 99_999.0, at(2026, 8, 5)).setup!!.receivedOdoKm, 0.0)
    }

    @Test fun anniversariesAfterInstallAreKeptEvenIfTheCarSleptThroughThem() {
        val s = r.checkDays(fresh(), at(2027, 8, 1)).state
        assertEquals(listOf("year-4", "days-1111", "year-3"), s.moments.map { it.id })
    }

    @Test fun hundredAndThousandDaysAreKeptForACarInstalledEarly() {
        val s0 = r.begin(AppState(), day(2023, 7, 8), 38_120.0, 38_150.0, at(2023, 7, 10))
        val s = r.checkDays(s0, at(2026, 11, 11)).state
        assertEquals(listOf("days-1111", "year-3", "days-1000", "year-2", "days-500", "year-1", "days-100"), s.moments.map { it.id })
    }

    @Test fun wipeOnlyAfterPhoneSavedAndLastDriveDone() {
        var s = fresh().copy(last = LastDrive.DONE, lastDoneAt = t0, export = Export("id", "key", uploadedAt = t0))
        assertEquals(s, r.wipe(s, t0))
        s = s.copy(export = s.export!!.copy(savedAt = t0 + 1))
        assertEquals(s.copy(last = LastDrive.NONE), r.wipe(s.copy(last = LastDrive.NONE), t0 + 2))
        val w = r.wipe(s, t0 + 2)
        assertNull(w.setup)
        assertTrue(w.moments.isEmpty())
        assertEquals(1071, w.wiped!!.days)
        assertEquals(s.setup!!.carName, w.wiped!!.carName)
        assertNotNull(w.wiped)
    }

    @Test fun moonDistanceTogether() {
        // 0km에서 받은 차가 함께 384,399km를 달린 뒤
        val s0 = r.begin(AppState(), day(2023, 7, 30), 0.0, 384_399.0, t0)
        val a = r.onOdometer(s0, 384_401.0, Snapshot(t0))
        assertTrue(a.added.any { it.id == "dist-moon" && it.title == "달까지의 거리" })
        assertTrue(r.onOdometer(a.state, 384_403.0, Snapshot(t0)).added.none { it.id == "dist-moon" })
    }

    @Test fun demoSampleIsShiftedToToday() {
        val s0 = r.begin(AppState(), day(2023, 7, 8), 38_120.0, 38_150.0, at(2023, 7, 10))
        val s1 = r.checkDays(s0, at(2026, 11, 11)).state
        val shifted = r.shiftDays(s1, day(2026, 11, 11), day(2026, 12, 1))
        assertEquals(day(2023, 7, 28), shifted.setup!!.receivedDay)
        assertEquals(r.daysTogether(s1.setup!!, at(2026, 11, 11)), r.daysTogether(shifted.setup!!, at(2026, 12, 1)))
        assertEquals(s1.moments.first().at + 20 * 86_400_000L, shifted.moments.first().at)
        // 옮긴 뒤에도 같은 날에는 새 날짜 순간이 생기지 않는다
        assertTrue(r.checkDays(shifted, at(2026, 12, 1)).added.isEmpty())
    }

    @Test fun thousandthArrivalIsAMoment() {
        var s = fresh()
        s = s.copy(counters = s.counters.copy(arrivals = 999))
        s = r.onGear(s, false, 72_963.0, alone, Snapshot(t0)).state
        s = r.onGear(s, true, 72_970.0, alone, Snapshot(t0 + 600_000)).state
        val st = r.onTick(s, t0 + 600_000 + 10 * 60_000)
        assertEquals(1000, st.state.counters.arrivals)
        assertEquals("1,000번째 도착", st.added.single().title)
        assertEquals(1, st.state.moments.count { it.id == "arrival-1000" })
    }

    @Test fun firstLongTripOnlyOnce() {
        var s = fresh(60_000.5)
        s = r.onGear(s, false, 60_000.5, alone, Snapshot(t0)).state
        val a = r.onGear(s, true, 60_320.5, alone, Snapshot(t0 + 1000))
        assertEquals(listOf("long-first"), a.added.map { it.id })
        var b = r.onGear(a.state, false, 60_320.5, alone, Snapshot(t0 + 2000)).state
        assertTrue(r.onGear(b, true, 60_700.5, alone, Snapshot(t0 + 3000)).added.none { it.id == "long-first" })
    }

    @Test fun seasonMomentsOncePerSeason() {
        // 2026-07-05 설치. 여름 폭염 한 번, 겨울 첫 영하 한 번(12월과 다음 해 1월은 같은 겨울), 2027년 첫 주행.
        var s = fresh()
        fun drive(st: AppState, t: Long, temp: Float?): Step {
            val g = r.onGear(st, false, st.lastOdoKm, alone, Snapshot(t)).state
            return r.onGear(g, true, st.lastOdoKm, alone, Snapshot(t + 5 * 60_000, outsideTempC = temp))
        }
        var x = drive(s, at(2026, 7, 20), 34f); assertEquals(listOf("heat-2026"), x.added.map { it.id }); s = x.state
        x = drive(s, at(2026, 8, 2), 35f); assertTrue(x.added.isEmpty()); s = x.state
        x = drive(s, at(2026, 12, 3), -2f); assertEquals(listOf("cold-2026"), x.added.map { it.id }); s = x.state
        x = drive(s, at(2027, 1, 2), -5f); assertEquals(listOf("newyear-2027"), x.added.map { it.id }); s = x.state
        x = drive(s, at(2027, 12, 1), -1f); assertEquals(listOf("cold-2027"), x.added.map { it.id })
    }

    @Test fun formats() {
        assertEquals("34,843", Fmt.km(34_843.99))
        assertEquals("2.8", Fmt.km1(2.8))
        assertEquals("811", Fmt.kwh(811_400.0))
        assertEquals("2026. 11. 14 토", Fmt.dateDow(LocalDate.of(2026, 11, 14)))
    }

    @Test
    fun carNameTakesTheRightParticle() {
        assertEquals("이 차가", Fmt.subject(null))
        assertEquals("이 차가", Fmt.subject("  "))
        assertEquals("콩이가", Fmt.subject("콩이"))
        assertEquals("은별이", Fmt.subject("은별"))
        assertEquals("아이오닉5가", Fmt.subject("아이오닉5"))
        assertEquals("R8이", Fmt.subject("R8"))
    }

    @Test
    fun renamingTheCarKeepsMomentsAndRewritesTheirSentences() {
        var s = r.begin(AppState(), day(2023, 7, 8), 38_120.0, 38_150.0, at(2023, 7, 10), "콩이")
        assertEquals("콩이", s.setup!!.carName)
        s = s.copy(moments = listOf(Moment(id = "full-first", kind = MomentKind.PEOPLE, title = "네 자리가 다 찬 날",
            line = "콩이가 지켜본 뒤로 네 자리가 처음 다 찬 날", why = "콩이가 지켜본 뒤로 네 자리가 모두 찬 첫 주행입니다.", at = at(2023, 8, 1))))
        val renamed = r.correctSetup(s, day(2023, 7, 8), 38_120.0, at(2023, 8, 2), "은별")
        assertEquals("은별", renamed.setup!!.carName)
        assertEquals(1, renamed.moments.count { it.id == "full-first" })
        assertEquals("은별이 지켜본 뒤로 네 자리가 처음 다 찬 날", renamed.moments.first { it.id == "full-first" }.line)
        val cleared = r.correctSetup(renamed, day(2023, 7, 8), 38_120.0, at(2023, 8, 2), "")
        assertEquals(null, cleared.setup!!.carName)
        assertEquals("이 차가 지켜본 뒤로 네 자리가 처음 다 찬 날", cleared.moments.first { it.id == "full-first" }.line)
    }

    @Test
    fun assistantAnswerCarriesDaysDistanceAndName() {
        val none = com.lrlr.carmigo.GleoAnswer.daysTogether(AppState(), r, t0)
        assertEquals("fail", none.status)
        val s = r.begin(AppState(), day(2023, 7, 8), 38_120.0, 38_150.0, at(2023, 7, 10), "콩이")
        val a = com.lrlr.carmigo.GleoAnswer.daysTogether(s, r, at(2023, 7, 10))
        assertEquals("success", a.status)
        assertTrue(a.message.contains("named 콩이") && a.message.contains("30 kilometers"))
        assertTrue(a.data!!.contains("\"daysTogether\":${r.daysTogether(s.setup!!, at(2023, 7, 10))}"))
    }

    @Test
    fun weatherIsAskedOnlyAfterAStationIsChosenAndOnlyForPastDays() {
        var s = r.begin(AppState(), day(2023, 7, 8), 38_120.0, 38_150.0, at(2023, 7, 10))
        s = s.copy(moments = listOf(
            Moment(id = "a", kind = MomentKind.ODOMETER, title = "44,444 km", line = "l", why = "w", at = at(2024, 1, 14)),
            Moment(id = "today", kind = MomentKind.ODOMETER, title = "t", line = "l", why = "w", at = at(2024, 3, 1)),
        ))
        val now = at(2024, 3, 1)
        assertTrue(r.weatherWanted(s, now).isEmpty())
        s = r.setStation(s, 108, "서울")
        val q = r.weatherWanted(s, now)
        assertEquals(listOf(null, "a"), q.map { it.momentId })
        assertEquals("2023-07-08", q[0].date); assertEquals(null, q[0].hour)
        assertEquals("2024-01-14", q[1].date); assertEquals(108, q[1].stationId)
        s = r.applyWeather(s, q[0], Weather("맑음", "서울", 29))
        s = r.applyWeather(s, q[1], null)
        assertEquals("맑음, 최고 29° · 서울 관측소", s.setup!!.receivedWeather!!.text)
        assertTrue(r.weatherWanted(s, now).isEmpty())
        // 관측소를 바꾸면 받은 날만 다시 묻는다
        s = r.setStation(s, 159, "부산")
        assertEquals(listOf<String?>(null), r.weatherWanted(s, now).map { it.momentId })
    }

    @Test
    fun nearestStationIsChosenInTheCar() {
        val all = listOf(
            com.lrlr.carmigo.data.Station(108, "서울", 37.5714, 126.9658),
            com.lrlr.carmigo.data.Station(159, "부산", 35.1047, 129.032),
            com.lrlr.carmigo.data.Station(184, "제주", 33.5141, 126.5297),
        )
        assertEquals("서울", com.lrlr.carmigo.data.Stations.nearest(all, 37.49, 127.12)!!.name)
        assertEquals("부산", com.lrlr.carmigo.data.Stations.nearest(all, 35.2, 128.6)!!.name)
        assertEquals(com.lrlr.carmigo.data.WeatherAnswer.None, com.lrlr.carmigo.data.WeatherClient.parse(404, "{\"error\":\"no_data\"}"))
        assertEquals(com.lrlr.carmigo.data.WeatherAnswer.Later, com.lrlr.carmigo.data.WeatherClient.parse(404, "{\"error\":\"not_yet\"}"))
        assertEquals(com.lrlr.carmigo.data.WeatherAnswer.Found("비", null, true), com.lrlr.carmigo.data.WeatherClient.parse(200, "{\"sky\":\"비\",\"sample\":true}"))
    }
}

