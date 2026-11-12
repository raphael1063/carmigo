package com.lrlr.carmigo.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 텍스트 한 줄. 색을 따로 주지 않으면 text_primary. */
@Composable
fun T(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Ivi.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align)
}

/**
 * 가이드의 터치 반응(Foundations > Interaction): 누르는 동안 95%로 줄어들고 배경색이 바뀐다.
 * 떼면 실행하고, 영역 밖으로 나가면 취소한다.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.95f else 1f, tween(100), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

enum class ButtonSize(val height: Dp, val pad: Dp, val radius: Dp) {
    LARGE(100.dp, 44.dp, 32.dp), MEDIUM(80.dp, 36.dp, 26.dp), SMALL(64.dp, 28.dp, 20.dp)
}

/** 가이드의 Basic button. filled = 어두운 쪽(확인), 아니면 옅은 쪽(기본). */
@Composable
fun IviButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    size: ButtonSize = ButtonSize.MEDIUM,
    enabled: Boolean = true,
    loading: Boolean = false,
    /** 눈에 덜 띄어야 하는 버튼(마지막 주행)의 글자색 */
    quiet: Boolean = false,
    /** 되돌릴 수 없는 동작: 채운 버튼이어도 로고 색 대신 중립색 */
    neutral: Boolean = false,
) {
    val c = Ivi.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val active = enabled && !loading
    val scale by animateFloatAsState(if (pressed && active) 0.95f else 1f, tween(100), label = "press")
    val bg = when {
        !active -> c.buttonBasicEnabled
        filled && neutral -> if (pressed) c.buttonNeutralPressed else c.buttonNeutralEnabled
        filled -> if (pressed) c.buttonFilledPressed else c.buttonFilledEnabled
        else -> if (pressed) c.buttonBasicPressed else c.buttonBasicEnabled
    }
    val fg = when {
        // 흐린 버튼은 누를 수 없을 때에도 읽을 수 있어야 한다("마지막 주행"은 주행 중에도 보인다)
        !active -> if (quiet) c.textTertiary else c.textQuaternary
        filled && neutral -> c.onButtonNeutral
        filled -> c.onButtonFilled
        quiet -> c.textTertiary
        else -> c.textPrimary
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .heightIn(min = size.height)
            .clip(RoundedCornerShape(size.radius))
            .background(bg)
            .clickable(interactionSource = source, indication = null, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = size.pad),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(32.dp), color = c.textTertiary, strokeWidth = 3.dp)
        else T(text, if (size == ButtonSize.SMALL) Type.labelLarge else Type.bodySmall.copy(fontWeight = Type.labelLarge.fontWeight), color = fg, maxLines = 1)
    }
}

/** 가이드의 Tabs(Box 타입): 옅은 통 안에서 선택된 항목만 밝은 판으로 올라온다. */
@Composable
fun IviTabs(labels: List<String>, selected: Int, badge: (Int) -> Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Ivi.colors
    Row(
        modifier.clip(RoundedCornerShape(24.dp)).background(c.buttonBasicEnabled).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val on = i == selected
            Row(
                Modifier
                    .height(68.dp)
                    .widthIn(min = 150.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (on) c.backgroundSecondary else Color.Transparent)
                    .pressable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                T(label, Type.labelLarge, color = if (on) c.textPrimary else c.textTertiary)
                val n = badge(i)
                if (n > 0) {
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.heightIn(min = 32.dp).widthIn(min = 32.dp).clip(CircleShape).background(c.buttonFilledEnabled).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        T(if (n > 9) "9+" else n.toString(), Type.labelSmall.copy(fontWeight = Type.labelLarge.fontWeight), color = c.onButtonFilled)
                    }
                }
            }
        }
    }
}

/** 움직임의 길이. 나타날 때는 천천히 자리를 잡고, 사라질 때는 짧게 비킨다. 주행 중 화면에는 쓰지 않는다. */
object Motion {
    const val ENTER = 280
    const val EXIT = 140
    const val STEP = 70
}

/** 나타날 때 한 번: 옅게 떠오르며 자리를 잡는다. order가 클수록 조금 늦게 나타나 위에서 아래로 차례가 생긴다. */
@Composable
fun Modifier.appear(order: Int = 0, rise: Dp = 14.dp, scaleFrom: Float = 1f): Modifier {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order.toLong() * Motion.STEP)
        a.animateTo(1f, tween(Motion.ENTER, easing = LinearOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * rise.toPx()
        val s = scaleFrom + (1f - scaleFrom) * a.value
        scaleX = s; scaleY = s
    }
}

/** 값이 생기면 나타나고 없어지면 사라지는 자리(팝업). 사라지는 동안에는 마지막 내용을 그대로 보여 준다. */
@Composable
fun <T : Any> Fading(target: T?, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(target) }
    if (target != null) last = target
    AnimatedVisibility(
        visible = target != null,
        enter = fadeIn(tween(Motion.ENTER, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(Motion.EXIT)),
    ) { last?.let { content(it) } }
}

/** 가이드의 System Popup: 어두운 막 위에 제목, 설명, 버튼(가로 정렬). 내용이 길면 안에서 스크롤한다. */
@Composable
fun IviPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 760.dp,
    buttons: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ivi.colors
    val none = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(c.dim).clickable(interactionSource = none, indication = null, onClick = onDismiss).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier
                .widthIn(max = width)
                .fillMaxWidth()
                .appear(rise = 20.dp, scaleFrom = 0.97f)
                .clip(RoundedCornerShape(40.dp))
                .background(c.backgroundPopup)
                // 팝업 안을 눌러도 닫히지 않게 한다
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(40.dp),
        ) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            Spacer(Modifier.height(36.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { buttons() }
        }
    }
}

@Composable
fun PopupTitle(title: String, description: String? = null) {
    T(title, Type.titleMedium)
    if (description != null) {
        Spacer(Modifier.height(16.dp))
        T(description, Type.bodySmall, color = Ivi.colors.textSecondary)
    }
}

/** 카드의 하늘색. 날씨는 알 수 없어서 시각만으로 고른다. 시각을 모르면 회색. */
@Composable
fun skyBrush(minuteOfDay: Int?, sky: String? = null): Brush {
    // 시각은 모르고 그날의 날씨만 아는 카드는 한낮의 하늘로 그린다
    @Suppress("NAME_SHADOWING") val minuteOfDay = minuteOfDay ?: if (sky != null) 720 else null
    val (clearA, clearB) = skyColors(minuteOfDay)
    // 날씨를 알면 구름 양과 비·눈만큼 잿빛을 섞는다
    val t = when (sky) { "구름많음" -> 0.35f; "흐림" -> 0.62f; "비" -> 0.72f; "눈" -> 0.55f; else -> 0f }
    if (t == 0f || minuteOfDay == null) return Brush.linearGradient(listOf(clearA, clearB))
    val night = minuteOfDay < 330 || minuteOfDay >= 1170
    val grey = if (sky == "눈") (if (night) Color(0xFF4A4F5C) else Color(0xFFC9CED6)) else (if (night) Color(0xFF2A2D36) else Color(0xFF7D838D))
    return Brush.linearGradient(listOf(androidx.compose.ui.graphics.lerp(clearA, grey, t), androidx.compose.ui.graphics.lerp(clearB, grey, t)))
}

@Composable
private fun skyColors(minuteOfDay: Int?): Pair<Color, Color> {
    val (a, b) = when {
        minuteOfDay == null -> Ivi.colors.surfaceAccent to Ivi.colors.surfaceAccent
        minuteOfDay < 330 || minuteOfDay >= 1170 -> Color(0xFF0B1024) to Color(0xFF1C2748)
        minuteOfDay < 450 -> Color(0xFF3A3566) to Color(0xFFE08A58)
        minuteOfDay < 960 -> Color(0xFF2F6FAE) to Color(0xFF8DBFE6)
        minuteOfDay < 1050 -> Color(0xFF4F79B4) to Color(0xFFEDA25F)
        else -> Color(0xFF2B2A55) to Color(0xFFC96A4A)
    }
    return a to b
}

/** 그때 탄 자리 수. 네 칸 가운데 찬 만큼만 칠한다. 누가 탔는지는 모른다. */
@Composable
fun SeatDots(count: Int, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val c = Ivi.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(4) { i ->
            Box(Modifier.size(size).clip(CircleShape).background(if (i < count) c.textSecondary else c.textQuaternary))
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ivi.colors.divider))
}

@Composable
fun CenterBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}

/** QR 코드. 화면 테마와 상관없이 흰 바탕에 검정으로 그린다(휴대폰 카메라가 읽기 쉽게). */
@Composable
fun QrImage(text: String, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { qrBitmap(text) }
    Box(modifier.size(size).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp)) {
        if (bitmap != null) {
            Image(
                BitmapPainter(bitmap.asImageBitmap(), filterQuality = androidx.compose.ui.graphics.FilterQuality.None),
                contentDescription = "휴대폰으로 찍는 QR 코드",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun qrBitmap(text: String): Bitmap? = try {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
    bmp
} catch (e: Exception) {
    null
}

/** 카미고 로고(청록 판 위의 크림색 차). 앱 이름 옆과 첫 화면, 마지막 화면에만 둔다. */
@Composable
fun LogoMark(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = androidx.compose.ui.res.painterResource(com.lrlr.carmigo.R.drawable.logo_mark),
        contentDescription = null,
        // 다크 모드에서는 짙은 청록 판이 배경에 묻히지 않게 옅은 크림 테두리를 두른다
        modifier = modifier.size(size).then(
            if (Ivi.colors.dark) Modifier.border(1.5.dp, BrandCream.copy(alpha = 0.45f), RoundedCornerShape(size * 0.25f)) else Modifier
        ),
    )
}
