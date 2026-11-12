package com.lrlr.carmigo

import android.content.Context
import android.content.pm.ApplicationInfo
import com.lrlr.carmigo.car.CarFeed
import com.lrlr.carmigo.car.Gear
import com.lrlr.carmigo.car.PleosCarFeed
import com.lrlr.carmigo.car.Speaker
import com.lrlr.carmigo.car.UxRestrictionWatcher
import com.lrlr.carmigo.core.AppState
import com.lrlr.carmigo.core.LastDrive
import com.lrlr.carmigo.core.Seats
import com.lrlr.carmigo.core.Rules
import com.lrlr.carmigo.core.Snapshot
import com.lrlr.carmigo.core.Step
import com.lrlr.carmigo.data.BookClient
import com.lrlr.carmigo.data.Books
import com.lrlr.carmigo.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 기록 받아 가기 화면의 진행 상태. */
enum class Sending { IDLE, UPLOADING, READY, FAILED }

/**
 * 차량 신호를 규칙에 넣고 결과를 저장하는 곳. 화면이 없어도(부팅 뒤 서비스만 떠 있어도) 돈다.
 * 모든 상태 변경은 메인 스레드에서 차례로 일어난다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Controller(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val store = Store(context)
    private val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    var rules = Rules()
        private set
    private val books = BookClient(BuildConfig.BOOK_SERVER)

    val car: CarFeed = PleosCarFeed(context)
    val ux = UxRestrictionWatcher(context)
    val speaker = Speaker(context)
    val notifier = Notifier(context)
    val stations = com.lrlr.carmigo.data.Stations(context)
    private val weather = com.lrlr.carmigo.data.WeatherClient(BuildConfig.BOOK_SERVER)
    private var weatherJob: kotlinx.coroutines.Job? = null
    private var weatherAt = 0L
    /** 화면을 열지 않은 사이에 남아 아직 알리지 않은 순간의 제목 */
    private val unannounced = mutableListOf<String>()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    private val _sending = MutableStateFlow(Sending.IDLE)
    val sending: StateFlow<Sending> = _sending
    /** 화면이 "지금"을 다시 계산하게 하는 시계(30초마다) */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    private var started = false
    /** 파일 쓰기는 한 줄로만 한다. 쓰는 순간의 최신 상태를 쓰므로 순서가 뒤바뀌지 않는다. */
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private var saveScheduled = false
    private var pollJob: Job? = null
    /** 받아 가기 화면이 열려 있는지. 열려 있는 동안에만 서버에 저장 여부를 묻는다. */
    private var exportVisible = false
    private var reupload = false
    /** 앱 화면이 보이고 있는지. 가려진 동안에는 올리지도 묻지도 않는다. */
    private var shown = false
    /** 앱이 켜진 뒤 처음 받은 값은 "지켜본 변화"가 아니다(꺼진 사이의 차이는 세지 않는다). */
    private var odoSeen = false
    private var batterySeen = false
    private var tempC: Float? = null
    private var tempAt = 0L

    fun start() {
        if (started) return
        started = true
        // 앱이 꺼질 때 진행 중이던 주행은 끝을 보지 못했으므로 세지 않는다
        _state.value = rules.forgetSampleWeather(rules.dropUnseenDrive(store.load()))
        if (_state.value.demo) rules = Rules(arrivalMs = DEMO_ARRIVAL_MS)
        fillWeather(force = true)
        car.start()
        ux.start()
        speaker.start()

        // 시연 모드에서는 차의 신호 대신 시연 버튼으로 움직인다(실제 기록과 섞이지 않게)
        scope.launch { car.odometerKm.collect { km -> if (km != null && !_state.value.demo) onOdometer(km) } }
        scope.launch { car.gear.collect { if (!_state.value.demo) onGear(it) } }
        scope.launch { car.seats.drop(1).collect { if (!_state.value.demo) apply(rules.onSeats(_state.value, it)) } }
        scope.launch { car.batteryWh.collect { wh -> if (wh != null && !_state.value.demo) onBattery(wh) } }
        scope.launch {
            while (true) {
                val t = System.currentTimeMillis()
                _now.value = t
                apply(rules.onTick(_state.value, t))
                delay(minOf(TICK_MS, rules.arrivalMs / 2).coerceAtLeast(1_000L))
            }
        }
        if (isRecording()) RecorderService.start(context)
    }

    /** 권한을 허용받은 뒤 차량 신호를 다시 구독한다. */
    fun reconnectCar() {
        car.stop()
        car.start()
    }

    /** 첫 입력을 마쳤고 지우지 않은 동안에만 기록한다. */
    fun isRecording(): Boolean = _state.value.setup != null

    /** 바깥 기온은 순간 카드에만 쓰므로 가끔만 읽는다. */
    private fun outsideTemp(now: Long): Float? {
        if (now - tempAt > TEMP_MS) {
            tempC = car.outsideTempC()
            tempAt = now
        }
        return tempC
    }

    private fun snapshot(): Snapshot {
        val now = System.currentTimeMillis()
        return Snapshot(now = now, seats = car.seats.value.count, outsideTempC = outsideTemp(now))
    }

    private fun apply(step: Step, urgent: Boolean = true) {
        commit(stampStation(step.state, step.added), urgent)
        step.speak?.let { speaker.say(it) }
        unannounced += step.added.map { it.fullTitle }
        announce()
    }

    /** 날씨를 남기기로 했으면, 새 순간에 그때 가장 가까운 관측소 번호를 적어 둔다. 좌표는 적지 않는다. */
    private fun stampStation(s: AppState, added: List<com.lrlr.carmigo.core.Moment>): AppState {
        if (added.isEmpty() || s.setup?.stationId == null) return s
        val near = stations.nearestNow()?.id ?: return s
        val ids = added.map { it.id }.toSet()
        return s.copy(moments = s.moments.map { if (it.id in ids && it.stationId == null) it.copy(stationId = near) else it })
    }

    /** 날씨를 남길 관측소를 고른다. 고른 뒤 곧바로 지난 날들의 날씨를 묻는다. */
    fun setStation(id: Int) {
        val st = stations.byId(id) ?: return
        commit(rules.setStation(_state.value, st.id, st.name))
        fillWeather(force = true)
    }

    /**
     * 아직 날씨를 모르는 날들을 서버에 묻는다. 관측소를 고른 경우에만, 한 시간에 한 번까지만.
     * 보내는 것은 관측소 번호와 날짜, 시(時)뿐이다. 통신이 안 되면 다음에 다시 묻고, 어림값으로 채우지 않는다.
     */
    fun fillWeather(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (weatherJob?.isActive == true) return
        if (!force && now - weatherAt < WEATHER_EVERY_MS) return
        if (rules.weatherWanted(_state.value, now, 1).isEmpty()) return
        weatherAt = now
        weatherJob = scope.launch {
            repeat(WEATHER_MAX) {
                val q = rules.weatherWanted(_state.value, System.currentTimeMillis(), 1).firstOrNull() ?: return@launch
                val name = stations.byId(q.stationId)?.name ?: _state.value.setup?.stationName ?: return@launch
                when (val a = withContext(Dispatchers.IO) { weather.ask(q.stationId, q.date, q.hour) }) {
                    is com.lrlr.carmigo.data.WeatherAnswer.Later -> {
                        // 통신이 안 됐으면 한 시간을 기다리지 않고 몇 분 뒤 화면을 열 때 다시 묻는다
                        weatherAt = System.currentTimeMillis() - WEATHER_EVERY_MS + WEATHER_RETRY_MS
                        return@launch
                    }
                    is com.lrlr.carmigo.data.WeatherAnswer.None -> commit(rules.applyWeather(_state.value, q, null))
                    is com.lrlr.carmigo.data.WeatherAnswer.Found -> {
                        // 예시 값은 시험판에서만 받는다
                        if (a.sample && !debuggable) return@launch
                        val w = if (a.sky == null && a.hi == null) null else com.lrlr.carmigo.core.Weather(a.sky, name, a.hi, a.sample)
                        commit(rules.applyWeather(_state.value, q, w))
                    }
                }
            }
        }
    }

    /** 새 순간은 화면이 닫혀 있고 차가 서 있을 때만 알린다. 달리는 중에 남은 것은 세운 뒤에 알린다. */
    private fun announce() {
        if (unannounced.isEmpty()) return
        if (shown) { unannounced.clear(); return }
        if (car.gear.value != Gear.PARK) return
        notifier.moments(unannounced.toList())
        unannounced.clear()
    }

    private fun commit(next: AppState, urgent: Boolean = true) {
        val prev = _state.value
        if (next == prev) return
        // 한 권의 내용이 달라졌으면 예전 저장 확인은 더는 지금 기록을 덮지 못한다
        val state = if (next.export != null && next.export == prev.export && !rules.sameBook(prev, next)) {
            _sending.value = Sending.IDLE
            // 받아 가기 화면이 보이고 있으면 달라진 내용으로 곧바로 다시 올린다(QR이 사라진 채로 두지 않는다).
            // 화면이 가려진 동안에는 아무것도 올리지 않는다.
            // 주소는 그대로 둔다. 휴대폰이 이미 QR을 찍었을 수 있다.
            if (exportVisible && shown) {
                reupload = true
                next.copy(export = next.export.copy(uploadedAt = null, savedAt = null))
            } else next.copy(export = null)
        } else next
        _state.value = state
        // 받아 가기 화면이 보이는 동안 달라진 내용은 새 상태를 적은 뒤에 다시 올린다
        if (reupload) { reupload = false; sendBook() }
        if (urgent) {
            scope.launch(writer) { store.save(_state.value.copy(wiped = null)) }
        } else if (!saveScheduled) {
            // 주행거리와 배터리처럼 자주 오는 값은 모아서 쓴다(늦어도 몇 초 안에는 쓴다)
            saveScheduled = true
            scope.launch {
                delay(SAVE_DELAY_MS)
                saveScheduled = false
                withContext(writer) { store.save(_state.value.copy(wiped = null)) }
            }
        }
    }

    private fun onOdometer(km: Double) {
        val s = _state.value
        if (!odoSeen) {
            odoSeen = true
            if (s.last != com.lrlr.carmigo.core.LastDrive.DONE) commit(s.copy(lastOdoKm = km), urgent = false)
            return
        }
        val step = rules.onOdometer(s, km, snapshot())
        apply(step, urgent = step.added.isNotEmpty() || step.speak != null)
    }

    private fun onBattery(wh: Double) {
        val s = _state.value
        if (!batterySeen) {
            batterySeen = true
            commit(s.copy(counters = s.counters.copy(lastLevelWh = wh)), urgent = false)
            return
        }
        apply(rules.onBattery(s, wh), urgent = false)
    }

    private fun onGear(gear: Gear) {
        if (gear == Gear.UNKNOWN) return
        // 달리기 시작할 때 운전석은 반드시 차 있다. 그렇지 않게 읽히면 착석 값의 방향을 바로잡는다.
        if (gear == Gear.MOVING) car.calibrateSeats()
        apply(rules.onGear(_state.value, gear == Gear.PARK, car.odometerKm.value, car.seats.value, snapshot()))
    }

    // ---------- 화면에서 부르는 것 ----------

    fun begin(receivedDay: Long, receivedOdoKm: Double, carName: String?) {
        val now = System.currentTimeMillis()
        var s = rules.begin(_state.value.copy(wiped = null), receivedDay, receivedOdoKm, car.odometerKm.value, now, carName)
        // 이미 달리는 중에 입력을 마쳤다면 지금 주행부터 센다
        if (car.gear.value == Gear.MOVING) {
            s = rules.onGear(s, false, car.odometerKm.value, car.seats.value, snapshot()).state
        }
        commit(rules.checkDays(s, now).state)
        RecorderService.start(context)
    }

    /** 처음 넣은 값 고치기(주차 중에만). */
    fun correctSetup(receivedDay: Long, receivedOdoKm: Double, carName: String?) =
        commit(rules.correctSetup(_state.value, receivedDay, receivedOdoKm, System.currentTimeMillis(), carName))

    fun armLast() = commit(rules.armLast(_state.value))

    fun disarmLast() = commit(rules.disarmLast(_state.value))

    fun undoLast() {
        commit(rules.undoLast(_state.value, System.currentTimeMillis()))
        _sending.value = Sending.IDLE
        pollJob?.cancel()
    }

    fun markSeen(id: String) = commit(rules.markSeen(_state.value, id))

    /**
     * 기록 받아 가기: 지금까지의 한 권을 올리고 QR을 보여 준다.
     * 이미 올린 한 권이 있으면 먼저 휴대폰에서 저장했는지 묻고, 아직이면 지금 내용으로 다시 올린다.
     */
    fun sendBook() {
        if (_sending.value == Sending.UPLOADING) return
        dropStaleExport()
        val s = _state.value
        val old = s.export
        // 내용이 그대로이고 저장도 확인된 한 권은 다시 올리지 않는다
        if (old?.savedAt != null) { _sending.value = Sending.READY; return }
        val book = Books.build(s, rules, System.currentTimeMillis()) ?: return
        _sending.value = Sending.UPLOADING
        scope.launch {
            if (old?.uploadedAt != null) {
                val at = withContext(Dispatchers.IO) { books.savedAt(old) }
                if (at != null && at > 0 && confirmSaved(old, at)) {
                    _sending.value = Sending.READY
                    return@launch
                }
            }
            val export = old ?: Books.newExport()
            val ok = withContext(Dispatchers.IO) { books.upload(export, book) }
            // 올리는 사이에 기록이 달라졌거나 지워졌으면 이 한 권은 쓰지 않는다
            if (!ok || !rules.sameBook(s, _state.value) || _state.value.export != old) {
                _sending.value = if (ok) Sending.IDLE else Sending.FAILED
                // 올리는 사이에 내용이 달라졌으면 열려 있는 창을 위해 한 번 더 올린다
                if (ok && exportVisible && shown && _state.value.export != null) sendBook()
                return@launch
            }
            commit(_state.value.copy(export = export.copy(uploadedAt = System.currentTimeMillis(), savedAt = null)))
            _sending.value = Sending.READY
            if (exportVisible && shown) pollSaved()
        }
    }

    private fun confirmSaved(e: com.lrlr.carmigo.core.Export, at: Long): Boolean {
        // 물어본 그 한 권이 그대로일 때만 받아들인다
        if (_state.value.export != e || isStale(e)) return false
        commit(_state.value.copy(export = e.copy(savedAt = at)))
        // 화면이 닫혀 있는 사이에 확인됐으면 주차 중일 때 알린다
        if (!shown && car.gear.value != Gear.MOVING) notifier.saved(canWipe = _state.value.last == com.lrlr.carmigo.core.LastDrive.DONE)
        return true
    }

    /** 한 권에는 날수가 들어간다. 마지막 주행을 마치기 전에는 날이 바뀌면 올려 둔 한 권이 지금 기록과 달라진다. */
    private fun isStale(e: com.lrlr.carmigo.core.Export): Boolean {
        val at = e.uploadedAt ?: return false
        return _state.value.last != com.lrlr.carmigo.core.LastDrive.DONE && rules.day(at) != rules.day(System.currentTimeMillis())
    }

    private fun dropStaleExport() {
        val e = _state.value.export ?: return
        if (isStale(e)) { pollJob?.cancel(); _sending.value = Sending.IDLE; commit(_state.value.copy(export = null)) }
    }

    /** 화면이 가려지면 서버에 묻는 것을 멈추고, 다시 보이면 잇는다. */
    fun onHidden() { shown = false; pollJob?.cancel(); pollHidden() }
    fun onShown() { shown = true; fillWeather(); unannounced.clear(); notifier.clear(); pollJob?.cancel(); dropStaleExport(); if (exportVisible) pollSaved() }

    /**
     * 받아 가기를 누른 뒤 화면을 닫았을 때: 알림을 허용한 경우에만, 10분 동안만 휴대폰의 저장 확인을 더 묻는다.
     * 확인되면 주차 중 알림으로 알린다.
     */
    private fun pollHidden() {
        val first = _state.value.export ?: return
        if (first.uploadedAt == null || first.savedAt != null || !notifier.enabled()) return
        pollJob = scope.launch {
            repeat(HIDDEN_POLL_TIMES) {
                delay(HIDDEN_POLL_MS)
                val e = _state.value.export ?: return@launch
                if (shown || e.uploadedAt == null || e.savedAt != null) return@launch
                val at = withContext(Dispatchers.IO) { books.savedAt(e) }
                if (at != null && at > 0) { confirmSaved(e, at); return@launch }
            }
        }
    }

    /** 받아 가기 화면이 열렸다. 열려 있는 동안 휴대폰에서 저장을 확인했는지 서버에 묻는다. */
    fun watchSaved() {
        exportVisible = true
        pollSaved()
    }

    private fun pollSaved() {
        pollJob?.cancel()
        if (!shown) return
        pollJob = scope.launch {
            var asked = 0
            while (true) {
                val e = _state.value.export ?: return@launch
                if (e.uploadedAt == null || e.savedAt != null) return@launch
                val at = withContext(Dispatchers.IO) { books.savedAt(e) }
                if (at != null && at > 0) { confirmSaved(e, at); return@launch }
                // 처음 20분은 5초마다, 그 뒤로는 화면이 떠 있는 동안 1분마다
                delay(if (++asked < POLL_TIMES) POLL_MS else 60_000L)
            }
        }
    }

    fun stopWatchingSaved() {
        exportVisible = false
        pollJob?.cancel()
        if (_sending.value == Sending.FAILED) _sending.value = Sending.IDLE
    }

    /** 화면을 다시 열 때 한 번: 받아 가기 화면을 닫은 뒤에 휴대폰에서 저장했을 수 있다. */
    fun checkSavedOnResume() {
        dropStaleExport()
        val e = _state.value.export ?: return
        if (e.uploadedAt == null || e.savedAt != null || pollJob?.isActive == true || _sending.value == Sending.UPLOADING) return
        scope.launch {
            val at = withContext(Dispatchers.IO) { books.savedAt(e) } ?: return@launch
            if (at > 0) confirmSaved(e, at)
        }
    }

    /** 차에 남은 기록 지우기. 저장이 확인된 뒤에만 된다(규칙이 한 번 더 막는다). 서버에 올린 것은 7일 뒤 지워진다. */
    fun wipe() {
        val before = _state.value
        val after = rules.wipe(before, System.currentTimeMillis())
        if (after === before) return
        pollJob?.cancel()
        commit(after)
        _sending.value = Sending.IDLE
        scope.launch(writer) { store.clearLeftovers() }
        RecorderService.stop(context)
        if (before.demo) rules = Rules()
    }

    // 지운 뒤의 안내(받은 날, 함께한 날수)는 화면에만 있고 파일에는 쓰지 않는다.
    // 닫지 않고 차를 넘겨도 다음에 켜면 이전 주인의 날짜가 남아 있지 않다.
    /** 지운 뒤의 안내를 닫는다. 다음에 열면 첫 입력부터 다시 시작한다. */
    fun dismissWiped() = commit(_state.value.copy(wiped = null))

    /** 시연용: 디버그 빌드에서만 도착으로 세는 시간을 줄일 수 있다. */
    // ---------- 시연 모드 ----------

    private var demoJob: Job? = null

    /** 예시 기록(흰둥이, 3년 4개월)을 오늘 날짜에 맞춰 불러온다. 지금 이 앱의 기록은 지워진다. */
    fun demoLoad() {
        demoJob?.cancel()
        val text = context.assets.open("demo/sample.json").bufferedReader().use { it.readText() }
        val sample = runCatching { com.lrlr.carmigo.data.AppJson.decodeFromString(AppState.serializer(), text) }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        rules = Rules(arrivalMs = DEMO_ARRIVAL_MS)
        val s = rules.shiftDays(sample, java.time.LocalDate.of(2026, 11, 11).toEpochDay(), rules.day(now))
        _sending.value = Sending.IDLE
        commit(s.copy(demo = true, parkedSince = now, arrivalPending = false))
        RecorderService.start(context)
    }

    fun canDemoDrive(s: AppState = _state.value): Boolean =
        s.demo && s.setup != null && s.drive == null && s.last != LastDrive.DONE && demoJob?.isActive != true

    /**
     * 시연 주행: 기어를 D로 두고 달리다가 P에 놓는 것을 앱이 대신 한다.
     * 다음 이정표(함께 달린 거리나 계기판 숫자)가 10km 안에 있으면 그것을 지나갈 때까지, 아니면 5km를 달린다. 옆자리에 한 사람이 탄다.
     */
    fun demoDrive() {
        if (!canDemoDrive()) return
        demoJob = scope.launch {
            val seats = Seats(driver = true, passenger = true, rearLeft = false, rearRight = false)
            fun snap() = Snapshot(now = System.currentTimeMillis(), seats = seats.count, outsideTempC = outsideTemp(System.currentTimeMillis()))
            var km = _state.value.lastOdoKm ?: return@launch
            val received = _state.value.setup?.receivedOdoKm ?: return@launch
            val next = minOf(rules.nextNumber(km).toDouble(), received + rules.nextTogether(km - received))
            val target = if (next - km <= 10.0) next + 0.4 else km + 5.0
            apply(rules.onGear(_state.value, false, km, seats, snap()))
            delay(1_500)
            while (km < target - 1e-6) {
                delay(DEMO_STEP_MS)
                km = minOf(target, Math.round((km + 0.1) * 10) / 10.0)
                apply(rules.onOdometer(_state.value, km, snap()), urgent = false)
            }
            delay(1_500)
            apply(rules.onGear(_state.value, true, km, seats, snap()))
        }
    }

    /** 시연 모드를 끄고 첫 실행 상태로 돌아간다. */
    fun demoEnd() {
        demoJob?.cancel()
        rules = Rules()
        _sending.value = Sending.IDLE
        commit(AppState())
    }

    fun setDemoArrivalSeconds(seconds: Int) {
        if (!debuggable || seconds <= 0) return
        rules = Rules(arrivalMs = seconds * 1000L)
    }

    private companion object {
        const val TICK_MS = 30_000L
        /** 시연 모드에서는 P에 놓고 10초 머물면 도착으로 센다 */
        const val DEMO_ARRIVAL_MS = 10_000L
        /** 시연 주행에서 0.1km를 가는 데 걸리는 시간 */
        const val DEMO_STEP_MS = 300L
        const val SAVE_DELAY_MS = 2_000L
        const val POLL_MS = 5_000L
        const val POLL_TIMES = 240 // 20분
        const val HIDDEN_POLL_MS = 15_000L
        const val WEATHER_EVERY_MS = 60 * 60_000L
        const val WEATHER_MAX = 60
        const val WEATHER_RETRY_MS = 3 * 60_000L
        const val HIDDEN_POLL_TIMES = 40 // 10분
        const val TEMP_MS = 30_000L
    }
}
