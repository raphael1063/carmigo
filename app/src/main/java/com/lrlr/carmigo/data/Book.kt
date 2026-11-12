package com.lrlr.carmigo.data

import android.util.Base64
import android.util.Log
import com.lrlr.carmigo.core.AppState
import com.lrlr.carmigo.core.Export
import com.lrlr.carmigo.core.LastDrive
import com.lrlr.carmigo.core.Rules
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** 휴대폰으로 받아 가는 한 권. 여기에 적힌 것만 차 밖으로 나간다(위치, 차대번호, 사람 정보 없음). */
@Serializable
data class Book(
    val v: Int = 1,
    val carName: String? = null,
    /** 차를 받은 날의 날씨("맑음, 최고 29°")와 그 관측소 이름 */
    val receivedWeather: String? = null,
    val receivedStation: String? = null,
    val receivedDate: String,
    val receivedOdoKm: Double,
    val untilDate: String,
    val days: Long,
    val kmTogether: Double? = null,
    val odoKm: Double? = null,
    val isLast: Boolean = false,
    val watchedDays: Long,
    val arrivals: Int,
    val companionDays: Int,
    val fullDays: Int,
    val firstFullDate: String? = null,
    val energyKwh: Long? = null,
    val moments: List<Page> = emptyList(),
) {
    @Serializable
    data class Page(
        val kind: String,
        val title: String,
        val line: String,
        val why: String,
        val date: String,
        val time: String? = null,
        /** 카드의 하늘색은 시각으로 그리고, 날씨를 알면 구름과 비·눈을 더한다 */
        val minuteOfDay: Int? = null,
        val odoKm: Double? = null,
        val tempC: Float? = null,
        val seats: Int? = null,
        /** 그 시각 가까운 기상청 관측소의 하늘(맑음, 구름많음, 흐림, 비, 눈)과 관측소 이름 */
        val weather: String? = null,
        val station: String? = null,
    )
}

object Books {
    private val random = SecureRandom()

    private fun token(bytes: Int): String {
        val b = ByteArray(bytes).also { random.nextBytes(it) }
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /** 추측할 수 없는 주소(128비트)와 차만 아는 쓰기 키(256비트). */
    fun newExport(): Export = Export(id = token(16), writeKey = token(32))

    fun url(server: String, export: Export): String = "$server/days/b/#${export.id}"

    fun build(state: AppState, rules: Rules, now: Long): Book? {
        val setup = state.setup ?: return null
        val until = if (state.last == LastDrive.DONE) state.lastDoneAt ?: now else now
        val c = state.counters
        return Book(
            receivedDate = rules.date(setup.receivedDay).toString(),
            receivedOdoKm = setup.receivedOdoKm,
            untilDate = rules.date(rules.day(until)).toString(),
            days = rules.daysTogether(setup, until),
            kmTogether = rules.kmTogether(setup, state.lastOdoKm),
            odoKm = state.lastOdoKm,
            isLast = state.last == LastDrive.DONE,
            carName = setup.carName,
            receivedWeather = setup.receivedWeather?.let { w -> listOfNotNull(w.sky, w.hi?.let { "최고 ${it}°" }).joinToString(", ").ifEmpty { null } },
            receivedStation = setup.receivedWeather?.let { w -> w.station + if (w.sample) " (예시 값)" else "" },
            watchedDays = rules.watchedDays(setup, until),
            arrivals = c.arrivals,
            companionDays = c.companionDays,
            fullDays = c.fullDays,
            firstFullDate = c.firstFullDay?.let { rules.date(it).toString() },
            energyKwh = (c.energyWh / 1000).let { if (it >= 1) Math.floor(it + 1e-6).toLong() else null },
            moments = state.moments.map { m ->
                val minute = rules.minuteOfDay(m.at)
                Book.Page(
                    kind = m.kind.label, title = m.displayTitle, line = m.line, why = m.displayWhy,
                    date = rules.date(rules.day(m.at)).toString(),
                    time = if (m.hasTime) "%02d:%02d".format(minute / 60, minute % 60) else null,
                    minuteOfDay = if (m.hasTime) minute else null,
                    odoKm = m.odoKm, tempC = m.outsideTempC, seats = m.seats,
                    weather = m.weather?.let { w -> listOfNotNull(w.sky, w.hi?.let { "최고 ${it}°" }).joinToString(", ").ifEmpty { null } },
                    station = m.weather?.let { w -> w.station + if (w.sample) " (예시 값)" else "" },
                )
            },
        )
    }
}

/** 기록 받아 가기 서버와의 통신. 운전자가 버튼을 누른 뒤에만 불린다. */
class BookClient(private val server: String) {

    /** 한 권을 올린다. 성공하면 true. */
    fun upload(export: Export, book: Book): Boolean =
        call("PUT", "", export, AppJson.encodeToString(book)) != null

    /** 휴대폰에서 저장을 누른 시각(서버 기준). 아직이면 0, 통신 실패면 null. */
    fun savedAt(export: Export): Long? {
        val body = call("GET", "/status", export, null) ?: return null
        return try {
            AppJson.parseToJsonElement(body).jsonObject["savedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        } catch (e: Exception) {
            null
        }
    }

    private fun call(method: String, suffix: String, export: Export, body: String?): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL("$server/api/days/books/${export.id}$suffix").openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Authorization", "Bearer ${export.writeKey}")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "$method$suffix rejected: $code")
                null
            } else conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            Log.w(TAG, "$method$suffix failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private companion object { const val TAG = "DaysBook" }
}

