package com.unjeonanbu.car.vehicle

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
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

/**
 * 주차 후 요약을 읽어 준다. Pleos TextToSpeech(ON_DEVICE)를 먼저 쓰고,
 * 초기화에 실패한 환경에서는 Android 기본 TTS 로 대신한다.
 * 오디오 포커스를 얻은 뒤 Pleos 가이드의 pre-roll 무음(150~200ms)을 두고 재생한다.
 */
class Speaker(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs)
        .build()

    private var pleos: TextToSpeech? = null
    private var fallback: android.speech.tts.TextToSpeech? = null

    private val _engine = MutableStateFlow("준비 중")
    /** 지금 쓰는 음성 엔진과 상태. 신호 확인 화면에 그대로 보여 준다. */
    val engine: StateFlow<String> = _engine

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking

    init {
        try {
            val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    tts.setLocale(Locale.KOREA)
                    tts.setAudioAttributes(attrs)
                    tts.addEventListener(object : EventListener {
                        override fun onStart() { _speaking.value = true }
                        override fun onReady() {}
                        override fun onDone() = finished()
                        override fun onStop() = finished()
                        override fun onUpdatedRms(rms: Double) {}
                        override fun onError(errorType: ErrorType) {
                            _engine.value = "Pleos TextToSpeech 오류: $errorType"
                            finished()
                        }
                    })
                    pleos = tts
                    _engine.value = "Pleos TTS · ON_DEVICE"
                }

                override fun onFailure(errorType: ErrorType) {
                    Log.w(TAG, "Pleos TTS init failed: $errorType")
                    useFallback("Pleos 초기화 실패: $errorType")
                }
            })
        } catch (t: Throwable) {
            Log.w(TAG, "Pleos TTS unavailable", t)
            useFallback("Pleos 사용 불가: ${t.javaClass.simpleName}")
        }
    }

    private fun useFallback(reason: String) = main.post {
        fallback = android.speech.tts.TextToSpeech(context.applicationContext) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                fallback?.language = Locale.KOREA
                fallback?.setAudioAttributes(attrs)
                _engine.value = "Android 기본 TTS ($reason)"
            } else {
                _engine.value = "음성 엔진 없음 ($reason)"
            }
        }
    }

    private fun finished() {
        _speaking.value = false
        audio.abandonAudioFocusRequest(focus)
    }

    fun speak(text: String) {
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        main.postDelayed({
            val p = pleos
            val f = fallback
            when {
                p != null -> p.speak(text, true)
                f != null -> {
                    _speaking.value = true
                    f.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "anbu")
                    main.postDelayed({ if (f.isSpeaking.not()) finished() }, 15_000)
                }
                else -> finished()
            }
        }, PRE_ROLL_MS)
    }

    fun stop() {
        pleos?.stop()
        fallback?.stop()
        finished()
    }

    companion object {
        private const val TAG = "UnjeonAnbu"
        private const val PRE_ROLL_MS = 180L
    }
}
