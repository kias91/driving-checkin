package com.unjeonanbu.car.vehicle

import com.unjeonanbu.car.data.Delta
import kotlin.math.abs

/**
 * 차량 신호 변화를 받아 주간 지표의 증가분으로 바꾼다.
 * Android 에 의존하지 않아 단위 테스트로 검증한다. 모든 시각은 밀리초.
 *
 * - 주행: 기어가 P 가 아닌 동안
 * - 회전: 주행 중 스티어링 각도가 TURN_ENTER 도 이상 꺾인 구간.
 *   꺾기 시작한 순간 방향지시등이 켜져 있었거나 SIGNAL_GRACE_MS 안에 꺼졌으면 '깜빡이를 켠 회전'으로 센다.
 * - 브레이크: 주행 중 페달이 밟힌 횟수(떼었다 다시 밟을 때 1회)
 * - 밤 운전: 주행 중 전조등(하향등)이 켜져 있던 시간
 * - 거리: 주행 중 누적 주행 거리(km)의 증가분
 */
class TripAggregator {
    var driving = false; private set
    var signalOn = false; private set
    var lowBeamOn = false; private set
    var braking = false; private set
    var inTurn = false; private set

    private var lastTick: Long? = null
    private var lastSignalOffAt: Long? = null
    private var lastOdometerKm: Double? = null

    private var drivingMs = 0L
    private var nightMs = 0L
    private var distanceKm = 0.0
    private var turns = 0
    private var turnsSignaled = 0
    private var brakePresses = 0

    private fun tick(now: Long) {
        val last = lastTick
        if (last != null && now > last && driving) {
            drivingMs += now - last
            if (lowBeamOn) nightMs += now - last
        }
        lastTick = now
    }

    fun onDriving(isDriving: Boolean, now: Long) {
        tick(now)
        driving = isDriving
        if (!isDriving) inTurn = false
    }

    fun onTurnSignal(on: Boolean, now: Long) {
        tick(now)
        if (signalOn && !on) lastSignalOffAt = now
        signalOn = on
    }

    fun onLowBeam(on: Boolean, now: Long) {
        tick(now)
        lowBeamOn = on
    }

    fun onBrakePedal(pressed: Boolean, now: Long) {
        tick(now)
        if (pressed && !braking && driving) brakePresses++
        braking = pressed
    }

    fun onSteeringAngle(degrees: Float, now: Long) {
        tick(now)
        val a = abs(degrees)
        if (!inTurn && driving && a >= TURN_ENTER) {
            inTurn = true
            turns++
            val off = lastSignalOffAt
            if (signalOn || (off != null && now - off <= SIGNAL_GRACE_MS)) turnsSignaled++
        } else if (inTurn && a <= TURN_EXIT) {
            inTurn = false
        }
    }

    fun onOdometerKm(km: Double, now: Long) {
        tick(now)
        val last = lastOdometerKm
        if (last != null && driving) {
            val d = km - last
            // 계기 초기화나 비정상 값은 버린다.
            if (d > 0.0 && d <= MAX_STEP_KM) distanceKm += d
        }
        lastOdometerKm = km
    }

    /** 지금까지 쌓인 증가분을 꺼내고 비운다. 초 단위로 내보내고 남은 밀리초는 다음으로 넘긴다. */
    fun drain(now: Long): Delta {
        tick(now)
        val d = Delta(
            distanceKm = distanceKm,
            drivingSec = drivingMs / 1000,
            nightSec = nightMs / 1000,
            turns = turns,
            turnsSignaled = turnsSignaled,
            brakePresses = brakePresses,
        )
        drivingMs %= 1000
        nightMs %= 1000
        distanceKm = 0.0
        turns = 0
        turnsSignaled = 0
        brakePresses = 0
        return d
    }

    companion object {
        const val TURN_ENTER = 90f
        const val TURN_EXIT = 30f
        const val SIGNAL_GRACE_MS = 5_000L
        const val MAX_STEP_KM = 50.0
    }
}
