package com.drivingcheckin.car.data

/**
 * 한 주 동안 차량 신호에서 집계한 값. 위치·경로는 담지 않는다.
 * weekStart 는 그 주 월요일(yyyy-MM-dd).
 */
data class WeekRecord(
    val weekStart: String,
    val distanceKm: Double = 0.0,
    val drivingSec: Long = 0,
    val nightSec: Long = 0,
    val turns: Int = 0,
    val turnsSignaled: Int = 0,
    val brakePresses: Int = 0,
    /** 시연용으로 불러온 주간 기록이면 true. 차량에서 직접 기록한 주는 false. */
    val demo: Boolean = false,
) {
    val hasDriving: Boolean get() = drivingSec > 0 || distanceKm > 0.0 || turns > 0

    /** 표본이 너무 적은 주는 그 지표를 비워 둔다(회전 몇 번만으로 비율을 말하지 않는다). */
    fun value(metric: Metric): Double? = when (metric) {
        Metric.SIGNAL_RATE -> if (turns >= MIN_TURNS) turnsSignaled.toDouble() / turns else null
        Metric.BRAKES_PER_KM -> if (distanceKm >= MIN_KM) brakePresses / distanceKm else null
        Metric.NIGHT_SHARE -> if (drivingSec >= MIN_DRIVING_SEC) nightSec.toDouble() / drivingSec else null
        Metric.DISTANCE -> if (hasDriving) distanceKm else null
    }

    companion object {
        const val MIN_TURNS = 5
        const val MIN_KM = 1.0
        const val MIN_DRIVING_SEC = 60L
    }

    operator fun plus(d: Delta) = copy(
        distanceKm = distanceKm + d.distanceKm,
        drivingSec = drivingSec + d.drivingSec,
        nightSec = nightSec + d.nightSec,
        turns = turns + d.turns,
        turnsSignaled = turnsSignaled + d.turnsSignaled,
        brakePresses = brakePresses + d.brakePresses,
    )
}

/** 집계기가 마지막으로 비운 뒤 쌓인 증가분. */
data class Delta(
    val distanceKm: Double = 0.0,
    val drivingSec: Long = 0,
    val nightSec: Long = 0,
    val turns: Int = 0,
    val turnsSignaled: Int = 0,
    val brakePresses: Int = 0,
) {
    val isEmpty: Boolean
        get() = distanceKm == 0.0 && drivingSec == 0L && nightSec == 0L &&
            turns == 0 && turnsSignaled == 0 && brakePresses == 0
}

/** 변화를 살피는 방향. */
enum class Watch { DECREASE, INCREASE, NONE }

enum class Metric(
    val key: String,
    val label: String,
    val watch: Watch,
) {
    SIGNAL_RATE("signal_rate", "깜빡이 사용률", Watch.DECREASE),
    BRAKES_PER_KM("brakes_per_km", "km당 브레이크 조작", Watch.INCREASE),
    NIGHT_SHARE("night_share", "밤 운전 비중", Watch.INCREASE),
    DISTANCE("distance_km", "주행 거리", Watch.NONE);

    fun format(v: Double?): String = when {
        v == null -> "기록 없음"
        this == SIGNAL_RATE || this == NIGHT_SHARE -> "${Math.round(v * 100)}%"
        this == BRAKES_PER_KM -> String.format("%.1f회", v)
        else -> "${Math.round(v)}km"
    }
}
