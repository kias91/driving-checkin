package com.unjeonanbu.car

import com.unjeonanbu.car.data.DemoData
import com.unjeonanbu.car.data.Insight
import com.unjeonanbu.car.data.Metric
import com.unjeonanbu.car.data.WeekRecord
import com.unjeonanbu.car.data.WeekStore
import com.unjeonanbu.car.vehicle.TripAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {

    @Test fun `주차 중 신호는 세지 않는다`() {
        val a = TripAggregator()
        a.onDriving(false, 0)
        a.onBrakePedal(true, 1_000)
        a.onSteeringAngle(180f, 2_000)
        a.onOdometerKm(10.0, 3_000)
        a.onOdometerKm(11.0, 4_000)
        val d = a.drain(5_000)
        assertTrue(d.isEmpty)
    }

    @Test fun `깜빡이를 켠 회전과 켜지 않은 회전을 구분한다`() {
        val a = TripAggregator()
        a.onDriving(true, 0)
        // 깜빡이를 켜고 회전
        a.onTurnSignal(true, 1_000)
        a.onSteeringAngle(120f, 2_000)
        a.onSteeringAngle(0f, 4_000)
        a.onTurnSignal(false, 4_500)
        // 한참 뒤 깜빡이 없이 회전
        a.onSteeringAngle(-150f, 20_000)
        a.onSteeringAngle(-140f, 21_000) // 같은 회전 안의 추가 값은 다시 세지 않는다
        a.onSteeringAngle(0f, 23_000)
        val d = a.drain(30_000)
        assertEquals(2, d.turns)
        assertEquals(1, d.turnsSignaled)
    }

    @Test fun `깜빡이를 끈 직후의 회전은 켠 것으로 본다`() {
        val a = TripAggregator()
        a.onDriving(true, 0)
        a.onTurnSignal(true, 1_000)
        a.onTurnSignal(false, 2_000)
        a.onSteeringAngle(100f, 2_000 + TripAggregator.SIGNAL_GRACE_MS)
        assertEquals(1, a.drain(10_000).turnsSignaled)
    }

    @Test fun `브레이크는 밟을 때마다 한 번, 밤 운전은 전조등이 켜진 주행 시간`() {
        val a = TripAggregator()
        a.onDriving(true, 0)
        a.onOdometerKm(100.0, 0)
        a.onBrakePedal(true, 1_000)
        a.onBrakePedal(true, 1_500) // 계속 밟고 있는 상태
        a.onBrakePedal(false, 2_000)
        a.onBrakePedal(true, 3_000)
        a.onLowBeam(true, 60_000)
        a.onOdometerKm(102.5, 90_000)
        a.onDriving(false, 120_000)
        a.onLowBeam(false, 130_000)
        val d = a.drain(200_000)
        assertEquals(2, d.brakePresses)
        assertEquals(120, d.drivingSec)
        assertEquals(60, d.nightSec)
        assertEquals(2.5, d.distanceKm, 1e-9)
    }

    @Test fun `drain 은 비우고, 남은 밀리초는 다음으로 넘긴다`() {
        val a = TripAggregator()
        a.onDriving(true, 0)
        assertEquals(1, a.drain(1_600).drivingSec)
        assertEquals(1, a.drain(2_000).drivingSec)
        assertEquals(0, a.drain(2_100).turns)
    }

    @Test fun `기준선이 4주 미만이면 변화를 판단하지 않는다`() {
        val weeks = (0 until 3).map { week(it, signaled = 9) }
        val t = Insight.trend(weeks, Metric.SIGNAL_RATE)
        assertNull(t.baseline)
        assertFalse(t.changed)
    }

    @Test fun `최근 3주가 연속으로 벗어나야 변화로 표시한다`() {
        val stable = (0 until 8).map { week(it, signaled = 9) }
        val twoBad = stable + week(8, signaled = 6) + week(9, signaled = 6)
        assertFalse(Insight.trend(twoBad, Metric.SIGNAL_RATE).changed)
        val threeBad = twoBad + week(10, signaled = 6)
        val t = Insight.trend(threeBad, Metric.SIGNAL_RATE)
        assertTrue(t.changed)
        assertEquals(0.9, t.baseline!!, 1e-9)
        // 한 주 회복하면 다시 변화 없음
        assertFalse(Insight.trend(threeBad + week(11, signaled = 9), Metric.SIGNAL_RATE).changed)
    }

    @Test fun `좋아지는 방향의 변화는 표시하지 않는다`() {
        val weeks = (0 until 4).map { week(it, signaled = 6) } + (4 until 8).map { week(it, signaled = 10) }
        assertFalse(Insight.trend(weeks, Metric.SIGNAL_RATE).changed)
    }

    @Test fun `시연 데이터는 깜빡이와 밤 운전 변화를 보여 준다`() {
        val weeks = DemoData.weeks(LocalDate.of(2026, 11, 10))
        assertEquals(12, weeks.size)
        assertTrue(weeks.all { it.demo })
        assertEquals("2026-11-02", weeks.last().weekStart) // 이번 주(11-09)는 비워 둔다
        val changed = Insight.trends(weeks).filter { it.changed }.map { it.metric }.toSet()
        assertEquals(setOf(Metric.SIGNAL_RATE, Metric.NIGHT_SHARE), changed)
        assertTrue(Insight.driverSummary(weeks).contains("깜빡이"))
    }

    @Test fun `주간 기록은 JSON 으로 저장했다 그대로 읽는다`() {
        val weeks = DemoData.weeks(LocalDate.of(2026, 11, 10))
        assertEquals(weeks, WeekStore.decode(WeekStore.encode(weeks)))
    }

    @Test fun `가족에게 보내는 내용에는 허용한 지표만 들어간다`() {
        val weeks = DemoData.weeks(LocalDate.of(2026, 11, 10))
        val payload = WeekStore.sharePayload(weeks, setOf(Metric.SIGNAL_RATE), hold = false)!!
        val items = payload.getJSONObject("metrics")
        assertTrue(items.has("signal_rate"))
        assertFalse(items.has("night_share"))
        assertFalse(items.has("distance_km"))
        assertNull(WeekStore.sharePayload(weeks, Metric.entries.toSet(), hold = true))
    }

    private fun week(i: Int, signaled: Int) = WeekRecord(
        weekStart = LocalDate.of(2026, 1, 5).plusWeeks(i.toLong()).toString(),
        distanceKm = 100.0, drivingSec = 10_000, nightSec = 1_000,
        turns = 10, turnsSignaled = signaled, brakePresses = 200,
    )
}
