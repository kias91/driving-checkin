package com.drivingcheckin.car.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivingcheckin.car.App
import com.drivingcheckin.car.data.Insight
import com.drivingcheckin.car.data.Metric
import com.drivingcheckin.car.data.ShareSettings
import com.drivingcheckin.car.data.Trend
import com.drivingcheckin.car.data.WeekRecord
import com.drivingcheckin.car.data.WeekStore
import com.drivingcheckin.car.vehicle.Signal
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

object C {
    val bg = Color(0xFF0F1419)
    val surface = Color(0xFF1A222B)
    val raised = Color(0xFF243040)
    val text = Color(0xFFF2F5F8)
    val sub = Color(0xFFA6B5C4)
    val accent = Color(0xFF8CC8FF)
    val onAccent = Color(0xFF07243B)
    val ok = Color(0xFF7BD8A0)
    val warn = Color(0xFFFFC46B)
    val line = Color(0xFF33414F)
}

private val title = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = C.text)
private val heading = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = C.text)
private val body = TextStyle(fontSize = 20.sp, color = C.text, lineHeight = 30.sp)
private val small = TextStyle(fontSize = 16.sp, color = C.sub, lineHeight = 23.sp)

private enum class Tab(val label: String) {
    HOME("이번 주"), TREND("추세"), SHARE("가족에게 보내는 내용"), SIGNALS("신호 확인")
}

@Composable
fun AnbuApp(app: App) {
    val weeks by app.store.weeks.collectAsStateWithLifecycle()
    val settings by app.store.settings.collectAsStateWithLifecycle()
    val driving by app.recorder.driving.collectAsStateWithLifecycle()
    val restricted by app.recorder.uxRestricted.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(C.bg)) {
        // 주행 중에는 화면도 음성도 내지 않는다. 기록은 서비스가 계속한다.
        if (driving == true || restricted == true) {
            app.speaker.stop()
            DrivingScreen()
        } else {
            var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
            Row(Modifier.fillMaxSize()) {
                Rail(tab) { tab = it }
                Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 32.dp)) {
                    when (tab) {
                        Tab.HOME -> HomeScreen(app, weeks, settings)
                        Tab.TREND -> TrendScreen(weeks)
                        Tab.SHARE -> ShareScreen(app, weeks, settings)
                        Tab.SIGNALS -> SignalScreen(app, weeks)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrivingScreen() {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("운전에 집중하세요", style = title.copy(fontSize = 44.sp))
        Spacer(Modifier.height(16.dp))
        Text("주행 중에는 안내를 멈춥니다. 기록은 계속되고, 주차하면 알려 드릴게요.", style = body.copy(color = C.sub))
    }
}

@Composable
private fun Rail(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(280.dp).fillMaxHeight().background(C.surface).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("운전 안부", style = title.copy(fontSize = 28.sp), modifier = Modifier.padding(12.dp))
        Spacer(Modifier.height(8.dp))
        Tab.entries.forEach { t ->
            val selected = t == current
            Box(
                Modifier.fillMaxWidth().heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) C.raised else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    t.label,
                    style = body.copy(
                        color = if (selected) C.accent else C.text,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(C.surface).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun BigButton(
    label: String,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.heightIn(min = 76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) C.accent else C.raised)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = body.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (primary) C.onAccent else C.text,
            ),
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text,
        style = small.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

private fun isHeld(settings: ShareSettings) =
    settings.holdWeek == WeekStore.weekStart(LocalDate.now())

// ── 이번 주 ───────────────────────────────────────────────────────────────

@Composable
private fun HomeScreen(app: App, weeks: List<WeekRecord>, settings: ShareSettings) {
    val summary = remember(weeks) { Insight.driverSummary(weeks) }
    val trends = remember(weeks) { Insight.trends(weeks) }
    val speaking by app.speaker.speaking.collectAsStateWithLifecycle()
    val held = isHeld(settings)

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("이번 주 운전 안부", style = title)
            if (weeks.any { it.demo }) Chip("시연용 기록 포함", C.sub)
        }
        Card(Modifier.fillMaxWidth()) {
            Text("가족에게 보내기 전에 먼저 확인하세요", style = small)
            Text(summary, style = body.copy(fontSize = 26.sp, lineHeight = 38.sp))
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton(if (speaking) "그만 듣기" else "소리로 듣기", primary = true) {
                    if (speaking) app.speaker.stop() else app.speaker.speak(summary)
                }
                BigButton(if (held) "보류함 · 다시 보내기" else "이번 주는 보내지 않기") {
                    app.store.setHold(!held)
                }
            }
            if (held) Text("이번 주 기록은 가족에게 보내지 않습니다.", style = small.copy(color = C.warn))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(Metric.SIGNAL_RATE, Metric.NIGHT_SHARE, Metric.BRAKES_PER_KM).forEach { m ->
                val t = trends.first { it.metric == m }
                Card(Modifier.weight(1f)) {
                    Text(m.label, style = small)
                    Text(m.format(t.latest), style = title.copy(fontSize = 44.sp))
                    Text(if (t.baseline != null) "평소 ${m.format(t.baseline)}" else " ", style = small)
                    StatusChip(t)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(t: Trend) = when {
    t.baseline == null -> Chip("평소 운전을 알아 가는 중", C.sub)
    t.changed -> Chip("평소와 달라졌어요", C.warn)
    else -> Chip("평소와 비슷해요", C.ok)
}

// ── 추세 ──────────────────────────────────────────────────────────────────

@Composable
private fun TrendScreen(weeks: List<WeekRecord>) {
    val sorted = remember(weeks) { weeks.sortedBy { it.weekStart }.takeLast(12) }
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("최근 석 달의 추세", style = title)
        Text("다른 사람과 비교하지 않고, 처음 4주의 본인 기록(점선)과만 비교합니다.", style = small)
        if (sorted.isEmpty()) {
            Card(Modifier.fillMaxWidth()) { Text("아직 운전 기록이 없어요.", style = body) }
        }
        listOf(Metric.SIGNAL_RATE, Metric.NIGHT_SHARE, Metric.BRAKES_PER_KM, Metric.DISTANCE).forEach { m ->
            val t = Insight.trend(weeks, m)
            val values = sorted.map { it.value(m) }
            if (values.any { it != null }) {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(m.label, style = heading)
                        if (m != Metric.DISTANCE) StatusChip(t)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (t.baseline != null) "평소 ${m.format(t.baseline)} → 최근 ${m.format(t.latest)}"
                            else "최근 ${m.format(t.latest)}",
                            style = body,
                        )
                    }
                    TrendChart(
                        values = values,
                        baseline = t.baseline,
                        highlight = t.changed,
                        labels = sorted.map { it.weekStart.substring(5).replace('-', '/') },
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                    )
                }
            }
        }
    }
}

// ── 가족에게 보내는 내용 ──────────────────────────────────────────────────

@Composable
private fun ShareScreen(app: App, weeks: List<WeekRecord>, settings: ShareSettings) {
    val held = isHeld(settings)
    val trends = remember(weeks) { Insight.trends(weeks) }
    val payload = remember(weeks, settings) { WeekStore.sharePayload(weeks, settings.shared, held) }

    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("가족에게 보내는 내용", style = title)
            Card(Modifier.fillMaxWidth()) {
                Text("보낼 항목은 직접 고르세요", style = heading)
                Metric.entries.forEach { m ->
                    ToggleRow(m.label, m in settings.shared) { app.store.setShared(m, it) }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.line))
                ToggleRow("이번 주는 보내지 않기", held) { app.store.setHold(it) }
            }
            Card(Modifier.fillMaxWidth()) {
                Text("차 밖으로 나가는 내용 전부", style = heading)
                Text(
                    "위치·경로·행선지와 주행 중 원시 기록은 포함하지 않습니다. " +
                        "이 시제품은 실제로 전송하지 않고 보낼 내용만 보여 줍니다.",
                    style = small,
                )
                Text(
                    payload?.toString(2) ?: "보내는 내용 없음",
                    style = small.copy(fontFamily = FontFamily.Monospace, color = C.text),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(C.bg).padding(16.dp),
                )
            }
        }
        // 자녀의 휴대폰에 보일 카드 미리보기
        Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("자녀에게 보이는 모습", style = small)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFFF7F4EE))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val ink = Color(0xFF1C1B19)
                val mute = Color(0xFF6B6860)
                Text("부모님의 운전 안부", style = heading.copy(color = ink))
                if (held || payload == null) {
                    Text("이번 주 소식은 쉬어 가요.", style = body.copy(color = mute))
                } else {
                    val shown = trends.filter { it.metric in settings.shared && it.latest != null }
                    val changed = shown.filter { it.changed }
                    Text(
                        if (changed.isEmpty()) "이번 주도 평소와 같아요." else "평소와 달라진 점이 있어요.",
                        style = body.copy(color = ink, fontWeight = FontWeight.SemiBold),
                    )
                    shown.forEach { t ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(t.metric.label, style = small.copy(color = mute), modifier = Modifier.weight(1f))
                            Text(
                                if (t.changed) "${t.metric.format(t.baseline)} → ${t.metric.format(t.latest)}"
                                else t.metric.format(t.latest),
                                style = body.copy(
                                    color = if (t.changed) Color(0xFF9A5B00) else ink,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                    Insight.conversationGuide(trends, settings.shared)?.let { guide ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEDE6D8)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("이렇게 말을 건네 보세요", style = small.copy(color = mute))
                            Text(guide, style = small.copy(color = ink, fontSize = 17.sp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = body, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = C.accent,
                checkedThumbColor = C.onAccent,
                uncheckedTrackColor = C.raised,
                uncheckedThumbColor = C.sub,
                uncheckedBorderColor = C.line,
            ),
        )
    }
}

// ── 신호 확인 ─────────────────────────────────────────────────────────────

@Composable
private fun SignalScreen(app: App, weeks: List<WeekRecord>) {
    val signals by app.recorder.signals.collectAsStateWithLifecycle()
    val running by app.recorder.running.collectAsStateWithLifecycle()
    val restricted by app.recorder.uxRestricted.collectAsStateWithLifecycle()
    val engine by app.speaker.engine.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val autoStartedAt = remember {
        context.getSharedPreferences("recorder", Context.MODE_PRIVATE).getLong("autoStartedAt", 0L)
    }
    val thisWeek = weeks.firstOrNull { it.weekStart == WeekStore.weekStart(LocalDate.now()) }

    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("신호 확인", style = title, modifier = Modifier.weight(1f))
            BigButton("시연용 12주 기록 불러오기") { app.store.loadDemo() }
            BigButton("기록 모두 지우기") { app.store.clear() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Card(Modifier.weight(1f)) {
                Text("Pleos Vehicle SDK에서 받는 신호", style = heading)
                Signal.entries.forEach { s ->
                    val st = signals.getValue(s)
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.label, style = body)
                            Text(s.api.substringAfter('.'), style = small.copy(fontSize = 13.sp), maxLines = 1)
                        }
                        Text(
                            st.error?.let { "오류: $it" } ?: st.value ?: "수신 대기",
                            style = body.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (st.error != null) C.warn else C.text,
                            ),
                        )
                        Text("${st.updates}회", style = small, modifier = Modifier.width(70.dp).padding(start = 12.dp))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Card(Modifier.fillMaxWidth()) {
                    Text("기록 상태", style = heading)
                    Fact("기록 서비스", if (running) "실행 중" else "멈춤")
                    Fact(
                        "시동 시 자동 시작",
                        if (autoStartedAt > 0) SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(autoStartedAt))
                        else "기록 없음",
                    )
                    Fact("주행 중 화면 제한", when (restricted) { true -> "제한 중"; false -> "제한 없음"; null -> "확인 불가" })
                    Fact("음성 엔진", engine)
                }
                Card(Modifier.fillMaxWidth()) {
                    Text("이번 주에 차에서 기록한 값", style = heading)
                    if (thisWeek == null) {
                        Text("아직 없음", style = body)
                    } else {
                        Fact("주행 시간", "${thisWeek.drivingSec}초 (밤 ${thisWeek.nightSec}초)")
                        Fact("주행 거리", String.format("%.1fkm", thisWeek.distanceKm))
                        Fact("회전", "${thisWeek.turns}회 중 깜빡이 ${thisWeek.turnsSignaled}회")
                        Fact("브레이크", "${thisWeek.brakePresses}회")
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, style = small, modifier = Modifier.width(150.dp))
        Text(value, style = body.copy(fontSize = 18.sp, lineHeight = 26.sp), modifier = Modifier.weight(1f))
    }
}
