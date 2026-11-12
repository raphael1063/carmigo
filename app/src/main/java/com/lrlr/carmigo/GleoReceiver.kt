package com.lrlr.carmigo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.lrlr.carmigo.core.AppState
import com.lrlr.carmigo.core.Fmt
import com.lrlr.carmigo.core.Rules
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import kotlin.math.floor

/**
 * 차의 음성 비서(Gleo)가 "카미고, 함께한 지 며칠이야?" 같은 물음을 받았을 때 부르는 곳.
 * 화면을 바꾸지 않고 사실만 돌려주며, 말하는 것은 비서가 한다. 앱이 먼저 말을 꺼내는 일은 여전히 없다.
 * 규격: assets/schema.json의 함수 이름이 곧 intent action이고, 답은 아래 broadcast로 돌려준다.
 */
class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GleoAnswer.GET_DAYS_TOGETHER) return
        val c = (context.applicationContext as DaysApp).controller
        val a = GleoAnswer.daysTogether(c.state.value, c.rules, System.currentTimeMillis())
        try {
            val response = JSONObject().apply {
                put("functionName", GleoAnswer.GET_DAYS_TOGETHER)
                put("status", a.status)
                put("message", a.message)
                a.data?.let { put("data", it) }
            }
            context.sendBroadcast(
                Intent(RESPONSE).putExtra("packageName", context.packageName).putExtra("response", response.toString()),
            )
            Log.i(TAG, "answered ${a.status}")
        } catch (e: Exception) {
            Log.w(TAG, "answer failed", e)
        }
    }

    private companion object {
        const val TAG = "DaysGleo"
        const val RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    }
}

/** 비서에게 돌려주는 답. 문장은 규격대로 영어이고, 숫자와 이름은 data에 넣어 비서가 운전자의 말로 옮기게 한다. */
object GleoAnswer {
    const val GET_DAYS_TOGETHER = "GET_DAYS_TOGETHER"

    data class Answer(val status: String, val message: String, val data: String?)

    fun daysTogether(state: AppState, rules: Rules, now: Long): Answer {
        val setup = state.setup
            ?: return Answer("fail", "The day the owner received this car has not been entered in the app yet.", null)
        val days = rules.daysTogether(setup, state.lastDoneAt ?: now)
        val km = rules.kmTogether(setup, state.lastOdoKm)?.let { floor(it + 1e-6).toLong() }
        val name = setup.carName
        val who = if (name != null) "the car named $name" else "this car"
        val message = if (km != null) "The owner has been with $who for $days days and they have driven $km kilometers together."
        else "The owner has been with $who for $days days."
        val item = buildJsonObject {
            if (name != null) put("carName", name)
            put("daysTogether", days)
            if (km != null) put("kmTogether", km)
            put("receivedDate", rules.date(setup.receivedDay).toString())
            put("spokenSummaryKo", "함께한 지 ${Fmt.n(days)}일" + (km?.let { ", 함께 달린 거리 ${Fmt.n(it)} km" } ?: ""))
        }
        return Answer("success", message, buildJsonArray { add(item) }.toString())
    }
}
