package com.unjeonanbu.car.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 공유 설정. 운전자 본인이 차 안에서 정한다. */
data class ShareSettings(
    val shared: Set<Metric> = setOf(Metric.SIGNAL_RATE, Metric.NIGHT_SHARE, Metric.DISTANCE),
    /** '이번 주는 보내지 않기'를 누른 주의 weekStart. */
    val holdWeek: String? = null,
)

/** 주간 기록과 공유 설정을 앱 내부 저장소에만 둔다. 위치·경로는 저장하지 않는다. */
class WeekStore(context: Context) {
    private val file = File(context.filesDir, "weeks.json")
    private val prefs = context.getSharedPreferences("share", Context.MODE_PRIVATE)

    private val _weeks = MutableStateFlow(load())
    val weeks: StateFlow<List<WeekRecord>> = _weeks

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<ShareSettings> = _settings

    private fun load(): List<WeekRecord> =
        runCatching { decode(file.readText()) }.getOrDefault(emptyList())

    private fun loadSettings(): ShareSettings {
        val keys = prefs.getStringSet("shared", null)
        val shared = keys?.let { ks -> Metric.entries.filter { it.key in ks }.toSet() }
        return ShareSettings(shared ?: ShareSettings().shared, prefs.getString("holdWeek", null))
    }

    @Synchronized
    private fun save(list: List<WeekRecord>) {
        val sorted = list.sortedBy { it.weekStart }
        file.writeText(encode(sorted))
        _weeks.value = sorted
    }

    @Synchronized
    fun add(delta: Delta, today: LocalDate = LocalDate.now()) {
        if (delta.isEmpty) return
        val key = weekStart(today)
        val list = _weeks.value.toMutableList()
        val i = list.indexOfFirst { it.weekStart == key }
        if (i >= 0) list[i] = list[i] + delta else list += WeekRecord(key) + delta
        save(list)
    }

    /** 시연용 12주 기록을 불러온다. 차량에서 직접 기록한 주는 그대로 둔다. */
    fun loadDemo(today: LocalDate = LocalDate.now()) {
        val real = _weeks.value.filter { !it.demo }
        val keep = real.map { it.weekStart }.toSet()
        save(real + DemoData.weeks(today).filter { it.weekStart !in keep })
    }

    fun clear() = save(emptyList())

    fun setShared(metric: Metric, on: Boolean) = update {
        it.copy(shared = if (on) it.shared + metric else it.shared - metric)
    }

    fun setHold(hold: Boolean, today: LocalDate = LocalDate.now()) = update {
        it.copy(holdWeek = if (hold) weekStart(today) else null)
    }

    private fun update(f: (ShareSettings) -> ShareSettings) {
        val s = f(_settings.value)
        prefs.edit()
            .putStringSet("shared", s.shared.map { it.key }.toSet())
            .putString("holdWeek", s.holdWeek)
            .apply()
        _settings.value = s
    }

    companion object {
        fun weekStart(day: LocalDate): String =
            day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

        fun encode(list: List<WeekRecord>): String = JSONArray().apply {
            list.forEach { w ->
                put(
                    JSONObject()
                        .put("weekStart", w.weekStart)
                        .put("distanceKm", w.distanceKm)
                        .put("drivingSec", w.drivingSec)
                        .put("nightSec", w.nightSec)
                        .put("turns", w.turns)
                        .put("turnsSignaled", w.turnsSignaled)
                        .put("brakePresses", w.brakePresses)
                        .put("demo", w.demo)
                )
            }
        }.toString()

        fun decode(text: String): List<WeekRecord> {
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WeekRecord(
                    weekStart = o.getString("weekStart"),
                    distanceKm = o.getDouble("distanceKm"),
                    drivingSec = o.getLong("drivingSec"),
                    nightSec = o.getLong("nightSec"),
                    turns = o.getInt("turns"),
                    turnsSignaled = o.getInt("turnsSignaled"),
                    brakePresses = o.getInt("brakePresses"),
                    demo = o.optBoolean("demo", false),
                )
            }
        }

        /**
         * 차 밖으로 나갈 수 있는 내용 전부. 운전자가 허용한 지표의 주간 값뿐이며,
         * 이번 주를 보류했으면 null 이다. 이 시제품은 실제 전송을 하지 않고 화면에 보여 주기만 한다.
         */
        fun sharePayload(weeks: List<WeekRecord>, shared: Set<Metric>, hold: Boolean): JSONObject? {
            if (hold) return null
            val sorted = weeks.sortedBy { it.weekStart }.takeLast(12)
            if (sorted.isEmpty()) return null
            val metrics = JSONObject()
            Metric.entries.filter { it in shared }.forEach { m ->
                val arr = JSONArray()
                sorted.forEach { w ->
                    val v = w.value(m)
                    arr.put(if (v == null) JSONObject.NULL else Math.round(v * 100) / 100.0)
                }
                metrics.put(m.key, arr)
            }
            return JSONObject()
                .put("weeks", JSONArray(sorted.map { it.weekStart }))
                .put("metrics", metrics)
        }
    }
}

/** 시연용 12주 시나리오: 8주는 평소와 같고, 최근 4주에 깜빡이 사용률이 낮아지고 밤 운전이 늘어난다. */
object DemoData {
    private val signalRate = listOf(.94, .92, .95, .93, .94, .93, .92, .94, .86, .78, .74, .70)
    private val nightShare = listOf(.10, .11, .09, .10, .11, .10, .09, .10, .13, .18, .22, .24)
    private val distance = listOf(118, 124, 109, 131, 120, 115, 127, 122, 119, 125, 121, 120)

    fun weeks(today: LocalDate): List<WeekRecord> {
        val thisWeek = LocalDate.parse(WeekStore.weekStart(today))
        return signalRate.indices.map { i ->
            val turns = 80
            val drivingSec = distance[i] * 120L // 평균 30km/h
            WeekRecord(
                weekStart = thisWeek.minusWeeks((signalRate.size - i).toLong()).toString(),
                distanceKm = distance[i].toDouble(),
                drivingSec = drivingSec,
                nightSec = Math.round(drivingSec * nightShare[i]),
                turns = turns,
                turnsSignaled = Math.round(turns * signalRate[i]).toInt(),
                brakePresses = distance[i] * 2,
                demo = true,
            )
        }
    }
}
