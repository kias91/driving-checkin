package com.drivingcheckin.car.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.drivingcheckin.car.data.WeekStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Signal(val label: String, val api: String) {
    GEAR("기어", "Gear.registerGearSelection"),
    TURN_SIGNAL("방향지시등", "TurnSignal.registerTurnSignalState"),
    BRAKE("브레이크 페달", "Brake.registerBrakePedalStatus"),
    LOW_BEAM("전조등(하향)", "Light.registerLowBeamLightState"),
    STEERING("스티어링 각도", "SteeringWheel.registerSteeringWheelAngle"),
    ODOMETER("누적 주행 거리", "Odometer.registerDistanceDriven"),
}

data class SignalState(val value: String? = null, val error: String? = null, val updates: Int = 0)

/**
 * Pleos Vehicle SDK 의 리스너 콜백을 받아 주간 기록으로 쌓는다.
 * 폴링하지 않고 상태가 바뀔 때만 처리하며, 화면이 없어도 서비스가 살아 있는 동안 동작한다.
 */
class Recorder(private val context: Context, private val store: WeekStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val aggregator = TripAggregator()

    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var flushJob: Job? = null

    private val _signals = MutableStateFlow(Signal.entries.associateWith { SignalState() })
    val signals: StateFlow<Map<Signal, SignalState>> = _signals

    /** 기어 기준 주행 여부. 아직 값을 받지 못했으면 null. */
    private val _driving = MutableStateFlow<Boolean?>(null)
    val driving: StateFlow<Boolean?> = _driving

    /** CarUxRestrictions 가 주행 중 화면 제한을 요구하는가. 확인할 수 없으면 null. */
    private val _uxRestricted = MutableStateFlow<Boolean?>(null)
    val uxRestricted: StateFlow<Boolean?> = _uxRestricted

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private fun now() = SystemClock.elapsedRealtime()

    private fun ok(signal: Signal, value: String) = _signals.update { m ->
        val s = m.getValue(signal)
        m + (signal to s.copy(value = value, error = null, updates = s.updates + 1))
    }

    private fun fail(signal: Signal, e: Throwable) {
        Log.w(TAG, "${signal.api} failed", e)
        _signals.update { m ->
            m + (signal to m.getValue(signal).copy(error = e.message ?: e.javaClass.simpleName))
        }
    }

    @Synchronized
    fun start() {
        if (_running.value) return
        _running.value = true
        try {
            val v = Vehicle(context).also { it.initialize() }
            vehicle = v
            register(v)
        } catch (t: Throwable) {
            Signal.entries.forEach { fail(it, t) }
        }
        watchUxRestrictions()
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    private fun register(v: Vehicle) {
        // 기어: 주행/주차 구분
        val onGear = { g: VehicleGear? ->
            val isDriving = g != null && g != VehicleGear.GEAR_PARK && g != VehicleGear.GEAR_UNKNOWN
            synchronized(lock) { aggregator.onDriving(isDriving, now()) }
            ok(Signal.GEAR, g?.name?.removePrefix("GEAR_") ?: "알 수 없음")
            val was = _driving.value
            _driving.value = isDriving
            if (was == true && !isDriving) flush() // 주차하면 바로 저장
        }
        v.getGear().getCurrentGearSelectionState({ onGear(it) }, { fail(Signal.GEAR, it) })
        v.getGear().registerGearSelection(object : GearSelectionListener {
            override fun onSelected(gear: VehicleGear?) = onGear(gear)
            override fun onFailed(e: Exception) = fail(Signal.GEAR, e)
        })

        // 방향지시등
        val onSignal = { s: TurnSignalValue? ->
            val on = s == TurnSignalValue.LEFT || s == TurnSignalValue.RIGHT
            synchronized(lock) { aggregator.onTurnSignal(on, now()) }
            ok(Signal.TURN_SIGNAL, when (s) {
                TurnSignalValue.LEFT -> "왼쪽"
                TurnSignalValue.RIGHT -> "오른쪽"
                else -> "꺼짐"
            })
        }
        v.getTurnSignal().getCurrentTurnSignalState({ onSignal(it) }, { fail(Signal.TURN_SIGNAL, it) })
        v.getTurnSignal().registerTurnSignalState(object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = onSignal(state)
            override fun onFailed(e: Exception) = fail(Signal.TURN_SIGNAL, e)
        })

        // 브레이크 페달
        val onBrake = { pressed: Boolean? ->
            synchronized(lock) { aggregator.onBrakePedal(pressed == true, now()) }
            ok(Signal.BRAKE, if (pressed == true) "밟음" else "뗌")
        }
        v.getBrake().getCurrentBrakePedalStatus({ onBrake(it) }, { fail(Signal.BRAKE, it) })
        v.getBrake().registerBrakePedalStatus(object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(pressed: Boolean?) = onBrake(pressed)
            override fun onFailed(e: Exception) = fail(Signal.BRAKE, e)
        })

        // 전조등(하향)
        val onBeam = { s: LightState? ->
            synchronized(lock) { aggregator.onLowBeam(s == LightState.ON, now()) }
            ok(Signal.LOW_BEAM, if (s == LightState.ON) "켜짐" else "꺼짐")
        }
        v.getLight().getCurrentLowBeamLightState({ onBeam(it) }, { fail(Signal.LOW_BEAM, it) })
        v.getLight().registerLowBeamLightState(object : LightStateListener {
            override fun onLightStateUpdated(state: LightState?) = onBeam(state)
            override fun onFailed(e: Exception) = fail(Signal.LOW_BEAM, e)
        })

        // 스티어링 각도: 회전 구간 판단
        val onAngle = { a: Float? ->
            if (a != null) {
                synchronized(lock) { aggregator.onSteeringAngle(a, now()) }
                ok(Signal.STEERING, "${Math.round(a)}°")
            }
        }
        v.getSteeringWheel().getCurrentSteeringWheelAngle({ onAngle(it) }, { fail(Signal.STEERING, it) })
        v.getSteeringWheel().registerSteeringWheelAngle(object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) = onAngle(angle)
            override fun onFailed(e: Exception) = fail(Signal.STEERING, e)
        })

        // 누적 주행 거리
        val onOdo = { km: Float? ->
            if (km != null) {
                synchronized(lock) { aggregator.onOdometerKm(km.toDouble(), now()) }
                ok(Signal.ODOMETER, String.format("%.1fkm", km))
            }
        }
        v.getOdometer().getCurrentDistanceDriven({ onOdo(it) }, { fail(Signal.ODOMETER, it) })
        v.getOdometer().registerDistanceDriven(object : DistanceDrivenListener {
            override fun onDistanceDrivenUpdated(distance: Float?) = onOdo(distance)
            override fun onFailed(e: Exception) = fail(Signal.ODOMETER, e)
        })
    }

    private fun watchUxRestrictions() {
        try {
            val c = Car.createCar(context) ?: return
            car = c
            val m = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager ?: return
            _uxRestricted.value = m.currentCarUxRestrictions?.isRequiresDistractionOptimization
            m.registerListener { r -> _uxRestricted.value = r.isRequiresDistractionOptimization }
        } catch (t: Throwable) {
            Log.w(TAG, "CarUxRestrictions unavailable", t)
        }
    }

    /** 쌓인 증가분을 이번 주 기록에 더한다. */
    fun flush() {
        val delta = synchronized(lock) { aggregator.drain(now()) }
        store.add(delta)
    }

    companion object {
        private const val TAG = "DrivingCheckin"
        private const val FLUSH_INTERVAL_MS = 15_000L
    }
}
