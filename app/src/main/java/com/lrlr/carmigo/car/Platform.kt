package com.lrlr.carmigo.car

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import android.speech.tts.TextToSpeech as AndroidTts
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * 주행 중 화면 제한(CarUxRestrictions). 제한이 걸리면 화면은 함께 달린 거리와 한 줄만 남긴다.
 * 차가 아닌 기기에서는 제한 없음으로 본다.
 */
class UxRestrictionWatcher(private val context: Context) {
    private val _restricted = MutableStateFlow(false)
    val restricted: StateFlow<Boolean> = _restricted
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        if (car != null) return
        try {
            val c = Car.createCar(context.applicationContext) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            manager = m
            apply(m.currentCarUxRestrictions)
            m.registerListener { apply(it) }
        } catch (t: Throwable) {
            // android.car가 없는 기기(휴대폰 등)
            Log.i(TAG, "CarUxRestrictions unavailable: ${t.message}")
        }
    }

    private fun apply(r: CarUxRestrictions?) {
        _restricted.value = r?.isRequiresDistractionOptimization == true
    }

    fun stop() {
        try {
            manager?.unregisterListener()
            car?.disconnect()
        } catch (_: Throwable) {
        }
        manager = null
        car = null
    }

    private companion object { const val TAG = "DaysUx" }
}

/**
 * 다가오는 계기판 숫자를 한 번 읽는 음성. Pleos TextToSpeech(온디바이스, 한국어)를 쓰고, 초기화가 안 되면 안드로이드 TTS로 읽는다.
 * 내비게이션 안내와 겹치지 않게 오디오 포커스를 잠깐 요청하고, 거절되면 포커스가 날 때까지 기다렸다가 읽는다.
 */
class Speaker(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null
    private var fallbackReady = false
    private var pending: String? = null
    private var focusRequest: AudioFocusRequest? = null
    /** 지금 읽는 문장이 끝난 뒤 이어 읽을 문장(시작 고지 뒤 도착 안내 등) */
    private var queued: String? = null
    /** 우리가 멈춘 문장의 늦은 onStop이 새 문장의 상태를 지우지 않게 센다 */
    private var stopsExpected = 0
    private var utterance = 0

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    /** 어느 엔진으로 읽는지. 시연과 디버깅용. */
    var engine: String = "준비 중"
        private set

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun start() {
        try {
            val tts = PleosTts(context.applicationContext, Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        try {
                            tts.setLocale(Locale.KOREA)
                            tts.setAudioAttributes(attributes)
                        } catch (e: Exception) {
                            Log.w(TAG, "pleos tts config failed", e)
                        }
                        // 에뮬레이터 확인(11-10): 리스너는 initialize 성공 뒤에 붙여야 한다(앞에 붙이면 매번 실패)
                        try { tts.addEventListener(object : EventListener {
                            override fun onStart() { _speaking.value = true }
                            override fun onReady() {}
                            override fun onDone() = finished()
                            override fun onError(errorType: ErrorType) {
                                Log.w(TAG, "pleos tts error $errorType")
                                finished()
                            }
                            override fun onStop() {
                                main.post {
                                    if (stopsExpected > 0) stopsExpected-- else finished()
                                }
                            }
                            override fun onUpdatedRms(rms: Double) {}
                        })
                        } catch (e: Exception) {
                            Log.w(TAG, "pleos tts listener failed", e)
                            startFallback()
                            return@post
                        }
                        pleosReady = true
                        engine = "Pleos TTS"
                    }
                }
                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "pleos tts init failed $errorType")
                    main.post { startFallback() }
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "pleos tts unavailable", t)
            startFallback()
        }
        // Pleos TTS가 응답하지 않으면 안드로이드 TTS도 준비해 둔다
        main.postDelayed({ if (!pleosReady) startFallback() }, 3_000)
    }

    private fun startFallback() {
        if (fallback != null) return
        fallback = AndroidTts(context.applicationContext) { status ->
            fallbackReady = status == AndroidTts.SUCCESS
            if (fallbackReady) {
                fallback?.language = Locale.KOREAN
                fallback?.setAudioAttributes(attributes)
                engine = "안드로이드 TTS"
            }
        }.also {
            it.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) { _speaking.value = true }
                override fun onDone(id: String?) = finished()
                @Deprecated("old API") override fun onError(id: String?) = finished()
            })
        }
    }

    /** 읽기. 이미 읽고 있으면 멈추고 새 문장을 읽는다. */
    fun say(text: String) = say(text, 0)

    /** 지금 읽는 문장을 끊지 않고 끝난 뒤 이어 읽는다. 읽는 중이 아니면 바로 읽는다. */
    fun sayAfter(text: String) {
        main.post {
            if (_speaking.value || pending != null) queued = text else say(text, 0)
        }
    }

    private fun say(text: String, attempt: Int) {
        main.post {
            stopNow()
            pending = text
            val asked = System.currentTimeMillis()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { change ->
                    // 기다리다 너무 늦게 차례가 오면 읽지 않는다(이미 지나간 안내가 된다)
                    if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        if (System.currentTimeMillis() - asked <= STALE_MS) speakPending() else stopNow()
                    } else if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopNow()
                }
                .build()
            focusRequest = req
            when (audio.requestAudioFocus(req)) {
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> speakPending()
                AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> Log.i(TAG, "waiting for navigation prompt to finish")
                // 거절되면 몇 번만 다시 시도한다
                else -> if (attempt < 5) main.postDelayed({ if (pending == text) say(text, attempt + 1) }, 1_500)
            }
        }
    }

    private fun speakPending(attempt: Int = 0) {
        val text = pending ?: return
        pending = null
        _speaking.value = true
        // 끝났다는 알림이 오지 않아도 오디오 포커스를 붙잡고 있지 않게 한다
        val turn = ++utterance
        main.postDelayed({ if (utterance == turn && _speaking.value) finished() }, UTTERANCE_MS)
        when {
            pleosReady -> try {
                pleos?.speak(text, true)
            } catch (e: Exception) {
                Log.w(TAG, "pleos speak failed", e)
                if (fallbackReady) fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "days") else finished()
            }
            fallbackReady -> fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "days")
            else -> {
                // 엔진이 아직 준비 전이면 잠시 뒤 다시 시도
                pending = text
                if (attempt < 20) main.postDelayed({ speakPending(attempt + 1) }, 500)
                else { pending = null; finished() }
            }
        }
    }

    private fun finished() {
        main.post {
            _speaking.value = false
            focusRequest?.let { audio.abandonAudioFocusRequest(it) }
            focusRequest = null
            queued?.let { next -> queued = null; say(next, 0) }
        }
    }

    fun stopNow() {
        pending = null
        queued = null
        if (_speaking.value && pleosReady) stopsExpected++
        try { pleos?.stop() } catch (_: Exception) {}
        fallback?.stop()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
        _speaking.value = false
    }

    fun release() {
        stopNow()
        try { pleos?.release() } catch (_: Exception) {}
        fallback?.shutdown()
    }

    private companion object {
        const val TAG = "DaysTts"
        const val STALE_MS = 20_000L
        const val UTTERANCE_MS = 15_000L
    }
}
