package com.lrlr.carmigo.core

import kotlinx.serialization.Serializable

/** 처음 한 번의 입력과 설치한 날(앱이 지켜보기 시작한 날). 날짜는 epochDay. */
@Serializable
data class Setup(
    val receivedDay: Long,
    val receivedOdoKm: Double,
    val installedDay: Long,
    /** 설치한 날의 계기판. 못 읽었으면 null. */
    val installedOdoKm: Double? = null,
    /** 차주가 정한 차 이름. 없으면 null. */
    val carName: String? = null,
    /** 그날의 날씨를 남기기로 했을 때 고른 기상청 관측소(번호와 이름). 고르지 않았으면 날씨를 남기지 않는다. */
    val stationId: Int? = null,
    val stationName: String? = null,
    /** 차를 받은 날의 날씨(관측소의 일자료) */
    val receivedWeather: Weather? = null,
    /** 받은 날의 날씨를 더 물을 필요가 없는지(받았거나, 자료가 없다는 답을 들음) */
    val receivedWeatherDone: Boolean = false,
)

/**
 * 기상청 관측소가 잰 그때의 하늘. 차가 있던 곳이 아니라 가까운 관측소의 값이라 관측소 이름을 늘 함께 적는다.
 * sky: 맑음, 구름많음, 흐림, 비, 눈 가운데 하나. hi는 일자료일 때의 최고 기온.
 */
@Serializable
data class Weather(
    val sky: String? = null,
    val station: String,
    val hi: Int? = null,
    /** 서버에 인증키가 없을 때의 예시 값(시험판에서만 받는다) */
    val sample: Boolean = false,
) {
    /** "비 · 서울 관측소" */
    val text: String get() = listOfNotNull(
        listOfNotNull(sky, hi?.let { "최고 ${it}°" }).joinToString(", ").ifEmpty { null },
        "$station 관측소" + if (sample) " (예시 값)" else "",
    ).joinToString(" · ")
}

/** 서버에 물을 날씨 하나. momentId가 null이면 차를 받은 날. hour가 null이면 그날의 일자료. */
data class WeatherQuery(val momentId: String?, val stationId: Int, val date: String, val hour: Int?)

enum class MomentKind(val label: String) {
    DAYS("함께한 날"),
    DISTANCE("함께 달린 거리"),
    PEOPLE("사람"),
    ODOMETER("계기판 숫자"),
    COINCIDENCE("우연"),
    SEASON("계절"),
    ARRIVAL("도착"),
}

/** 차가 알아챈 순간 한 장. 문장은 사실만 적는다. */
@Serializable
data class Moment(
    val id: String,
    val kind: MomentKind,
    val title: String,
    val line: String,
    val why: String,
    val at: Long,
    /** 시각까지 아는 순간인지(날짜만 아는 "함께한 지 N일"은 false) */
    val hasTime: Boolean = true,
    val odoKm: Double? = null,
    val outsideTempC: Float? = null,
    /** 그때의 날씨. 관측 자료는 다음 날부터 나오므로 나중에 채운다. */
    val weather: Weather? = null,
    /** 그 순간에 가장 가까웠던 관측소. 모르면 처음 고른 관측소로 묻는다. */
    val stationId: Int? = null,
    val weatherDone: Boolean = false,
    /** 그때 탄 자리 수. 못 읽었으면 null */
    val seats: Int? = null,
    val seen: Boolean = false,
) {
    /** 종류 표시 없이 제목만 놓이는 곳(작별 화면 목록, 알림)에서 쓰는 제목: "함께한 지 100일", "함께 40,000 km" */
    val fullTitle: String get() = when {
        kind == MomentKind.DAYS && !title.startsWith("함께") -> "함께한 지 $title"
        kind == MomentKind.DISTANCE && id.removePrefix("dist-").all { it.isDigit() } && id.startsWith("dist-") && !title.startsWith("함께") -> "함께 $title"
        else -> title
    }
}

/** 지금 진행 중인 주행. 기어가 P를 벗어나면 시작하고 P에 놓이면 끝난다. */
@Serializable
data class Drive(
    val startAt: Long,
    val startOdoKm: Double? = null,
    val companion: Boolean = false,
    val full: Boolean = false,
    val maxSeats: Int = 0,
    val isLast: Boolean = false,
    /** 이번 주행에서 지나간 계기판 숫자(주행 중 한 줄에 쓴다) */
    val passedNumber: Long? = null,
    /** 이번 주행에서 지나간 함께 달린 거리의 이정표(주행 중 한 줄에 쓴다) */
    val passedTogether: Long? = null,
    /** 마친 마지막 주행을 다시 이어 갈 때, 10분을 채우지 못하고 센 도착을 되돌려야 하는지 */
    val undoArrival: Boolean = false,
    /** 출발하기 전의 세워 둔 상태. 움직이지 않고 다시 P에 놓으면 그대로 되돌린다. */
    val prevParkedSince: Long? = null,
    val prevArrivalPending: Boolean = false,
)

/** 앱이 지켜본 뒤로 센 것. 어림하지 않는다. */
@Serializable
data class Counters(
    val arrivals: Int = 0,
    val companionDays: Int = 0,
    val lastCompanionDay: Long? = null,
    val fullDays: Int = 0,
    val lastFullDay: Long? = null,
    val firstFullDay: Long? = null,
    /** 함께 쓴 전기: 배터리의 남은 에너지가 줄어든 만큼을 더한 값 */
    val energyWh: Double = 0.0,
    val lastLevelWh: Double? = null,
)

enum class LastDrive { NONE, ARMED, DRIVING, DONE }

/** 기록 받아 가기 한 번. 주소는 추측할 수 없는 임의 값. */
@Serializable
data class Export(
    val id: String,
    val writeKey: String,
    val uploadedAt: Long? = null,
    /** 휴대폰에서 저장을 누른 것을 서버로 확인한 시각 */
    val savedAt: Long? = null,
)

/** 지운 뒤에 남기는 한 줄의 근거. */
@Serializable
data class Wiped(val receivedDay: Long, val days: Long, val at: Long, val carName: String? = null)

@Serializable
data class AppState(
    val setup: Setup? = null,
    val counters: Counters = Counters(),
    val moments: List<Moment> = emptyList(),
    val drive: Drive? = null,
    val last: LastDrive = LastDrive.NONE,
    val lastDoneAt: Long? = null,
    val lastOdoKm: Double? = null,
    /** P에 놓인 시각. 10분 머물면 도착 한 번으로 센다. */
    val parkedSince: Long? = null,
    val arrivalPending: Boolean = false,
    /** 음성으로 이미 알린 계기판 숫자 */
    val announced: List<Long> = emptyList(),
    /** 음성으로 이미 알린 함께 달린 거리의 이정표(km) */
    val announcedTogether: List<Long> = emptyList(),
    val export: Export? = null,
    val wiped: Wiped? = null,
    /** 시연 모드: 예시 기록을 불러와 차의 기어와 주행거리 신호 대신 시연 버튼으로 움직인다. */
    val demo: Boolean = false,
) {
    val unseen: Int get() = moments.count { !it.seen }
}

/** 순간을 남길 때 함께 적는 그때의 상황. */
data class Snapshot(
    val now: Long,
    val seats: Int? = null,
    val outsideTempC: Float? = null,
)

/** 좌석 네 자리. null은 못 읽음. */
data class Seats(
    val driver: Boolean? = null,
    val passenger: Boolean? = null,
    val rearLeft: Boolean? = null,
    val rearRight: Boolean? = null,
) {
    private val all get() = listOf(driver, passenger, rearLeft, rearRight)
    /** 하나도 못 읽었으면 null */
    val count: Int? get() = if (all.all { it == null }) null else all.count { it == true }
    val isFull: Boolean get() = all.all { it == true }
    val hasCompanion: Boolean get() = passenger == true
}
