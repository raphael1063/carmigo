package com.lrlr.carmigo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lrlr.carmigo.R
import com.lrlr.carmigo.Sending
import com.lrlr.carmigo.core.AppState
import com.lrlr.carmigo.core.Fmt
import com.lrlr.carmigo.core.LastDrive
import com.lrlr.carmigo.core.Moment
import com.lrlr.carmigo.core.Rules
import com.lrlr.carmigo.core.Wiped
import java.time.LocalDate
import kotlin.math.floor
import java.time.YearMonth

private val Gutter = 40.dp

/** 가로 화면(앱 하나)인지, 세로 화면(앱 둘로 나눔)인지. Pleos Connect 앱은 둘 다 지원해야 한다. */
private fun wide(width: Dp) = width >= 1100.dp

// ---------- 첫 입력 ----------

/**
 * 처음 한 번의 입력. 고칠 때도 같은 화면을 쓴다(initial과 onCancel을 준다).
 * maxKm은 넣을 수 있는 가장 큰 주행거리(지금 또는 설치한 날의 계기판)다.
 */
@Composable
fun OnboardingScreen(
    odoKm: Double?,
    today: LocalDate,
    onStart: (receivedDay: Long, receivedOdoKm: Double, carName: String?) -> Unit,
    initialDate: LocalDate = today,
    initialKm: Double? = null,
    maxDate: LocalDate = today,
    maxKm: Double? = odoKm,
    onCancel: (() -> Unit)? = null,
    initialName: String? = null,
    /** 제목을 길게 누르면 시연 모드 창을 연다(일반 사용자가 우연히 켜지 않도록 숨겨 둔다) */
    onSecret: () -> Unit = {},
) {
    val c = Ivi.colors
    val editing = onCancel != null
    var nameText by rememberSaveable { mutableStateOf(initialName ?: "") }
    var year by rememberSaveable { mutableStateOf(initialDate.year) }
    var month by rememberSaveable { mutableStateOf(initialDate.monthValue) }
    var day by rememberSaveable { mutableStateOf(initialDate.dayOfMonth) }
    var kmText by rememberSaveable { mutableStateOf(initialKm?.let { floor(it).toLong().toString() } ?: "") }

    val picked = LocalDate.of(year, month, day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth()))
    val date = if (picked.isAfter(maxDate)) maxDate else picked
    val typed = kmText.toDoubleOrNull()
    val km = typed?.coerceIn(0.0, maxKm ?: 9_999_999.0)
    fun setDate(d: LocalDate) {
        val v = if (d.isAfter(maxDate)) maxDate else if (d.year < 1990) LocalDate.of(1990, 1, 1) else d
        year = v.year; month = v.monthValue; day = v.dayOfMonth
    }

    val scroll = rememberScrollState()
    val focus = LocalFocusManager.current
    var typing by remember { mutableStateOf(false) }
    // 자판이 올라와 화면이 줄어들면 아래의 시작 버튼이 보이도록 끝까지 내린다
    LaunchedEffect(typing) {
        if (typing) snapshotFlow { scroll.maxValue }.collect { scroll.scrollTo(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 900.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = Gutter, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 880.dp).fillMaxWidth()) {
                T(if (editing) "처음 등록한 정보 수정" else "이 차와 언제부터 함께하셨나요", Type.titleLarge, Modifier.secret(onSecret))
                Spacer(Modifier.height(12.dp))
                T(
                    if (editing) "받은 날과 그때의 주행거리, 차 이름을 고칩니다. 받은 날이나 주행거리를 바꾸면 그 값으로 계산한 함께한 날, 함께 달린 거리 카드는 지우고 다시 셉니다."
                    else "받은 날과 그때의 주행거리를 알려주시면 그날부터의 시간을 셉니다. 처음 한 번만 묻습니다.",
                    Type.bodySmall, color = c.textSecondary,
                )
                Spacer(Modifier.height(40.dp))

                T("차를 받은 날", Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(12.dp))
                val steppers: List<@Composable (Modifier) -> Unit> = listOf(
                    { m -> Stepper("${date.year}년", m, { setDate(date.minusYears(1)) }, { setDate(date.plusYears(1)) }) },
                    { m -> Stepper("${date.monthValue}월", m, { setDate(date.minusMonths(1)) }, { setDate(date.plusMonths(1)) }) },
                    { m -> Stepper("${date.dayOfMonth}일", m, { setDate(date.minusDays(1)) }, { setDate(date.plusDays(1)) }) },
                )
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { steppers.forEach { it(Modifier.fillMaxWidth()) } }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        steppers[0](Modifier.weight(1.2f)); steppers[1](Modifier.weight(1f)); steppers[2](Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(32.dp))

                T("차를 받았을 때 계기판 거리", Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(12.dp))
                val field: @Composable (Modifier) -> Unit = { m ->
                    Row(
                        m.height(100.dp).clip(RoundedCornerShape(24.dp)).background(c.backgroundSecondary).padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (kmText.isEmpty()) T("숫자를 넣어 주세요", Type.bodyMedium, color = c.textTertiary, maxLines = 1)
                            BasicTextField(
                                value = kmText,
                                onValueChange = { v -> kmText = v.filter { it.isDigit() }.take(7) },
                                singleLine = true,
                                textStyle = Type.titleMedium.copy(color = c.textPrimary),
                                cursorBrush = SolidColor(c.positive),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                modifier = Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused },
                            )
                        }
                        T("km", Type.bodyMedium, color = c.textTertiary)
                    }
                }
                if (compact) {
                    field(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    IviButton("새 차였어요", { kmText = "0" }, Modifier.fillMaxWidth(), size = ButtonSize.MEDIUM)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        field(Modifier.weight(1f))
                        IviButton("새 차였어요", { kmText = "0" }, size = ButtonSize.LARGE)
                    }
                }
                Spacer(Modifier.height(32.dp))

                T("이 차를 부르는 이름 (없으면 비워 두세요)", Type.labelLarge, color = c.textTertiary)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(24.dp)).background(c.backgroundSecondary).padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (nameText.isEmpty()) T("예: 흰둥이", Type.bodyMedium, color = c.textTertiary, maxLines = 1)
                        BasicTextField(
                            value = nameText,
                            onValueChange = { v -> nameText = v.replace("\n", "").take(Fmt.NAME_MAX) },
                            singleLine = true,
                            textStyle = Type.titleMedium.copy(color = c.textPrimary),
                            cursorBrush = SolidColor(c.positive),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { typing = it.isFocused },
                        )
                    }
                }
                Spacer(Modifier.height(32.dp))

                Column(Modifier.fillMaxWidth().heightIn(min = 108.dp)) {
                    if (odoKm != null) {
                        T("지금 계기판은 ${Fmt.km(odoKm)} km입니다 (차에서 읽음).", Type.bodySmall, color = c.textSecondary)
                    } else {
                        T("지금 계기판 숫자를 아직 읽지 못했습니다.", Type.bodySmall, color = c.textSecondary)
                    }
                    if (km != null) {
                        if (typed != null && typed > km) {
                            T("${if (editing) "앱을 설치한 날의" else "지금"} 계기판보다 클 수 없어 ${Fmt.km(km)} km로 넣습니다.", Type.bodySmall, color = c.textPrimary)
                        }
                        val days = today.toEpochDay() - date.toEpochDay()
                        val together = odoKm?.let { " 함께 달린 거리 ${Fmt.km((it - km).coerceAtLeast(0.0))} km." } ?: ""
                        T("함께한 지 ${Fmt.n(days)}일.$together", Type.bodySmall, color = c.textPrimary)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (onCancel != null) IviButton("취소", onCancel, Modifier.weight(1f), size = ButtonSize.LARGE)
                    IviButton(
                        if (editing) "수정 내용 저장" else "시작", { km?.let { onStart(date.toEpochDay(), it, Fmt.cleanName(nameText)) } },
                        Modifier.weight(1f), filled = true, size = ButtonSize.LARGE, enabled = km != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, modifier: Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Ivi.colors
    Row(
        modifier.height(100.dp).clip(RoundedCornerShape(24.dp)).background(c.backgroundSecondary).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", "$label 줄이기", onMinus)
        T(label, Type.titleSmall, Modifier.weight(1f), align = TextAlign.Center, maxLines = 1)
        StepButton("+", "$label 늘리기", onPlus)
    }
}

@Composable
private fun StepButton(sign: String, description: String, onClick: () -> Unit) {
    val c = Ivi.colors
    Box(
        Modifier.size(80.dp).clip(CircleShape).background(c.buttonBasicEnabled).semantics { contentDescription = description }.pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        T(sign, Type.titleMedium, color = c.textSecondary)
    }
}

/** 처음 여는 화면: 로고와 이름, 한 줄, 시작하기. 이름이나 로고를 길게 누르면 시연 모드 창을 연다. */
@Composable
fun WelcomeScreen(onStart: () -> Unit, onSecret: () -> Unit = {}) {
    CenterBox(Modifier.padding(Gutter)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(144.dp, Modifier.appear(0, scaleFrom = 0.92f).secret(onSecret))
            Spacer(Modifier.height(36.dp))
            T(stringResource(R.string.app_name), Type.headlineLarge, Modifier.appear(1).secret(onSecret))
            Spacer(Modifier.height(12.dp))
            T("내가 이 차를 만난 날부터", Type.bodyLarge, Modifier.appear(2), color = Ivi.colors.textSecondary)
            Spacer(Modifier.height(56.dp))
            IviButton("시작하기", onStart, Modifier.widthIn(min = 360.dp).appear(3), filled = true, size = ButtonSize.LARGE)
        }
    }
}

/** 첫 입력 전인데 이미 달리는 중일 때. 주행 중에는 설정 화면을 열지 않는다. */
@Composable
fun ParkFirstScreen() {
    CenterBox(Modifier.padding(Gutter)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(96.dp)
            Spacer(Modifier.height(24.dp))
            T(stringResource(R.string.app_name), Type.titleLarge)
            Spacer(Modifier.height(16.dp))
            T("주차한 뒤에 시작할 수 있습니다", Type.bodyLarge, color = Ivi.colors.textSecondary)
        }
    }
}

// ---------- 위쪽 줄 ----------

@Composable
fun TopBar(
    state: AppState,
    rules: Rules,
    now: Long,
    tab: Int?,
    onTab: (Int) -> Unit,
    lastEnabled: Boolean,
    onLast: () -> Unit,
    compact: Boolean,
    /** 주행 중에는 버튼 하나만 남긴다 */
    minimal: Boolean = false,
    /** 마지막 주행을 마친 뒤(작별 화면)에는 버튼을 두지 않는다 */
    showLast: Boolean = true,
    /** 시연 모드 창 열기(앱 이름이나 날수를 길게 누르거나, 시연 모드 표시를 누른다) */
    onDemo: () -> Unit = {},
) {
    val setup = state.setup ?: return
    val c = Ivi.colors
    val lastLabel = if (state.last == LastDrive.ARMED) "마지막 주행 취소" else "마지막 주행"
    @Composable
    fun last() {
        if (showLast) Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.last == LastDrive.ARMED) T("다음 주행을 마지막으로 기록합니다", Type.labelSmall, color = c.textSecondary)
            IviButton(lastLabel, onLast, size = ButtonSize.SMALL, enabled = lastEnabled, quiet = state.last != LastDrive.ARMED)
        }
    }
    @Composable
    fun demoTag() { if (state.demo && !minimal) IviButton("시연 모드", onDemo, size = ButtonSize.SMALL, quiet = true) }

    if (compact) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 날수와 거리는 탭 맨 위에서 한 번만 보여 준다. 여기는 앱 이름만.
                if (!minimal) {
                    LogoMark(32.dp)
                    Spacer(Modifier.width(12.dp))
                }
                T(if (minimal) "" else stringResource(R.string.app_name), Type.labelLarge, Modifier.weight(1f).secret(onDemo), color = c.textSecondary)
                demoTag()
                if (state.demo && !minimal) Spacer(Modifier.width(12.dp))
                last()
            }
            if (tab != null) IviTabs(listOf("순간", "숫자"), tab, { if (it == 0) state.unseen else 0 }, onTab)
        }
    } else {
        Box(Modifier.fillMaxWidth().height(112.dp).padding(horizontal = Gutter)) {
            if (!minimal) Row(Modifier.align(Alignment.CenterStart).secret(onDemo), verticalAlignment = Alignment.CenterVertically) {
                LogoMark(44.dp)
                Spacer(Modifier.width(16.dp))
                T(stringResource(R.string.app_name), Type.titleSmall)
            }
            if (tab != null) {
                IviTabs(listOf("순간", "숫자"), tab, { if (it == 0) state.unseen else 0 }, onTab, Modifier.align(Alignment.Center))
            }
            Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                demoTag()
                last()
            }
        }
    }
}

// ---------- 순간 ----------

@Composable
fun MomentsTab(state: AppState, rules: Rules, now: Long, onOpen: (Moment) -> Unit, onExport: () -> Unit) {
    val c = Ivi.colors
    Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                // 가장 먼저 보이는 것은 함께한 날과 함께 달린 거리
                state.setup?.let { setup ->
                    val km = rules.kmTogether(setup, state.lastOdoKm)?.let { " · 함께 ${Fmt.km(it)} km" } ?: ""
                    T("함께한 지 ${Fmt.n(rules.daysTogether(setup, now))}일$km", Type.titleMedium)
                    Spacer(Modifier.height(4.dp))
                }
                T("함께한 순간 ${Fmt.n(state.moments.size)}장", Type.labelLarge, color = c.textTertiary)
            }
            IviButton("기록 받아 가기", onExport, size = ButtonSize.SMALL)
        }
        if (state.moments.isEmpty()) {
            CenterBox(Modifier.padding(bottom = 80.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    T("아직 기록된 순간이 없습니다", Type.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    T("함께한 날과 달린 거리에서 기념할 순간을 자동으로 남깁니다.", Type.bodySmall, color = c.textSecondary, align = TextAlign.Center)
                }
            }
        } else {
            LazyVerticalGrid(
                // 카드 칸을 넓혀 "옆자리에 누군가 탄 200번째 날" 같은 제목도 한 줄에 들어가게 한다(가로 화면 3열).
                columns = GridCells.Adaptive(480.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(state.moments, key = { it.id }) { m -> MomentCard(m, rules) { onOpen(m) } }
            }
        }
    }
}

@Composable
private fun MomentCard(m: Moment, rules: Rules, onClick: () -> Unit) {
    val c = Ivi.colors
    Column(
        Modifier.fillMaxWidth().heightIn(min = 240.dp).clip(RoundedCornerShape(28.dp)).background(c.backgroundSecondary)
            .then(if (!m.seen) Modifier.border(2.dp, c.accent.copy(alpha = 0.55f), RoundedCornerShape(28.dp)) else Modifier)
            .pressable(onClick = onClick).padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(skyBrush(if (m.hasTime) rules.minuteOfDay(m.at) else null, m.weather?.sky)).border(2.dp, Ivi.colors.textTertiary, CircleShape))
            Spacer(Modifier.width(10.dp))
            T(m.kind.label, Type.labelSmall, Modifier.weight(1f), color = c.textTertiary, maxLines = 1)
            if (!m.seen) T("새 순간", Type.labelSmall.copy(fontWeight = Type.labelLarge.fontWeight), color = c.accent)
        }
        Spacer(Modifier.height(14.dp))
        T(m.displayTitle, Type.titleSmall, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        T(m.line, Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textSecondary, maxLines = 2)
        Spacer(Modifier.height(14.dp))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(Fmt.dateDow(rules.date(rules.day(m.at))), Type.labelSmall, Modifier.weight(1f), color = c.textTertiary)
            m.seats?.let { SeatDots(it) }
        }
    }
}

@Composable
fun MomentPopup(m: Moment, rules: Rules, onClose: () -> Unit) {
    val c = Ivi.colors
    val minute = if (m.hasTime) rules.minuteOfDay(m.at) else null
    IviPopup(onClose, width = 820.dp, buttons = { IviButton("닫기", onClose, Modifier.weight(1f)) }) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(skyBrush(minute, m.weather?.sky)).border(2.dp, Ivi.colors.textTertiary, CircleShape))
        Spacer(Modifier.height(24.dp))
        T(m.kind.label, Type.labelLarge, color = c.textTertiary)
        T(m.displayTitle, Type.headlineSmall)
        T(m.line, Type.bodySmall, color = c.textSecondary)
        Spacer(Modifier.height(24.dp))
        Divider()
        val date = Fmt.dateDow(rules.date(rules.day(m.at)))
        Fact("언제", if (minute != null) "$date ${Fmt.hm(minute)}" else date)
        m.odoKm?.let { Fact("계기판", "${if (it % 1.0 == 0.0) Fmt.km(it) else Fmt.km1(it)} km") }
        m.outsideTempC?.let { Fact("바깥 기온", "${Math.round(it)}°") }
        // 기상청 관측소가 잰 값. 차가 있던 곳의 하늘이 아니라 가까운 관측소의 하늘이다.
        m.weather?.let { Fact("날씨", it.text) }
        m.seats?.let { n ->
            Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
                T("탄 자리", Type.labelLarge, Modifier.width(200.dp), color = c.textTertiary)
                SeatDots(n, size = 20.dp)
                Spacer(Modifier.width(14.dp))
                T("${n}자리", Type.bodySmall)
            }
            Divider()
        }
        Spacer(Modifier.height(20.dp))
        T(m.displayWhy, Type.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, Type.labelLarge, Modifier.width(200.dp), color = Ivi.colors.textTertiary)
        T(value, Type.bodySmall, maxLines = 1)
    }
    Divider()
}

// ---------- 숫자 ----------

@Composable
fun NumbersTab(state: AppState, rules: Rules, now: Long, onEdit: () -> Unit, notifyOn: Boolean = true, onNotify: () -> Unit = {}, onWeather: () -> Unit = {}) {
    val setup = state.setup ?: return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (wide(maxWidth)) {
            Row(Modifier.fillMaxSize().padding(horizontal = Gutter).padding(bottom = 32.dp), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                Column(Modifier.weight(1.15f).fillMaxHeight().verticalScroll(rememberScrollState())) { Together(state, rules, now, Type.hero) }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) { Counted(state, rules, now, onEdit, notifyOn, onNotify, onWeather) }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Gutter).padding(bottom = 32.dp)) {
                Together(state, rules, now, Type.heroCompact)
                Spacer(Modifier.height(32.dp))
                Counted(state, rules, now, onEdit, notifyOn, onNotify, onWeather)
            }
        }
    }
}

/** 함께한 날수와 함께 달린 거리가 가장 크다. 계기판 숫자는 작게 둔다. */
@Composable
private fun ColumnScope.Together(state: AppState, rules: Rules, now: Long, hero: androidx.compose.ui.text.TextStyle) {
    val setup = state.setup ?: return
    val c = Ivi.colors
    val odo = state.lastOdoKm
    val together = rules.kmTogether(setup, odo)
    Spacer(Modifier.height(16.dp))
    T("${Fmt.date(rules.date(setup.receivedDay))}부터 함께한 시간", Type.labelLarge, color = c.textTertiary)
    BigNumber(Fmt.n(rules.daysTogether(setup, now)), "일", hero)
    Spacer(Modifier.height(20.dp))
    T("함께 달린 거리", Type.labelLarge, color = c.textTertiary)
    BigNumber(together?.let { Fmt.km(it) } ?: "–", "km", hero)
    Spacer(Modifier.height(8.dp))
    // 함께 달린 거리를 어떻게 셌는지 한 줄로: 처음 만났을 때와 지금의 계기판
    val now0 = odo?.let { " · 지금 계기판 ${Fmt.km(it)} km" } ?: ""
    T("처음 만났을 때 ${Fmt.km(setup.receivedOdoKm)} km$now0", Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textSecondary)
    if (odo != null && together != null) {
        Spacer(Modifier.height(28.dp))
        // 다가오는 순간: 함께 달린 거리의 이정표가 먼저, 계기판 숫자가 그다음
        val t = rules.nextTogether(together)
        val next = rules.nextNumber(odo)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.backgroundSecondary).padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            T("다가오는 순간", Type.labelSmall, color = c.textTertiary)
            Upcoming(rules.togetherTitle(t), "함께 달린 거리", t - together)
            Upcoming("${Fmt.n(next)} km", "계기판 숫자 · ${rules.numberLabel(next)}", next - odo)
        }
    } else {
        T("계기판 숫자를 아직 읽지 못했습니다", Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textTertiary)
    }
}

@Composable
private fun Upcoming(title: String, note: String, left: Double) {
    val c = Ivi.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, Type.titleSmall, maxLines = 1)
            T(note, Type.labelSmall, color = c.textTertiary, maxLines = 1)
        }
        T("${if (left < 100) Fmt.km1(left) else Fmt.km(left)} km 남음", Type.bodySmall, color = c.textSecondary)
    }
}

@Composable
private fun BigNumber(number: String, unit: String, hero: androidx.compose.ui.text.TextStyle) {
    Row {
        T(number, hero, Modifier.alignByBaseline(), maxLines = 1)
        Spacer(Modifier.width(12.dp))
        T(unit, Type.titleMedium, Modifier.alignByBaseline(), color = Ivi.colors.textSecondary)
    }
}

/** 앱이 지켜본 뒤로 센 것. 설치하기 전의 횟수는 어림으로도 보여 주지 않는다. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ColumnScope.Counted(state: AppState, rules: Rules, now: Long, onEdit: () -> Unit, notifyOn: Boolean, onNotify: () -> Unit, onWeather: () -> Unit) {
    val setup = state.setup ?: return
    val c = Ivi.colors
    val k = state.counters
    val watched = rules.watchedDays(setup, now)
    Spacer(Modifier.height(16.dp))
    // 이 칸의 숫자는 앱을 설치해 기록을 시작한 날부터 센 것이다. 머리 줄에서 한 번만 말한다.
    T("${Fmt.date(rules.date(setup.installedDay))} 기록 시작", Type.labelLarge, color = c.textTertiary)
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.backgroundSecondary).padding(horizontal = 28.dp)) {
        Counter("도착한 횟수", "${Fmt.n(k.arrivals)}번", if (rules.arrivalMs >= 60_000) "주차하고 ${rules.arrivalMs / 60_000}분 넘게 머문 것만 셉니다" else "주차하고 머문 것만 셉니다 (시연용 설정: ${rules.arrivalMs / 1000}초)")
        Divider()
        Counter("옆자리에 사람이 탄 날", "${Fmt.n(k.companionDays)}일", if (k.companionDays > 0) "약 ${Fmt.n(Math.round(watched.toDouble() / k.companionDays))}일에 하루" else "아직 없습니다")
        Divider()
        Counter(
            "네 자리가 다 찬 날", "${Fmt.n(k.fullDays)}일",
            k.firstFullDay?.let { "처음은 ${Fmt.dateDow(rules.date(it))}요일이었습니다" } ?: "아직 없습니다",
        )
    }
    Spacer(Modifier.height(28.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IviButton("처음 등록한 정보 수정", onEdit, size = ButtonSize.SMALL, quiet = true)
        if (!notifyOn) IviButton("주차 중 알림 받기", onNotify, size = ButtonSize.SMALL, quiet = true)
        IviButton(if (setup.stationName == null) "그날의 날씨 남기기" else "관측소 바꾸기", onWeather, size = ButtonSize.SMALL, quiet = true)
    }
    Spacer(Modifier.height(12.dp))
    T(
        if (setup.stationName != null) "순간이 남은 날의 날씨를 ${setup.stationName} 관측소(기상청) 기준으로 함께 적습니다. 관측 자료는 다음 날부터 나옵니다."
        else "날씨를 남기면 순간이 남은 날과 차를 받은 날의 하늘을 기상청 관측 자료로 함께 적습니다.",
        Type.labelSmall, color = Ivi.colors.textTertiary,
    )
    Spacer(Modifier.height(12.dp))
    T(
        if (notifyOn) "화면을 닫아 둔 사이에 새 순간이 남거나 휴대폰에서 저장을 확인하면 차를 세웠을 때 알림으로 알립니다. 달리는 동안에는 알리지 않습니다."
        else "알림을 받으면 화면을 닫아 둔 사이에 남은 새 순간과 휴대폰의 저장 확인을 차를 세웠을 때 알려 줍니다.",
        Type.labelSmall, color = Ivi.colors.textTertiary,
    )
}

@Composable
private fun Counter(label: String, value: String, note: String) {
    val c = Ivi.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(label, Type.bodySmall)
            T(note, Type.labelSmall, color = c.textTertiary)
        }
        Spacer(Modifier.width(16.dp))
        T(value, Type.titleLarge, maxLines = 1)
    }
}

/** 날씨를 남길 관측소 고르기. 지금 위치로 고르거나 이름을 직접 고른다. 좌표는 차 밖으로 나가지 않는다. */
@Composable
fun StationPopup(stations: List<com.lrlr.carmigo.data.Station>, current: Int?, onLocate: () -> Unit, locating: Boolean, onPick: (Int) -> Unit, onClose: () -> Unit) {
    val c = Ivi.colors
    IviPopup(onClose, width = 1100.dp, buttons = {
        IviButton("닫기", onClose, Modifier.weight(1f))
        IviButton("지금 위치에서 가장 가까운 곳", onLocate, Modifier.weight(1.6f), filled = true, loading = locating)
    }) {
        PopupTitle(
            "어느 관측소의 날씨를 남길까요",
            "순간이 남은 날과 차를 받은 날의 하늘을 기상청 관측 자료로 적습니다. 차의 위치는 가장 가까운 관측소를 차 안에서 고르는 데만 쓰고, 밖으로는 관측소 번호와 날짜만 나갑니다.",
        )
        Spacer(Modifier.height(20.dp))
        T("가나다순 ${stations.size}곳입니다. 아래로 내리면 더 있습니다.", Type.labelSmall, color = c.textTertiary)
        Spacer(Modifier.height(12.dp))
        stations.sortedBy { it.name }.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { st ->
                    val on = st.id == current
                    Box(
                        Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (on) c.textPrimary else c.backgroundSecondary).pressable(onClick = { onPick(st.id) }),
                        contentAlignment = Alignment.Center,
                    ) { T(st.name, Type.labelLarge, color = if (on) c.backgroundPrimary else c.textPrimary, maxLines = 1) }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------- 주행 중 ----------

/** 달리는 동안에는 함께 달린 거리와 한 줄뿐이다. 움직이는 것도, 누를 것도 없다. */
@Composable
fun DrivingScreen(state: AppState, rules: Rules, now: Long) {
    val setup = state.setup ?: return
    val c = Ivi.colors
    val together = rules.kmTogether(setup, state.lastOdoKm)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = wide(maxWidth)
        val hero = if (isWide) Type.hero else Type.heroCompact
        Column(Modifier.fillMaxSize().padding(Gutter), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.last == LastDrive.DRIVING) {
                // 마지막 주행: 무엇을 하는 중인지 먼저 말하고, 함께한 날수를 가장 크게 둔다. 달리는 동안 바뀌는 소수점은 보이지 않는다.
                T("마지막 주행", Type.titleLarge)
                Spacer(Modifier.height(if (isWide) 40.dp else 28.dp))
                T("함께한 지", Type.bodyMedium, color = c.textTertiary)
                BigNumber(Fmt.n(rules.daysTogether(setup, now)), "일째", hero)
                if (together != null) {
                    Spacer(Modifier.height(12.dp))
                    T("함께 달린 거리 ${Fmt.km(together)} km", Type.titleMedium.copy(fontWeight = Type.bodyLarge.fontWeight), color = c.textSecondary)
                }
                // 처음 만난 날부터 오늘까지의 길을 움직이지 않는 한 줄로 함께 둔다
                Spacer(Modifier.height(if (isWide) 72.dp else 48.dp))
                Box(Modifier.widthIn(max = 1500.dp)) {
                    JourneyLine(
                        rules, setup.receivedDay, rules.day(now), state.moments,
                        startTitle = "처음 만난 날", startText = "${Fmt.date(rules.date(setup.receivedDay))} · ${Fmt.km(setup.receivedOdoKm)} km",
                        endTitle = "오늘", endText = Fmt.date(rules.date(rules.day(now))),
                    )
                }
            } else {
                T("함께 달린 거리", Type.bodyMedium, color = c.textTertiary)
                Spacer(Modifier.height(8.dp))
                // 주행 중에는 숫자가 자주 바뀌지 않게 정수 km만 보인다.
                BigNumber(together?.let { Fmt.km(it) } ?: "–", "km", hero)
                Spacer(Modifier.height(28.dp))
                T(rules.drivingLine(state, now), Type.titleMedium.copy(fontWeight = Type.bodyLarge.fontWeight), color = c.textSecondary, maxLines = 2, align = TextAlign.Center)
            }
        }
    }
}

// ---------- 마지막 주행 ----------

@Composable
fun LastConfirmPopup(onNo: () -> Unit, onYes: () -> Unit) {
    IviPopup(onNo, buttons = {
        IviButton("아직 아니에요", onNo, Modifier.weight(1f))
        IviButton("마지막 주행으로 남기기", onYes, Modifier.weight(1.4f), filled = true)
    }) {
        PopupTitle(
            "이 차와의 마지막 주행인가요",
            "차를 보내는 날에 누릅니다. 다음에 출발하는 주행이 마지막 주행으로 남고, 도착해 P에 놓으면 작별 화면이 열립니다. 도착한 뒤에도 되돌릴 수 있습니다.",
        )
    }
}

/** 작별 화면에서 한 줄이 다 그려진 뒤에 나타나는 것들의 차례 */
private const val LATE = 9

@Composable
fun FarewellScreen(state: AppState, rules: Rules, onExport: () -> Unit, onUndo: () -> Unit, onWipe: () -> Unit, onOpen: (Moment) -> Unit) {
    val setup = state.setup ?: return
    val c = Ivi.colors
    val end = state.lastDoneAt ?: return
    val k = state.counters
    val saved = state.export?.savedAt != null
    val moments = state.moments.sortedBy { it.at }
    val km = rules.kmTogether(setup, state.lastOdoKm)

    // 1. 처음 만난 날에서 마지막 주행까지: 함께한 날수와 거리, 그리고 그 사이에 남은 순간들이 놓인 한 줄
    @Composable
    fun ColumnScope.head(wide: Boolean) {
        val hero = if (wide) Type.hero else Type.heroCompact
        T("마지막 주행을 마쳤습니다", Type.titleSmall, Modifier.appear(0))
        Spacer(Modifier.height(if (wide) 24.dp else 20.dp))
        if (wide) {
            Row(Modifier.appear(2), horizontalArrangement = Arrangement.spacedBy(88.dp)) {
                Column { T("함께한 시간", Type.labelSmall, color = c.textTertiary); BigNumber(Fmt.n(rules.daysTogether(setup, end)), "일", hero) }
                if (km != null) Column { T("함께 달린 거리", Type.labelSmall, color = c.textTertiary); BigNumber(Fmt.km(km), "km", hero) }
            }
        } else {
            Column(Modifier.appear(2)) {
                T("함께한 시간", Type.labelSmall, color = c.textTertiary)
                BigNumber(Fmt.n(rules.daysTogether(setup, end)), "일", hero)
            }
            if (km != null) {
                Spacer(Modifier.height(8.dp))
                Column(Modifier.appear(4)) {
                    T("함께 달린 거리", Type.labelSmall, color = c.textTertiary)
                    BigNumber(Fmt.km(km), "km", hero)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        JourneyLine(
            rules, setup.receivedDay, rules.day(end), moments,
            startTitle = "처음 만난 날", startText = "${Fmt.date(rules.date(setup.receivedDay))} · ${Fmt.km(setup.receivedOdoKm)} km",
            endTitle = "마지막 주행", endText = Fmt.date(rules.date(rules.day(end))) + (state.lastOdoKm?.let { " · ${Fmt.km(it)} km" } ?: ""),
            drawIn = true, startNote = setup.receivedWeather?.text,
        )
    }

    // 2. 함께한 생활: 앱이 지켜본 뒤로 센 것
    @Composable
    fun ColumnScope.life() {
        val tiles = buildList {
            if (k.arrivals > 0) add("${Fmt.n(k.arrivals)}번" to "도착했습니다")
            if (k.companionDays > 0) add("${Fmt.n(k.companionDays)}일" to "옆자리에 사람이 탔습니다")
            if (k.fullDays > 0) add("${Fmt.n(k.fullDays)}일" to "네 자리가 다 찼습니다")
            if (k.energyWh >= 1000) add("${Fmt.kwh(k.energyWh)} kWh" to "전기를 함께 썼습니다")
        }
        if (tiles.isNotEmpty()) {
            T("${Fmt.date(rules.date(setup.installedDay))} 기록 시작", Type.labelLarge, Modifier.appear(LATE), color = c.textTertiary)
            Spacer(Modifier.height(12.dp))
            tiles.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEachIndexed { col, (value, label) ->
                        Column(Modifier.weight(1f).appear(LATE + 1 + row * 2 + col).clip(RoundedCornerShape(28.dp)).background(c.backgroundSecondary).padding(horizontal = 28.dp, vertical = 22.dp)) {
                            T(value, Type.titleLarge, maxLines = 1)
                            T(label, Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textSecondary, maxLines = 1)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(16.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 4. 기록 받아 가기
    @Composable
    fun ColumnScope.actions() {
        // 누를 것은 기다리게 하지 않는다
        Row(Modifier.appear(1), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IviButton("기록 받아 가기", onExport, filled = !saved)
            IviButton("마지막이 아니었어요", onUndo)
        }
        // 휴대폰에서 저장을 확인하면 지우기가 펼쳐지며 나타난다
        AnimatedVisibility(saved, enter = fadeIn(tween(Motion.ENTER)) + expandVertically(tween(Motion.ENTER)), exit = fadeOut(tween(Motion.EXIT)) + shrinkVertically(tween(Motion.EXIT))) {
            Column {
                Spacer(Modifier.height(20.dp))
                T("휴대폰에서 \"저장된 것을 확인했어요\"를 눌렀습니다. 차를 넘기기 전에 카미고에 남은 기록을 지울 수 있습니다.", Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textSecondary)
                Spacer(Modifier.height(12.dp))
                IviButton("카미고 기록 지우기", onWipe, filled = true, neutral = true)
            }
        }
    }

    // 3. 남은 순간들, 그리고 마지막 주행. 해마다 묶어 지나온 순서대로.
    @Composable
    fun pagesTitle() {
        T(if (moments.isEmpty()) "마지막 주행" else "함께한 순간 ${Fmt.n(moments.size)}장", Type.labelLarge, Modifier.appear(LATE), color = c.textTertiary)
    }

    @Composable
    fun pages() {
        Column(Modifier.fillMaxWidth().appear(LATE + 2).clip(RoundedCornerShape(28.dp)).background(c.backgroundSecondary).padding(horizontal = 28.dp).padding(bottom = 8.dp)) {
            var year = 0
            fun short(d: LocalDate) = Fmt.dateDow(d).substringAfter(". ")
            moments.forEach { m ->
                val d = rules.date(rules.day(m.at))
                if (d.year != year) {
                    year = d.year
                    T("${d.year}", Type.labelLarge, Modifier.padding(top = 24.dp, bottom = 4.dp), color = c.textTertiary)
                } else Divider()
                PageRow(if (m.hasTime) rules.minuteOfDay(m.at) else null, m.fullTitle, short(d), onClick = { onOpen(m) }, sky = m.weather?.sky)
            }
            val d = rules.date(rules.day(end))
            if (d.year != year) T("${d.year}", Type.labelLarge, Modifier.padding(top = 24.dp, bottom = 4.dp), color = c.textTertiary) else Divider()
            PageRow(rules.minuteOfDay(end), "마지막 주행", "${short(d)} ${Fmt.hm(rules.minuteOfDay(end))}", null, strong = true)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (wide(maxWidth)) {
            Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
                head(true)
                Spacer(Modifier.height(28.dp))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) { life(); actions() }
                    // 목록의 제목은 목록을 내려도 제자리에 남는다
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        pagesTitle()
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) { pages() }
                    }
                }
            }
        } else {
            // 좁은 화면: 순서는 그대로 두고, 기록 받아 가기는 목록을 다 내리지 않아도 누를 수 있게 아래에 붙여 둔다
            Column(Modifier.fillMaxSize().padding(horizontal = Gutter)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                    head(false)
                    Spacer(Modifier.height(32.dp))
                    life()
                    pagesTitle()
                    Spacer(Modifier.height(12.dp))
                    pages()
                }
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp)) { actions() }
            }
        }
    }
}

/**
 * 처음 만난 날에서 끝 날까지의 한 줄. 그 사이에 남은 순간들이 날짜 자리에 점으로 놓인다.
 * 움직이지 않는 그림이라 주행 중 화면에도 쓴다.
 */
@Composable
private fun JourneyLine(
    rules: Rules, startDay: Long, endDay: Long, moments: List<Moment>,
    startTitle: String, startText: String, endTitle: String, endText: String,
    /** 처음 만난 날에서 끝 날까지 선이 그어지고 순간이 날짜 차례로 놓이는 것을 한 번 보여 준다(주차 중 화면에서만). */
    drawIn: Boolean = false,
    /** 처음 만난 날의 날씨 한 줄(알 때만) */
    startNote: String? = null,
) {
    val c = Ivi.colors
    val span = (endDay - startDay).coerceAtLeast(1).toFloat()
    val drawn = remember { Animatable(if (drawIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (drawIn) { delay(250); drawn.animateTo(1f, tween(1300, easing = FastOutSlowInEasing)) }
    }
    val p = drawn.value
    fun shown(at: Float) = if (!drawIn) 1f else ((p - at) / 0.05f).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(68.dp)) {
            val inset = 11.dp
            val track = maxWidth - inset * 2
            fun x(day: Long) = inset + track * ((day - startDay) / span).coerceIn(0f, 1f)
            Box(Modifier.offset(x = inset, y = 12.dp).width(track * p).height(4.dp).clip(RoundedCornerShape(2.dp)).background(c.textQuaternary))
            // 해가 바뀌는 자리: 눈금 바로 아래 가운데에 연도
            var y = rules.date(startDay).year + 1
            while (LocalDate.of(y, 1, 1).toEpochDay() < endDay) {
                val at = x(LocalDate.of(y, 1, 1).toEpochDay())
                Box(Modifier.offset(x = at - 1.dp, y = 24.dp).width(2.dp).height(8.dp).background(c.textQuaternary))
                if (at > 60.dp && at < maxWidth - 60.dp) {
                    val yearOn = shown(((at - inset) / track).coerceIn(0f, 0.95f))
                    T("$y", Type.labelSmall, Modifier.offset(x = at - 40.dp, y = 34.dp).width(80.dp).graphicsLayer { alpha = yearOn }, color = c.textTertiary, maxLines = 1, align = TextAlign.Center)
                }
                y++
            }
            // 가까운 날의 점은 포개지지 않게 조금씩 벌린다
            val gap = 22.dp
            val lo = inset + gap
            val hi = maxWidth - inset - gap
            val sorted = moments.sortedBy { it.at }
            val xs = sorted.map { x(rules.day(it.at)).coerceIn(lo, hi) }.toMutableList()
            if ((hi - lo) >= gap * (xs.size - 1).coerceAtLeast(0)) {
                for (i in 1 until xs.size) if (xs[i] - xs[i - 1] < gap) xs[i] = xs[i - 1] + gap
                if (xs.isNotEmpty() && xs.last() > hi) {
                    xs[xs.size - 1] = hi
                    for (i in xs.size - 2 downTo 0) if (xs[i + 1] - xs[i] < gap) xs[i] = xs[i + 1] - gap
                }
            }
            sorted.forEachIndexed { i, m ->
                val on = shown(((xs[i] - inset) / track).coerceIn(0f, 0.95f))
                Box(
                    Modifier.offset(x = xs[i] - 9.dp, y = 5.dp).size(18.dp).graphicsLayer { alpha = on; scaleX = 0.4f + 0.6f * on; scaleY = 0.4f + 0.6f * on }.clip(CircleShape)
                        .background(skyBrush(if (m.hasTime) rules.minuteOfDay(m.at) else null, m.weather?.sky)).border(2.dp, c.textSecondary, CircleShape),
                )
            }
            Box(Modifier.offset(x = 0.dp, y = 3.dp).size(22.dp).clip(CircleShape).background(c.textPrimary))
            val endOn = shown(0.95f)
            Box(Modifier.offset(x = maxWidth - 22.dp, y = 3.dp).size(22.dp).graphicsLayer { alpha = endOn; scaleX = 0.4f + 0.6f * endOn; scaleY = 0.4f + 0.6f * endOn }.clip(CircleShape).background(c.textPrimary))
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                T(startTitle, Type.labelLarge, maxLines = 1)
                T(startText, Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textSecondary, maxLines = 1)
                if (startNote != null) T(startNote, Type.labelSmall, color = c.textTertiary, maxLines = 1)
            }
            Column(Modifier.weight(1f).graphicsLayer { alpha = shown(0.95f) }, horizontalAlignment = Alignment.End) {
                T(endTitle, Type.labelLarge, maxLines = 1)
                T(endText, Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight), color = c.textSecondary, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PageRow(minute: Int?, title: String, date: String, onClick: (() -> Unit)?, strong: Boolean = false, sky: String? = null) {
    val c = Ivi.colors
    Row(
        Modifier.fillMaxWidth().height(80.dp).then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (strong) Box(Modifier.size(22.dp).clip(CircleShape).background(c.textPrimary))
        else Box(Modifier.size(22.dp).clip(CircleShape).background(skyBrush(minute, sky)).border(2.dp, c.textSecondary, CircleShape))
        Spacer(Modifier.width(16.dp))
        T(title, if (strong) Type.bodySmall.copy(fontWeight = Type.titleSmall.fontWeight) else Type.bodySmall, Modifier.weight(1f), maxLines = 1)
        T(date, Type.labelSmall, color = if (strong) c.textSecondary else c.textTertiary)
    }
}

// ---------- 기록 받아 가기 ----------

@Composable
fun ExportPopup(state: AppState, sending: Sending, url: String?, onClose: () -> Unit, onRetry: () -> Unit, onWipe: () -> Unit) {
    val c = Ivi.colors
    val export = state.export
    val isLast = state.last == LastDrive.DONE
    val count = state.moments.size
    var showContents by rememberSaveable { mutableStateOf(false) }
    when {
        sending == Sending.UPLOADING -> IviPopup({}, buttons = { IviButton("전송 중", {}, Modifier.weight(1f), loading = true) }) {
            PopupTitle("기록을 전송하고 있습니다", "휴대폰에서 받아 볼 수 있도록 함께한 순간과 숫자를 전송하고 있습니다.")
        }
        sending == Sending.FAILED -> IviPopup(onClose, buttons = {
            IviButton("닫기", onClose, Modifier.weight(1f))
            IviButton("다시 시도", onRetry, Modifier.weight(1f), filled = true)
        }) {
            PopupTitle("기록을 전송하지 못했습니다", "차의 인터넷 연결을 확인한 뒤 다시 눌러 주세요. 차에 있는 기록은 그대로입니다.")
        }
        export?.savedAt != null -> IviPopup(onClose, buttons = {
            IviButton("닫기", onClose, Modifier.weight(1f))
            if (isLast) IviButton("카미고 기록 지우기", onWipe, Modifier.weight(1.4f), filled = true, neutral = true)
        }) {
            PopupTitle(
                "휴대폰에서 저장 확인을 마쳤습니다",
                "휴대폰에서 \"저장된 것을 확인했어요\"를 눌렀습니다. 카미고가 휴대폰의 파일을 직접 확인한 것은 아닙니다. 서버에 전송한 기록은 전송 후 7일 뒤 지워집니다.",
            )
            if (isLast) {
                Spacer(Modifier.height(16.dp))
                T("차를 넘기기 전에 카미고에 남은 기록을 지울 수 있습니다.", Type.bodySmall, color = c.textSecondary)
            }
        }
        export?.uploadedAt != null && url != null -> IviPopup(onClose, width = 980.dp, buttons = { IviButton("닫기", onClose, Modifier.weight(1f)) }) {
            T("기록 받아 가기", Type.titleMedium)
            Spacer(Modifier.height(24.dp))
            val words: @Composable ColumnScope.() -> Unit = {
                val body = Type.labelLarge.copy(fontWeight = Type.bodySmall.fontWeight)
                T("휴대폰으로 QR을 찍어 기록을 열고, 이미지나 PDF로 저장해 주세요.", body, color = c.textSecondary)
                T("함께한 순간과 숫자를 한 권의 기록으로 모았습니다.", body, color = c.textSecondary)
                T("기록 모음은 ‘기록 받아 가기’를 누를 때 서버로 전송됩니다. 서버의 기록은 전송 후 7일 뒤 지워집니다.", Type.labelSmall, color = c.textTertiary)
                IviButton(if (showContents) "포함되는 정보 접기" else "포함되는 정보", { showContents = !showContents }, size = ButtonSize.SMALL, quiet = true)
                if (showContents) {
                    T("차 이름, 함께한 날수와 거리, 순간 ${count}장, 도착 횟수, 동승한 날수, 함께 쓴 전기가 포함됩니다. 순간마다 날짜·시각, 계기판 거리, 바깥 기온, 탑승 좌석 수가 포함될 수 있습니다.", body, color = c.textSecondary)
                    T("좌표와 탑승자의 신원은 포함되지 않습니다. 날씨를 남겼다면 관측소 이름이 포함되어 대략의 지역을 알 수 있습니다. 날씨 조회에는 별도로 관측소 번호와 날짜·시각을 전송합니다.", Type.labelSmall, color = c.textTertiary)
                }
            }
            BoxWithConstraints {
                if (maxWidth < 700.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        QrImage(url, 280.dp)
                        words()
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        QrImage(url, 300.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = words)
                    }
                }
            }
        }
        else -> IviPopup(onClose, buttons = { IviButton("닫기", onClose, Modifier.weight(1f)) }) {
            PopupTitle("기록 받아 가기", "잠시 뒤 다시 눌러 주세요.")
        }
    }
}

@Composable
fun WipeConfirmPopup(onNo: () -> Unit, onYes: () -> Unit) {
    IviPopup(onNo, buttons = {
        IviButton("아직이요", onNo, Modifier.weight(1f))
        IviButton("지우기", onYes, Modifier.weight(1f), filled = true, danger = true)
    }) {
        PopupTitle("카미고의 기록을 지울까요?", "휴대폰에 저장한 이미지나 PDF가 열리는지 먼저 확인해 주세요. 카미고에서 지운 기록은 되돌릴 수 없습니다. 차량의 다른 앱이나 내비게이션 기록은 지우지 않습니다. 서버에 전송한 기록은 전송 후 7일 뒤 지워집니다.")
    }
}

@Composable
fun WipedScreen(wiped: Wiped, rules: Rules, onClose: () -> Unit) {
    CenterBox(Modifier.padding(Gutter)) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            LogoMark(88.dp)
            Spacer(Modifier.height(28.dp))
            T("카미고에 저장된 기록을 지웠습니다", Type.titleLarge, align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            T(
                "${Fmt.date(rules.date(wiped.receivedDay))}부터 함께한 ${Fmt.n(wiped.days)}일의 기록을 카미고에서 지웠습니다. 휴대폰에 저장한 파일은 그대로입니다.",
                Type.bodySmall, color = Ivi.colors.textSecondary, align = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            T(
                "${Fmt.with(wiped.carName)} 함께한 시간을 오래 간직해 주세요.",
                Type.titleSmall, align = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            IviButton("닫기", onClose)
        }
    }
}

// ---------- 시연 모드 ----------

/** 길게 누르면 숨겨 둔 동작을 한다 */
private fun Modifier.secret(onLongPress: () -> Unit): Modifier =
    pointerInput(onLongPress) { detectTapGestures(onLongPress = { onLongPress() }) }

/**
 * 심사와 시연을 위한 창. 예시 기록을 불러오고, 시연 주행으로 음성 안내와 순간 카드, 마지막 주행을 볼 수 있다.
 * 켜 둔 동안에는 차의 기어와 주행거리 신호를 쓰지 않는다.
 */
@Composable
fun DemoPopup(state: AppState, canDrive: Boolean, onLoad: () -> Unit, onDrive: () -> Unit, onEnd: () -> Unit, onClose: () -> Unit) {
    IviPopup(onClose, width = 900.dp, buttons = { IviButton("닫기", onClose, Modifier.weight(1f)) }) {
        PopupTitle(
            "시연 모드",
            "심사와 시연을 위한 기능입니다. 예시 기록(흰둥이, 3년 4개월)을 불러오고, 차의 기어와 주행거리 대신 아래 버튼으로 주행합니다. " +
                "시연 모드에서는 P에 놓고 10초 머물면 도착으로 셉니다.",
        )
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            DemoRow(
                "예시 기록 불러오기",
                "이 앱에 있는 지금 기록은 지워집니다. 날짜는 오늘 기준으로 맞춥니다.",
                onLoad,
            )
            DemoRow(
                "시연 주행",
                when {
                    !state.demo -> "예시 기록을 먼저 불러와 주세요."
                    state.last == LastDrive.DONE -> "마지막 주행을 마쳤습니다. 기록 받아 가기와 지우기를 이어서 볼 수 있습니다."
                    else -> "출발해서 다음 계기판 숫자를 지나가거나 5km를 달린 뒤 P에 놓습니다. \"마지막 주행\"을 누른 뒤에 하면 작별 화면이 열립니다."
                },
                onDrive, enabled = canDrive,
            )
            DemoRow("시연 모드 끄기", "예시 기록을 지우고 첫 실행 화면으로 돌아갑니다.", onEnd, enabled = state.demo)
        }
    }
}

@Composable
private fun DemoRow(label: String, note: String, onClick: () -> Unit, enabled: Boolean = true) {
    val c = Ivi.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        IviButton(label, onClick, Modifier.width(300.dp), enabled = enabled)
        T(note, Type.bodySmall, Modifier.weight(1f), color = c.textSecondary)
    }
}

