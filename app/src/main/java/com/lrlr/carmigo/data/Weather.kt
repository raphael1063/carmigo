package com.lrlr.carmigo.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos

/** 기상청 지상(종관) 관측소 하나 */
@Serializable
data class Station(val id: Int, val name: String, val lat: Double, val lon: Double)

/**
 * 앱에 넣어 둔 관측소 목록. 가장 가까운 곳은 차 안에서 고르고, 밖으로는 관측소 번호만 나간다.
 * 차의 좌표는 어디에도 보내지 않고 저장하지도 않는다.
 */
class Stations(private val context: Context) {
    val all: List<Station> by lazy {
        try {
            AppJson.decodeFromString<List<Station>>(context.assets.open("stations.json").use { it.readBytes().toString(Charsets.UTF_8) })
        } catch (e: Exception) {
            Log.w("DaysWeather", "stations unreadable", e)
            emptyList()
        }
    }

    fun byId(id: Int): Station? = all.firstOrNull { it.id == id }

    fun nearest(lat: Double, lon: Double): Station? = nearest(all, lat, lon)

    private fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 차가 마지막으로 안 위치에서 가장 가까운 관측소. 권한이 없거나 위치를 모르면 null. */
    fun nearestNow(): Station? {
        if (!hasPermission()) return null
        return try {
            val lm = context.getSystemService(LocationManager::class.java) ?: return null
            var best: Location? = null
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                if (best == null || l.time > best!!.time) best = l
            }
            best?.let { nearest(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            Log.w("DaysWeather", "location unreadable", e)
            null
        }
    }

    /**
     * 지금 위치를 한 번 물어 가장 가까운 관측소를 돌려준다. 못 구하면 null.
     * Pleos FusedLocation에 먼저 묻고, 몇 초 안에 답이 없으면 안드로이드 위치 서비스로 넘어간다.
     * 받은 좌표는 관측소를 고르는 데만 쓰고 바로 버린다.
     */
    fun locate(onResult: (Station?) -> Unit) {
        if (!hasPermission()) { onResult(nearestNow()); return }
        val main = Handler(Looper.getMainLooper())
        var done = false
        var fused: FusedLocation? = null
        lateinit var listener: FusedLocationListener
        fun finish(station: Station?) {
            if (done) return
            done = true
            fused?.let { f ->
                runCatching { f.unregisterFusedLocationCallback(listener) }
                runCatching { f.release() }
            }
            if (station != null) onResult(station) else locateAndroid(onResult)
        }
        listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if (location == null) return
                val st = nearest(location.latitude, location.longitude)
                main.post { finish(st) }
            }
            override fun onError(exception: Exception) {
                Log.w("DaysWeather", "pleos location error", exception)
                main.post { finish(null) }
            }
        }
        try {
            fused = FusedLocation(context.applicationContext).also {
                it.initialize()
                it.registerFusedLocationCallback(listener)
            }
        } catch (t: Throwable) {
            Log.w("DaysWeather", "pleos location unavailable", t)
            finish(null)
            return
        }
        main.postDelayed({ finish(null) }, PLEOS_WAIT_MS)
    }

    /** 안드로이드 위치 서비스로 한 번 묻는다(Pleos 위치가 답하지 않을 때). */
    private fun locateAndroid(onResult: (Station?) -> Unit) {
        val known = nearestNow()
        if (Build.VERSION.SDK_INT < 30) { onResult(known); return }
        try {
            val lm = context.getSystemService(LocationManager::class.java)
            val provider = lm?.getProviders(true)?.let { ps ->
                listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in ps }
            }
            if (lm == null || provider == null) { onResult(known); return }
            lm.getCurrentLocation(provider, null, context.mainExecutor) { l ->
                onResult(l?.let { nearest(it.latitude, it.longitude) } ?: known)
            }
        } catch (e: Exception) {
            onResult(known)
        }
    }

    companion object {
        private const val PLEOS_WAIT_MS = 4_000L

        fun nearest(all: List<Station>, lat: Double, lon: Double): Station? {
            val k = cos(Math.toRadians(lat))
            return all.minByOrNull { s ->
                val dy = s.lat - lat
                val dx = (s.lon - lon) * k
                dx * dx + dy * dy
            }
        }
    }
}

/** 서버가 준 답 하나 */
sealed interface WeatherAnswer {
    data class Found(val sky: String?, val hi: Int?, val sample: Boolean) : WeatherAnswer
    /** 그날 그 관측소에 자료가 없다. 다시 묻지 않는다. */
    data object None : WeatherAnswer
    /** 아직 자료가 나오지 않았거나 통신이 안 됐다. 다음에 다시 묻는다. */
    data object Later : WeatherAnswer
}

/** 그날의 날씨를 우리 서버에 묻는다. 보내는 것은 관측소 번호, 날짜, 시(時)뿐이다. */
class WeatherClient(private val server: String) {
    fun ask(stationId: Int, date: String, hour: Int?): WeatherAnswer {
        var conn: HttpURLConnection? = null
        return try {
            val q = "stn=$stationId&date=$date" + (hour?.let { "&hour=$it" } ?: "")
            conn = (URL("$server/api/days/weather?$q").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 10_000
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            parse(code, body)
        } catch (e: Exception) {
            Log.w("DaysWeather", "ask failed: ${e.message}")
            WeatherAnswer.Later
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        fun parse(code: Int, body: String): WeatherAnswer = try {
            val o = AppJson.parseToJsonElement(body).jsonObject
            when {
                code in 200..299 -> WeatherAnswer.Found(
                    sky = o["sky"]?.jsonPrimitive?.contentOrNull,
                    hi = o["hi"]?.jsonPrimitive?.intOrNull,
                    sample = o["sample"]?.jsonPrimitive?.booleanOrNull == true,
                )
                code == 404 && o["error"]?.jsonPrimitive?.contentOrNull == "no_data" -> WeatherAnswer.None
                else -> WeatherAnswer.Later
            }
        } catch (e: Exception) {
            WeatherAnswer.Later
        }
    }
}
