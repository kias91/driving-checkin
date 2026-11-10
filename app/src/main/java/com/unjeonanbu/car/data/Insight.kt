package com.unjeonanbu.car.data

import kotlin.math.abs

/** 한 지표의 본인 기준선 대비 추세. */
data class Trend(
    val metric: Metric,
    /** 기준선: 기록이 있는 첫 4주의 평균. 4주가 안 되면 null. */
    val baseline: Double?,
    val latest: Double?,
    /** 기준선 이후 최근 3주가 연속으로 살피는 방향으로 벗어났는가. */
    val changed: Boolean,
)

object Insight {
    const val BASELINE_WEEKS = 4
    const val CONSECUTIVE_WEEKS = 3
    /** 기준선 대비 이 비율 이상 벗어나야 '달라짐'으로 본다. 시범 운영에서 보정할 값. */
    const val THRESHOLD = 0.15

    fun trend(weeks: List<WeekRecord>, metric: Metric): Trend {
        val values = weeks.sortedBy { it.weekStart }.mapNotNull { it.value(metric) }
        val latest = values.lastOrNull()
        if (values.size < BASELINE_WEEKS) return Trend(metric, null, latest, false)
        val baseline = values.take(BASELINE_WEEKS).average()
        val after = values.drop(BASELINE_WEEKS)
        val changed = metric.watch != Watch.NONE &&
            after.size >= CONSECUTIVE_WEEKS &&
            after.takeLast(CONSECUTIVE_WEEKS).all { deviates(metric, baseline, it) }
        return Trend(metric, baseline, latest, changed)
    }

    private fun deviates(metric: Metric, baseline: Double, v: Double): Boolean {
        if (baseline == 0.0) return metric.watch == Watch.INCREASE && v > 0.0
        val rel = (v - baseline) / abs(baseline)
        return when (metric.watch) {
            Watch.DECREASE -> rel <= -THRESHOLD
            Watch.INCREASE -> rel >= THRESHOLD
            Watch.NONE -> false
        }
    }

    fun trends(weeks: List<WeekRecord>): List<Trend> = Metric.entries.map { trend(weeks, it) }

    /** 운전자 본인이 주차 후 먼저 듣는 요약. 화면과 음성에 같은 문장을 쓴다. */
    fun driverSummary(weeks: List<WeekRecord>): String {
        val sorted = weeks.sortedBy { it.weekStart }
        val week = sorted.lastOrNull { it.hasDriving }
            ?: return "아직 운전 기록이 없어요. 평소처럼 운전하시면 기록이 쌓입니다."
        val parts = mutableListOf<String>()
        parts += "이번 주는 ${Metric.DISTANCE.format(week.distanceKm)} 달리셨어요."

        val changed = trends(sorted).filter { it.changed }
        if (sorted.count { it.hasDriving } < BASELINE_WEEKS) {
            parts += "지금은 평소 운전을 알아 가는 기간입니다."
        } else if (changed.isEmpty()) {
            parts += "깜빡이와 브레이크 모두 평소와 비슷하셨어요."
        } else {
            changed.forEach { t ->
                parts += when (t.metric) {
                    Metric.SIGNAL_RATE ->
                        "회전할 때 깜빡이를 켠 비율이 평소 ${t.metric.format(t.baseline)}에서 " +
                            "${t.metric.format(t.latest)}로 낮아졌어요."
                    Metric.NIGHT_SHARE ->
                        "밤에 운전한 시간이 평소 ${t.metric.format(t.baseline)}에서 " +
                            "${t.metric.format(t.latest)}로 늘었어요."
                    Metric.BRAKES_PER_KM ->
                        "브레이크를 밟는 횟수가 평소보다 늘었어요."
                    Metric.DISTANCE -> ""
                }
            }
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    /** 자녀 카드에 함께 보여 줄, 다그치지 않는 다음 행동 제안. */
    fun conversationGuide(trends: List<Trend>, shared: Set<Metric>): String? {
        val changed = trends.filter { it.changed && it.metric in shared }.map { it.metric }
        return when {
            Metric.NIGHT_SHARE in changed ->
                "밤길 운전이 늘었어요. \"저녁 모임은 제가 모셔다 드릴게요\"처럼 먼저 제안해 보세요."
            Metric.SIGNAL_RATE in changed ->
                "면허 이야기보다 \"요즘 운전하실 때 피곤하지 않으세요?\"로 안부를 먼저 물어보세요."
            Metric.BRAKES_PER_KM in changed ->
                "자주 다니시는 길이 막히는지, 운전이 부담스럽지 않은지 물어보세요."
            else -> null
        }
    }
}
