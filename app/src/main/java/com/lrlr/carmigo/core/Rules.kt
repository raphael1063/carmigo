package com.lrlr.carmigo.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.max

/** 규칙을 한 번 적용한 결과. speak는 한 번 읽을 문장, added는 방금 남은 순간. */
data class Step(
    val state: AppState,
    val speak: String? = null,
    val added: List<Moment> = emptyList(),
)

/**
 * 기록 규칙. 차량 SDK와 분리한 순수 계산이라 단위 테스트로 검증한다.
 * 원칙: 앱이 실제로 지켜본 것만 세고, 어림하지 않는다. 문장은 사실만 적는다.
 */
class Rules(
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** P에 놓고 이만큼 머물면 도착 한 번으로 센다(신호 대기나 잠깐 세운 것을 빼기 위해). */
    val arrivalMs: Long = 10 * 60_000L,
) {
    fun day(at: Long): Long = Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay()
    fun date(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)
    fun minuteOfDay(at: Long): Int = Instant.ofEpochMilli(at).atZone(zone).let { it.hour * 60 + it.minute }

    fun daysTogether(setup: Setup, now: Long): Long = max(0, day(now) - setup.receivedDay)
    fun kmTogether(setup: Setup, odoKm: Double?): Double? = odoKm?.let { max(0.0, it - setup.receivedOdoKm) }
    /** 앱이 지켜본 날수. 설치한 날이 1일. */
    fun watchedDays(setup: Setup, now: Long): Long = max(1, day(now) - setup.installedDay + 1)
    fun watchedKm(setup: Setup, odoKm: Double?): Double? =
        if (odoKm == null || setup.installedOdoKm == null) null else max(0.0, odoKm - setup.installedOdoKm)

    // ---------- 처음 한 번의 입력 ----------

    fun begin(state: AppState, receivedDay: Long, receivedOdoKm: Double, odoKm: Double?, now: Long, carName: String? = null): AppState {
        @Suppress("NAME_SHADOWING") val odoKm = odoKm ?: state.lastOdoKm
        val today = day(now)
        val setup = Setup(
            receivedDay = receivedDay.coerceAtMost(today),
            receivedOdoKm = receivedOdoKm.coerceAtLeast(0.0).let { if (odoKm != null) it.coerceAtMost(odoKm) else it },
            installedDay = today,
            installedOdoKm = odoKm,
            carName = Fmt.cleanName(carName),
        )
        return AppState(setup = setup, lastOdoKm = odoKm, counters = Counters(lastLevelWh = state.counters.lastLevelWh))
    }

    // ---------- 계기판 숫자 ----------

    /**
     * km보다 큰 가장 가까운 계기판 숫자. 드문 것만 고른다:
     * 같은 숫자가 나란히 서는 것(44,444, 77,777, 111,111 …), 숫자가 차례로 오르는 것(12,345, 56,789, 123,456 …),
     * 10만 km 단위(100,000, 200,000 …).
     */
    fun nextNumber(km: Double): Long = NUMBERS.first { it > km }

    private fun isAscending(n: Long): Boolean {
        val s = n.toString()
        return s.length >= 5 && s.zipWithNext().all { (a, b) -> b - a == 1 }
    }

    private fun isRound(n: Long): Boolean = n % 100_000L == 0L

    fun numberLine(n: Long): String {
        val s = n.toString()
        return when {
            s.toSet().size == 1 -> "같은 숫자 ${digitsWord(s.length)} 개가 나란히 선 순간"
            isAscending(n) -> "계기판 숫자가 ${s.first()}부터 ${s.last()}까지 차례로 선 순간"
            isRound(n) -> "계기판이 ${n / 10_000}만 km가 된 순간"
            else -> "계기판이 ${Fmt.n(n)}km가 된 순간"
        }
    }

    /** 다가오는 숫자 옆에 적는 짧은 설명 */
    fun numberLabel(n: Long): String {
        val s = n.toString()
        return when {
            s.toSet().size == 1 -> "같은 숫자 ${digitsWord(s.length)} 개"
            isAscending(n) -> "차례로 선 숫자"
            isRound(n) -> "${n / 10_000}만 km"
            else -> "계기판 숫자"
        }
    }

    /** 카드의 한 줄: 그 순간이 함께한 지 며칠째였는지 */
    private fun dayLine(setup: Setup, at: Long) = "함께한 지 ${Fmt.n(daysTogether(setup, at))}일째"

    /** 카드의 한 줄: 그 순간이 함께한 지 며칠째였는지(이름 없이 state만 있을 때) */
    private fun watchLine(state: AppState, at: Long): String {
        val setup = state.setup ?: return ""
        return dayLine(setup, at)
    }

    private fun tempLine(t: Float?) = t?.let { "바깥 기온 ${Math.round(it)}°" }

    /** 바퀴 수를 세는 말: 한, 두, 세 … 열, 그 뒤는 숫자 */
    fun lapWord(n: Long): String = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열").getOrNull((n - 1).toInt()) ?: Fmt.n(n)

    /** 함께 달린 거리의 다음 이정표(km): 10,000km마다, 지구 한 바퀴마다, 달까지의 거리 가운데 가장 가까운 것. */
    fun nextTogether(after: Double): Long {
        var m = minOf((floor(after / DISTANCE_STEP_KM) + 1) * DISTANCE_STEP_KM, (floor(after / EARTH_KM) + 1) * EARTH_KM)
        if (after < MOON_KM) m = minOf(m, MOON_KM)
        return Math.round(m)
    }

    private fun isEarth(m: Long) = m >= EARTH_KM.toLong() && m % EARTH_KM.toLong() == 0L
    private fun earthWord(m: Long) = "지구 ${lapWord(m / EARTH_KM.toLong())} 바퀴"

    /** 주행 중 한 줄: "함께 40,000 km까지", "지구 한 바퀴까지", "달까지의 거리를 채우기까지" */
    fun nearTogetherLabel(m: Long): String = when {
        m == MOON_KM.toLong() -> "달까지의 거리를 채우기"
        isEarth(m) -> earthWord(m)
        else -> "${Fmt.n(m)} km"
    }

    /** 숫자 탭과 카드에 쓰는 이정표 이름: "40,000 km", "지구 한 바퀴", "달까지의 거리" */
    fun togetherTitle(m: Long): String = when {
        m == MOON_KM.toLong() -> "달까지의 거리"
        isEarth(m) -> earthWord(m)
        else -> "${Fmt.n(m)} km"
    }

    fun passedTogetherLine(m: Long): String = when {
        m == MOON_KM.toLong() -> "달까지의 거리만큼 함께 달렸습니다"
        isEarth(m) -> "${earthWord(m)}만큼 함께 달렸습니다"
        else -> "${Fmt.n(m)} km를 넘었습니다"
    }

    private fun togetherSpeech(m: Long, left: Double, name: String?): String {
        val what = when {
            m == MOON_KM.toLong() -> "달까지의 거리만큼"
            isEarth(m) -> "${earthWord(m)}만큼"
            else -> "${Fmt.n(m)}킬로미터가"
        }
        val whole = Math.round(left)
        val head = if (whole >= 1) "약 ${whole}킬로미터 뒤에" else "곧"
        return if (m == MOON_KM.toLong() || isEarth(m)) "$head ${Fmt.with(name)} $what 함께 달리게 됩니다"
        else "$head ${Fmt.with(name)} 함께 달린 거리가 $what 됩니다"
    }

    private fun digitsWord(n: Int) = when (n) { 5 -> "다섯"; 6 -> "여섯"; 7 -> "일곱"; else -> n.toString() }

    /** 주행 중 화면의 한 줄. 숫자가 가까우면 남은 거리, 방금 지났으면 지났다는 사실, 아니면 함께한 날수. */
    fun drivingLine(state: AppState, now: Long): String {
        val setup = state.setup ?: return ""
        val days = Fmt.n(daysTogether(setup, now))
        if (state.last == LastDrive.DRIVING) return "마지막 주행 · 함께한 지 ${days}일째"
        state.drive?.passedTogether?.let { return passedTogetherLine(it) }
        state.drive?.passedNumber?.let { return "계기판이 ${Fmt.n(it)} km를 지났습니다" }
        val odo = state.lastOdoKm
        if (odo != null) {
            val after = odo - setup.receivedOdoKm
            val t = nextTogether(after)
            if (t - after <= NEAR_KM) return "${nearTogetherLabel(t)}까지 ${Fmt.km1(t - after)} km"
            val next = nextNumber(odo)
            val left = next - odo
            if (left <= NEAR_KM) return "계기판 ${Fmt.n(next)} km까지 ${Fmt.km1(left)} km"
        }
        return "함께한 지 ${days}일"
    }

    // ---------- 신호 ----------

    /** 마친 마지막 주행을 다시 달리는 중으로 되돌린다. 10분을 채우지 못한 도착이었다면 그 도착도 되돌린다. */
    private fun resumeLast(state: AppState): AppState {
        val d = state.drive ?: return state
        var c = state.counters
        if (d.undoArrival && c.arrivals > 0) c = c.copy(arrivals = c.arrivals - 1)
        return state.copy(counters = c, last = LastDrive.DRIVING, lastDoneAt = null, drive = d.copy(undoArrival = false))
    }

    fun onOdometer(state0: AppState, km: Double, snap: Snapshot): Step {
        var state = state0
        // 마지막 주행을 마친 뒤에는 기록을 그대로 둔다(다시 달리면 startDrive가 마지막 주행을 이어 간다)
        if (state.last == LastDrive.DONE) {
            val d = state.drive
            val from = d?.startOdoKm
            // 마지막 주행을 마친 뒤에 실제로 다시 움직였을 때에만 마지막 주행을 이어 간다
            if (d == null) return Step(state)
            // 출발할 때 주행거리를 몰랐다면 지금 값을 출발점으로 삼는다(이것만으로는 움직였다고 보지 않는다)
            if (from == null) return Step(state.copy(drive = d.copy(startOdoKm = km)))
            if (km <= from) return Step(state)
            state = resumeLast(state)
        }
        val prev = state.lastOdoKm
        var s = state.copy(lastOdoKm = km)
        val setup = s.setup ?: return Step(s)
        // 이어서 지켜본 변화만 순간으로 남긴다. 앱이 꺼진 사이의 큰 차이는 "그 순간"을 보지 못한 것이다.
        val live = prev != null && km >= prev && km - prev <= LIVE_JUMP_KM
        if (!live) return Step(s)
        prev!!
        val added = mutableListOf<Moment>()

        val target = nextNumber(prev)
        if (km >= target) {
            added += Moment(
                id = "odo-$target", kind = MomentKind.ODOMETER,
                title = "${Fmt.n(target)} km", line = numberLine(target),
                why = "다음으로 남을 계기판 숫자는 ${Fmt.n(nextNumber(target.toDouble()))}km입니다.",
                at = snap.now, odoKm = target.toDouble(), outsideTempC = snap.outsideTempC, seats = snap.seats,
            )
            s = s.copy(drive = s.drive?.copy(passedNumber = target))
        }

        val before = prev - setup.receivedOdoKm
        val after = km - setup.receivedOdoKm
        val step = floor(after / DISTANCE_STEP_KM).toLong() * DISTANCE_STEP_KM.toLong()
        if (step >= DISTANCE_STEP_KM && before < step) {
            added += Moment(
                id = "dist-$step", kind = MomentKind.DISTANCE,
                title = "${Fmt.n(step)} km", line = dayLine(setup, snap.now),
                why = "계기판 숫자에서 받은 날의 주행거리 ${Fmt.km(setup.receivedOdoKm)}km를 뺀 거리입니다.",
                at = snap.now, odoKm = km, outsideTempC = snap.outsideTempC, seats = snap.seats,
            )
        }
        // 지구 둘레만큼 달릴 때마다: 한 바퀴, 두 바퀴, 세 바퀴 …
        val laps = floor(after / EARTH_KM).toLong()
        if (laps >= 1 && before < laps * EARTH_KM) {
            val lapKm = (laps * EARTH_KM).toLong()
            added += Moment(
                id = if (laps == 1L) "dist-earth" else "dist-earth-$laps", kind = MomentKind.DISTANCE,
                title = "지구 ${lapWord(laps)} 바퀴", line = "${Fmt.n(lapKm)} km · ${dayLine(setup, snap.now)}",
                why = "지구 둘레는 약 ${Fmt.n(EARTH_KM.toLong())}km입니다.",
                at = snap.now, odoKm = km, outsideTempC = snap.outsideTempC, seats = snap.seats,
            )
        }
        if (before < MOON_KM && after >= MOON_KM) {
            added += Moment(
                id = "dist-moon", kind = MomentKind.DISTANCE,
                title = "달까지의 거리", line = "${Fmt.n(MOON_KM.toLong())} km · ${dayLine(setup, snap.now)}",
                why = "지구에서 달까지의 평균 거리입니다.",
                at = snap.now, odoKm = km, outsideTempC = snap.outsideTempC, seats = snap.seats,
            )
        }
        val passedT = nextTogether(before)
        if (after >= passedT) s = s.copy(drive = s.drive?.copy(passedTogether = passedT))
        s = add(s, added)

        // 다가오는 이정표는 주행 중에 한 번만 말로 알린다. 함께 달린 거리가 먼저, 계기판 숫자가 그다음.
        var speak: String? = null
        val tNext = nextTogether(after)
        val tLeft = tNext - after
        val next = nextNumber(km)
        val left = next - km
        if (s.drive != null && tLeft <= ANNOUNCE_KM && tNext !in s.announcedTogether) {
            speak = togetherSpeech(tNext, tLeft, setup.carName)
            s = s.copy(announcedTogether = (s.announcedTogether + tNext).takeLast(20))
        } else if (s.drive != null && left <= ANNOUNCE_KM && next !in s.announced) {
            val whole = Math.round(left)
            speak = if (whole >= 1) "약 ${whole}킬로미터 뒤에 계기판이 ${Fmt.n(next)}킬로미터가 됩니다"
            else "곧 계기판이 ${Fmt.n(next)}킬로미터가 됩니다"
            s = s.copy(announced = (s.announced + next).takeLast(20))
        }
        return Step(s, speak, added.filter { m -> state.moments.none { it.id == m.id } })
    }

    /** 기어가 P인지 아닌지가 바뀔 때. */
    fun onGear(state: AppState, parked: Boolean, odoKm: Double?, seats: Seats, snap: Snapshot): Step {
        if (state.setup == null) return Step(state)
        return if (parked) endDrive(state, odoKm, snap) else startDrive(state, odoKm, seats, snap)
    }

    private fun startDrive(state: AppState, odoKm: Double?, seats: Seats, snap: Snapshot): Step {
        if (state.drive != null) return Step(state)
        var c = state.counters
        val since = state.parkedSince
        val stayed = since != null && snap.now - since >= arrivalMs
        // 10분을 채운 도착을 아직 세지 못했다면(시동이 꺼져 있었던 경우 등) 출발하기 전에 센다
        if (state.arrivalPending && stayed) c = c.copy(arrivals = c.arrivals + 1)
        // 마지막 주행을 마쳤다고 했는데 다시 달리면 그 주행이 이어지는 것으로 본다.
        // 다만 실제로 움직이기 전에는 아무것도 바꾸지 않는다(기어만 옮겼다 다시 P에 놓는 일이 있다).
        val resumed = state.last == LastDrive.DONE
        val isLast = state.last == LastDrive.ARMED || resumed
        var s = state.copy(
            counters = c,
            drive = Drive(
                startAt = snap.now,
                // 마친 마지막 주행 뒤에는 예전에 적어 둔 값으로 어림하지 않는다(늦게 온 값과의 차이를 움직인 것으로 보게 된다)
                startOdoKm = if (resumed) odoKm else odoKm ?: state.lastOdoKm, isLast = isLast,
                undoArrival = resumed && !stayed,
                // 움직이지 않고 다시 P에 놓으면 세우고 있던 시간을 그대로 잇는다
                prevParkedSince = state.parkedSince, prevArrivalPending = state.arrivalPending && !stayed,
            ),
            last = if (state.last == LastDrive.ARMED) LastDrive.DRIVING else state.last,
            // 10분이 되기 전에 다시 출발하면 도착으로 세지 않는다
            parkedSince = if (resumed) state.parkedSince else null,
            arrivalPending = false,
        )
        s = onSeats(s, seats).state
        return Step(s)
    }

    private fun endDrive(state: AppState, odoKm: Double?, snap: Snapshot): Step {
        val drive = state.drive ?: return Step(state)
        val setup = state.setup!!
        val today = day(snap.now)
        var c = state.counters
        val added = mutableListOf<Moment>()
        val seatCount = if (drive.maxSeats > 0) drive.maxSeats else snap.seats
        val end = odoKm ?: state.lastOdoKm
        val begin = drive.startOdoKm
        if (state.last == LastDrive.DONE) {
            // 마친 마지막 주행 뒤에 기어만 옮겼다 놓은 것. 작별 화면과 저장 확인을 그대로 둔다.
            if (begin == null || odoKm == null || odoKm <= begin) return Step(state.copy(drive = null))
            // 주행거리 신호보다 P가 먼저 온 경우: 여기서 이어 간 것으로 바꾸고 아래에서 끝맺는다
            return endDrive(resumeLast(state).copy(lastOdoKm = odoKm), odoKm, snap)
        }
        // 움직이지 않고 곧바로 다시 P에 놓은 것은 주행으로 세지 않는다(주행거리를 읽지 못했으면 판단하지 않는다).
        // 주행거리가 1km 단위로만 오는 차의 짧은 주행을 버리지 않도록, 잠깐일 때만 그렇게 본다.
        val decimals = begin != null && odoKm != null && (begin % 1.0 != 0.0 || odoKm % 1.0 != 0.0)
        if (begin != null && odoKm != null && odoKm <= begin && (decimals || snap.now - drive.startAt < NO_MOVE_MS)) {
            return Step(
                state.copy(
                    drive = null,
                    last = if (state.last == LastDrive.DRIVING) LastDrive.ARMED else state.last,
                    parkedSince = drive.prevParkedSince ?: snap.now, arrivalPending = drive.prevArrivalPending,
                ),
            )
        }

        if (drive.companion && c.lastCompanionDay != today) {
            c = c.copy(companionDays = c.companionDays + 1, lastCompanionDay = today)
            if (c.companionDays % COMPANION_STEP == 0) {
                added += Moment(
                    id = "companion-${c.companionDays}", kind = MomentKind.PEOPLE,
                    title = "옆자리에 누군가 탄 ${c.companionDays}번째 날",
                    line = watchLine(state, snap.now),
                    why = "누가 탔는지 차는 모릅니다. 옆자리가 찼던 날을 세었을 뿐입니다.",
                    at = snap.now, odoKm = end, outsideTempC = snap.outsideTempC, seats = seatCount,
                )
            }
        }
        if (drive.full && c.lastFullDay != today) {
            val first = c.firstFullDay == null
            c = c.copy(fullDays = c.fullDays + 1, lastFullDay = today, firstFullDay = c.firstFullDay ?: today)
            if (first) {
                added += Moment(
                    id = "full-first", kind = MomentKind.PEOPLE,
                    title = "네 자리가 다 찬 날", line = dayLine(setup, snap.now),
                    why = "기록을 시작한 뒤로 네 자리가 모두 찬 첫 주행입니다. 누가 탔는지 차는 모릅니다.",
                    at = snap.now, odoKm = end, outsideTempC = snap.outsideTempC, seats = 4,
                )
            }
        }
        // 기록된 거리가 100.0km인 주행. 주행거리가 소수 자리까지 올 때만 만든다.
        val start = drive.startOdoKm
        if (start != null && end != null && (start % 1.0 != 0.0 || end % 1.0 != 0.0)) {
            // 화면에 적는 방식(소수 첫째 자리 아래 버림)으로 100.0일 때만
            val tenths = floor((end - start) * 10 + 1e-6).toLong()
            if (tenths == 1000L) {
                added += Moment(
                    id = "c100-${drive.startAt}", kind = MomentKind.COINCIDENCE,
                    title = "100.0 km 주행", line = "계기판 ${Fmt.km1(start)} → ${Fmt.km1(end)} km",
                    why = "출발할 때와 도착했을 때의 계기판 차이가 소수점까지 딱 100.0km였습니다.",
                    at = snap.now, odoKm = end, outsideTempC = snap.outsideTempC, seats = seatCount,
                )
            }
        }

        // 한 번에 300km를 넘게 달린 첫 주행
        if (start != null && end != null && end - start >= LONG_TRIP_KM && state.moments.none { it.id == "long-first" }) {
            added += Moment(
                id = "long-first", kind = MomentKind.DISTANCE,
                title = "한 번에 ${Fmt.km(end - start)} km", line = "처음으로 한 번에 ${Fmt.n(LONG_TRIP_KM.toLong())}km를 넘긴 주행",
                why = MomentText.LONG_WHY,
                at = snap.now, odoKm = end, outsideTempC = snap.outsideTempC, seats = seatCount,
            )
        }
        added += seasonMoments(state, setup, snap, end, seatCount)

        var s = state.copy(counters = c, drive = null, lastOdoKm = if (state.last == LastDrive.DONE) state.lastOdoKm else end ?: state.lastOdoKm)
        s = if (drive.isLast && state.last == LastDrive.DRIVING) {
            // 마지막 주행은 P에 놓는 즉시 도착으로 센다
            s.copy(
                counters = c.copy(arrivals = c.arrivals + 1),
                last = LastDrive.DONE, lastDoneAt = snap.now, parkedSince = snap.now, arrivalPending = false,
            )
        } else {
            s.copy(parkedSince = snap.now, arrivalPending = true)
        }
        s = add(s, added)
        return Step(checkDays(s, snap.now).state, added = added.filter { m -> state.moments.none { it.id == m.id } })
    }

    /** 해마다 한 번씩: 새해 첫 주행, 그해 겨울 첫 영하, 그해 여름 첫 폭염. 설치한 뒤에 맞은 것만. */
    private fun seasonMoments(state: AppState, setup: Setup, snap: Snapshot, odoKm: Double?, seats: Int?): List<Moment> {
        val today = date(day(snap.now))
        val out = mutableListOf<Moment>()
        fun has(id: String) = state.moments.any { it.id == id }
        val y = today.year
        // 새해 첫 주행: 그해 1월 1일 전에 이미 지켜보고 있었을 때만(설치한 해의 첫 주행은 새해 첫 주행이 아니다)
        if (LocalDate.of(y, 1, 1).toEpochDay() > setup.installedDay && !has("newyear-$y")) {
            out += Moment(
                id = "newyear-$y", kind = MomentKind.SEASON,
                title = "${y}년 첫 주행", line = tempLine(snap.outsideTempC) ?: "새해 들어 처음 마친 주행",
                why = "기록을 시작한 뒤, ${y}년에 처음 마친 주행입니다.",
                at = snap.now, odoKm = odoKm, outsideTempC = snap.outsideTempC, seats = seats,
            )
        }
        val t = snap.outsideTempC
        if (t != null && t < 0f) {
            val winter = if (today.monthValue <= 6) y - 1 else y
            if (!has("cold-$winter")) out += Moment(
                id = "cold-$winter", kind = MomentKind.SEASON,
                title = MomentText.COLD_TITLE, line = "${winter}년 겨울 · ${tempLine(t)}",
                why = MomentText.COLD_WHY,
                at = snap.now, odoKm = odoKm, outsideTempC = t, seats = seats,
            )
        }
        if (t != null && t >= HEAT_C) {
            if (!has("heat-$y")) out += Moment(
                id = "heat-$y", kind = MomentKind.SEASON,
                title = MomentText.HEAT_TITLE, line = "${y}년 여름 · ${tempLine(t)}",
                why = MomentText.HEAT_WHY,
                at = snap.now, odoKm = odoKm, outsideTempC = t, seats = seats,
            )
        }
        return out
    }

    fun onSeats(state: AppState, seats: Seats): Step {
        val d = state.drive ?: return Step(state)
        val count = seats.count ?: 0
        return Step(
            state.copy(
                drive = d.copy(
                    companion = d.companion || seats.hasCompanion,
                    full = d.full || seats.isFull,
                    maxSeats = max(d.maxSeats, count),
                ),
            ),
        )
    }

    /**
     * 남은 에너지(Wh)가 줄어든 만큼만 더한다. 늘어난 것(충전)은 세지 않는다.
     * 값이 조금 올랐다 내리는 흔들림을 쓴 것으로 세지 않도록, 기준값은 내려갈 때와 뚜렷이 올랐을 때만 옮긴다.
     */
    fun onBattery(state: AppState, levelWh: Double): Step {
        if (levelWh.isNaN() || levelWh < 0) return Step(state)
        val c = state.counters
        val ref = c.lastLevelWh ?: return Step(state.copy(counters = c.copy(lastLevelWh = levelWh)))
        // 첫 입력 전과 마지막 주행을 마친 뒤에는 마지막 값만 기억한다
        val counting = state.setup != null && state.last != LastDrive.DONE
        return when {
            levelWh < ref -> Step(state.copy(counters = c.copy(energyWh = c.energyWh + if (counting) ref - levelWh else 0.0, lastLevelWh = levelWh)))
            levelWh - ref >= BATTERY_RISE_WH -> Step(state.copy(counters = c.copy(lastLevelWh = levelWh)))
            else -> Step(state)
        }
    }

    /** 주기적으로 부른다: 10분 머문 도착 세기, 날짜 순간. */
    fun onTick(state: AppState, now: Long): Step {
        var s = state
        if (s.setup == null) return Step(s)
        val since = s.parkedSince
        var added: List<Moment> = emptyList()
        if (s.arrivalPending && since != null && now - since >= arrivalMs) {
            val n = s.counters.arrivals + 1
            s = s.copy(counters = s.counters.copy(arrivals = n), arrivalPending = false)
            if (n % ARRIVAL_STEP == 0) {
                added = listOf(
                    Moment(
                        id = "arrival-$n", kind = MomentKind.ARRIVAL,
                        title = "${Fmt.n(n)}번째 도착", line = watchLine(s, since),
                        why = "P에 놓고 10분 넘게 머문 것을 도착 한 번으로 셉니다.",
                        at = since, odoKm = s.lastOdoKm,
                    ),
                )
                s = add(s, added)
            }
        }
        val d = checkDays(s, now)
        return Step(d.state, added = added + d.added)
    }

    /** 함께한 지 100일 단위. 설치한 뒤에 맞은 날만 남긴다. */
    fun checkDays(state: AppState, now: Long): Step {
        val setup = state.setup ?: return Step(state)
        if (state.last == LastDrive.DONE) return Step(state)
        val days = daysTogether(setup, now)
        // 드물게만 남긴다: 100일, 1,000일, 그리고 해마다 돌아오는 받은 날. 설치한 뒤에 맞은 날만.
        // (한동안 차를 쓰지 않아 지나친 날도 날짜는 사실이다)
        val received = date(setup.receivedDay)
        val marks = buildList {
            add(Triple("days-100", 100L, "100일" to null))
            add(Triple("days-500", 500L, "500일" to null))
            add(Triple("days-1000", 1000L, "1,000일" to null))
            for (k in 1..9) add(Triple("days-${1111 * k}", 1111L * k, "${Fmt.n(1111 * k)}일" to "같은 숫자가 나란히 선 날수"))
            add(Triple("days-2000", 2000L, "2,000일" to null))
            var y = 1
            while (true) {
                val offset = received.plusYears(y.toLong()).toEpochDay() - setup.receivedDay
                if (offset > days) break
                add(Triple("year-$y", offset, "${y}년" to null))
                y++
            }
        }
        val fresh = marks
            .filter { (id, offset, _) -> offset <= days && setup.receivedDay + offset >= setup.installedDay && state.moments.none { it.id == id } }
            .map { (id, offset, text) ->
                // 그날 알아챈 것이면 그날까지 함께 달린 거리를 적는다. 늦게 알아챈 날은 거리를 모른다.
                val onTheDay = setup.receivedDay + offset == day(now)
                val km = if (onTheDay) kmTogether(setup, state.lastOdoKm) else null
                Moment(
                    id = id, kind = MomentKind.DAYS,
                    title = text.first, line = text.second ?: km?.let { "함께 달린 거리 ${Fmt.km(it)} km" } ?: "받은 날 ${Fmt.date(received)}부터 센 날",
                    why = "받은 날 ${Fmt.date(received)}부터 센 날입니다.",
                    at = date(setup.receivedDay + offset).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                    hasTime = false, odoKm = if (onTheDay) state.lastOdoKm else null,
                )
            }
        if (fresh.isEmpty()) return Step(state)
        return Step(add(state, fresh), added = fresh)
    }

    private fun add(state: AppState, moments: List<Moment>): AppState {
        val fresh = moments.filter { m -> state.moments.none { it.id == m.id } }
        if (fresh.isEmpty()) return state
        return state.copy(moments = (fresh + state.moments).sortedByDescending { it.at })
    }

    // ---------- 마지막 주행 ----------

    /** P에서만 누를 수 있다. 한 번 더 누르면 풀린다. */
    fun armLast(state: AppState): AppState =
        if (state.setup != null && state.drive == null && state.last == LastDrive.NONE) state.copy(last = LastDrive.ARMED) else state

    fun disarmLast(state: AppState): AppState =
        if (state.last == LastDrive.ARMED) state.copy(last = LastDrive.NONE) else state

    /**
     * 앱이 꺼진 사이 끝났을 주행은 끝을 보지 못했으므로 세지 않고 버린다.
     * (앱을 켤 때 저장돼 있던 진행 중 주행에 쓴다.)
     */
    fun dropUnseenDrive(state: AppState): AppState {
        val d = state.drive ?: return state
        if (state.last == LastDrive.DONE) return state.copy(drive = null)
        return state.copy(
            drive = null,
            last = if (state.last == LastDrive.DRIVING) LastDrive.ARMED else state.last,
            // 세워 두고 있던 시간은 그대로 잇는다
            parkedSince = d.prevParkedSince, arrivalPending = d.prevArrivalPending,
        )
    }

    /** 받아 간 한 권에 들어가는 내용이 같은지. 달라졌으면 예전 저장 확인으로는 지울 수 없다. */
    fun sameBook(a: AppState, b: AppState): Boolean =
        a.setup == b.setup && a.last == b.last && a.lastDoneAt == b.lastDoneAt &&
            a.moments.map { it.id } == b.moments.map { it.id } &&
            a.counters.arrivals == b.counters.arrivals && a.counters.companionDays == b.counters.companionDays &&
            a.counters.fullDays == b.counters.fullDays &&
            a.lastOdoKm?.let { floor(it) } == b.lastOdoKm?.let { floor(it) } &&
            floor(a.counters.energyWh / 1000) == floor(b.counters.energyWh / 1000)

    /** "마지막이 아니었어요": 기록은 그대로 두고 마지막 표시만 되돌린다. */
    fun undoLast(state: AppState, now: Long): AppState {
        if (state.last != LastDrive.DONE || state.drive != null) return state
        // 마지막 주행은 P에 놓자마자 도착으로 세었다. 10분이 되기 전에 되돌리면 다른 도착처럼 10분을 기다린다.
        val since = state.parkedSince
        val early = since != null && now - since < arrivalMs && state.counters.arrivals > 0
        return state.copy(
            last = LastDrive.NONE, lastDoneAt = null, export = null,
            counters = if (early) state.counters.copy(arrivals = state.counters.arrivals - 1) else state.counters,
            arrivalPending = early,
        )
    }

    /**
     * 처음 넣은 값 고치기. 받은 날과 그때의 주행거리로 계산한 순간(함께한 날, 함께한 거리)은
     * 예전 값 기준이라 지우고, 앞으로 맞는 것부터 다시 남긴다. 앱이 센 횟수는 그대로다.
     */
    fun correctSetup(state: AppState, receivedDay: Long, receivedOdoKm: Double, now: Long, carName: String? = state.setup?.carName): AppState {
        val before = state.setup ?: return state
        if (state.drive != null || state.last == LastDrive.DONE) return state
        // 차 이름이 바뀌면 이미 남은 순간의 문장에서도 이름을 바꾼다. 순간 자체는 그대로 둔다.
        val name = Fmt.cleanName(carName)
        @Suppress("NAME_SHADOWING") val state = if (name == before.carName) state else {
            val old = "${Fmt.subject(before.carName)} 지켜본"
            val new = "${Fmt.subject(name)} 지켜본"
            state.copy(
                setup = before.copy(carName = name), export = null,
                moments = state.moments.map { it.copy(line = it.line.replace(old, new), why = it.why.replace(old, new)) },
            )
        }
        val setup = state.setup ?: return state
        val maxKm = setup.installedOdoKm ?: state.lastOdoKm
        val fixed = setup.copy(
            receivedDay = receivedDay.coerceAtMost(setup.installedDay),
            receivedOdoKm = receivedOdoKm.coerceAtLeast(0.0).let { if (maxKm != null) it.coerceAtMost(maxKm) else it },
        )
        if (fixed == setup) return state
        val kept = state.moments.filter { it.kind != MomentKind.DAYS && it.kind != MomentKind.DISTANCE }
        return checkDays(state.copy(setup = fixed, moments = kept, export = null), now).state
    }

    // ---------- 그날의 날씨 ----------

    /** 날씨를 남길 관측소를 고른다(또는 바꾼다). 받은 날의 날씨는 새 관측소로 다시 묻는다. */
    fun setStation(state: AppState, id: Int, name: String): AppState {
        val setup = state.setup ?: return state
        if (state.last == LastDrive.DONE || (setup.stationId == id && setup.stationName == name)) return state
        return state.copy(setup = setup.copy(stationId = id, stationName = name, receivedWeather = null, receivedWeatherDone = false))
    }

    /**
     * 아직 날씨를 모르는 날들. 관측 자료는 다음 날부터 나오므로 어제까지만 묻는다.
     * 받아 가려고 올린 한 권이 있는 동안에는 묻지 않는다(올린 내용이 달라지지 않게).
     */
    fun weatherWanted(state: AppState, now: Long, limit: Int = WEATHER_BATCH): List<WeatherQuery> {
        val setup = state.setup ?: return emptyList()
        val home = setup.stationId ?: return emptyList()
        if (state.export != null) return emptyList()
        val today = day(now)
        val out = mutableListOf<WeatherQuery>()
        if (!setup.receivedWeatherDone && setup.receivedDay < today) {
            out += WeatherQuery(null, home, date(setup.receivedDay).toString(), null)
        }
        for (m in state.moments.sortedBy { it.at }) {
            if (out.size >= limit) break
            if (m.weatherDone || day(m.at) >= today) continue
            out += WeatherQuery(m.id, m.stationId ?: home, date(day(m.at)).toString(), if (m.hasTime) minuteOfDay(m.at) / 60 else null)
        }
        return out.take(limit)
    }

    /** 물음 하나의 답을 적는다. weather가 null이면 그날 자료가 없다는 뜻이고 다시 묻지 않는다. */
    fun applyWeather(state: AppState, q: WeatherQuery, weather: Weather?): AppState {
        val setup = state.setup ?: return state
        if (state.export != null) return state
        if (q.momentId == null) {
            if (setup.stationId != q.stationId || setup.receivedWeatherDone) return state
            return state.copy(setup = setup.copy(receivedWeather = weather, receivedWeatherDone = true))
        }
        return state.copy(moments = state.moments.map {
            if (it.id == q.momentId && !it.weatherDone) it.copy(weather = weather, weatherDone = true) else it
        })
    }

    /** 예시 값으로 채운 날씨는 다음에 다시 묻는다(서버에 인증키가 생기면 관측 자료로 바뀐다). */
    fun forgetSampleWeather(state: AppState): AppState {
        val setup = state.setup ?: return state
        if (state.export != null) return state
        val s = if (setup.receivedWeather?.sample == true) setup.copy(receivedWeather = null, receivedWeatherDone = false) else setup
        return state.copy(setup = s, moments = state.moments.map {
            if (it.weather?.sample == true) it.copy(weather = null, weatherDone = false) else it
        })
    }

    fun markSeen(state: AppState, id: String): AppState =
        state.copy(moments = state.moments.map { if (it.id == id) it.copy(seen = true) else it })

    /** 차에 남은 기록 지우기. 휴대폰에서 저장을 누른 것이 확인된 뒤에만 된다. */
    fun wipe(state: AppState, now: Long): AppState {
        val setup = state.setup ?: return state
        if (state.last != LastDrive.DONE || state.export?.savedAt == null) return state
        return AppState(wiped = Wiped(setup.receivedDay, daysTogether(setup, state.lastDoneAt ?: now), now, setup.carName))
    }

    /**
     * 시연용 예시 기록의 날짜를 오늘 기준으로 옮긴다. 예시 기록은 refDay를 "오늘"로 두고 만들었다.
     * 받은 날, 설치한 날, 센 날, 순간의 시각을 모두 같은 날수만큼 옮긴다.
     */
    fun shiftDays(state: AppState, refDay: Long, today: Long): AppState {
        val d = today - refDay
        if (d == 0L) return state
        val ms = d * 86_400_000L
        val setup = state.setup?.let { it.copy(receivedDay = it.receivedDay + d, installedDay = it.installedDay + d) }
        val c = state.counters
        return state.copy(
            setup = setup,
            counters = c.copy(
                lastCompanionDay = c.lastCompanionDay?.plus(d),
                lastFullDay = c.lastFullDay?.plus(d),
                firstFullDay = c.firstFullDay?.plus(d),
            ),
            moments = state.moments.map { it.copy(at = it.at + ms) },
        )
    }

    companion object {
        const val LIVE_JUMP_KM = 5.0
        const val WEATHER_BATCH = 12
        /** 이보다 짧게 P를 벗어났다 돌아오고 주행거리도 그대로면 주행이 아니다 */
        const val NO_MOVE_MS = 2 * 60_000L
        /** 남은 에너지가 이만큼 올라야 충전(또는 회생)으로 보고 기준값을 옮긴다 */
        const val BATTERY_RISE_WH = 100.0
        const val NEAR_KM = 10.0
        const val ANNOUNCE_KM = 3.0
        const val DISTANCE_STEP_KM = 10_000.0
        const val EARTH_KM = 40_075.0
        /** 지구에서 달까지의 평균 거리 */
        const val MOON_KM = 384_400.0
        const val COMPANION_STEP = 50
        const val DAYS_STEP = 100L
        const val LONG_TRIP_KM = 300.0
        const val HEAT_C = 33f
        const val ARRIVAL_STEP = 1000
        /** 남길 계기판 숫자들(작은 것부터) */
        val NUMBERS: List<Long> = buildList {
            for (k in 1..9) { add(11_111L * k); add(111_111L * k); add(1_111_111L * k) }
            for (a in 1..5) add((0 until 5).fold(0L) { acc, i -> acc * 10 + a + i })
            for (a in 1..4) add((0 until 6).fold(0L) { acc, i -> acc * 10 + a + i })
            add(1_234_567L)
            for (k in 1..9) add(100_000L * k)
            add(1_000_000L)
        }.distinct().sorted()
    }
}

