package com.lrlr.carmigo.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Pleos Connect UI Design Guide의 색 토큰(document.pleos.ai, Foundations > Colors).
 * 이름은 가이드의 토큰 이름을 따른다. 라이트와 다크는 시스템 설정을 그대로 따른다.
 */
data class IviColors(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val backgroundPopup: Color,
    val surfaceHigh: Color,
    val surfaceAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textQuaternary: Color,
    val buttonBasicEnabled: Color,
    val buttonBasicPressed: Color,
    val buttonFilledEnabled: Color,
    val buttonFilledPressed: Color,
    val onButtonFilled: Color,
    val divider: Color,
    val dim: Color,
    /** informative_positive: 활성 상태와 새 소식에만 쓰는 포인트 색 */
    val positive: Color,
    val negative: Color,
)

private val Ink = Color(0xFF131417)

val IviLight = IviColors(
    backgroundPrimary = Color(0xFFF7F8FA),
    backgroundSecondary = Color(0xFFFFFFFF),
    backgroundPopup = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFEDEEF2),
    surfaceAccent = Color(0xFFE0E1E6),
    textPrimary = Ink,
    textSecondary = Ink.copy(alpha = 0.84f),
    textTertiary = Ink.copy(alpha = 0.64f),
    textQuaternary = Ink.copy(alpha = 0.32f),
    buttonBasicEnabled = Ink.copy(alpha = 0.05f),
    buttonBasicPressed = Ink.copy(alpha = 0.10f),
    buttonFilledEnabled = Color(0xFF44464E),
    buttonFilledPressed = Color(0xFF313236),
    onButtonFilled = Color.White,
    divider = Color(0xFFE0E1E6),
    dim = Ink.copy(alpha = 0.60f),
    positive = Color(0xFF0064FF),
    negative = Color(0xFFFE3D16),
)

val IviDark = IviColors(
    backgroundPrimary = Color(0xFF1D1E21),
    backgroundSecondary = Color(0xFF131417),
    backgroundPopup = Color(0xFF313236),
    surfaceHigh = Color(0xFF313236),
    surfaceAccent = Color(0xFF44464E),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.84f),
    textTertiary = Color.White.copy(alpha = 0.64f),
    textQuaternary = Color.White.copy(alpha = 0.32f),
    buttonBasicEnabled = Color.White.copy(alpha = 0.05f),
    buttonBasicPressed = Color.White.copy(alpha = 0.10f),
    buttonFilledEnabled = Color(0xFFE0E1E6),
    buttonFilledPressed = Color(0xFFC7C9CE),
    onButtonFilled = Ink,
    divider = Color(0xFF44464E),
    dim = Color.Black.copy(alpha = 0.60f),
    positive = Color(0xFF0082FF),
    negative = Color(0xFFFF4C28),
)

private val LocalIvi = staticCompositionLocalOf { IviLight }

/**
 * 시스템 서체. Pleos Connect에는 Asta Sans가 "pretendard"라는 이름으로 들어 있다.
 * 없는 기기에서는 시스템 기본 서체로 내려간다.
 */
private val SystemFont = FontFamily(
    Font(DeviceFontFamilyName("pretendard"), FontWeight.Normal),
    Font(DeviceFontFamilyName("pretendard"), FontWeight.SemiBold),
    Font(DeviceFontFamilyName("pretendard"), FontWeight.Bold),
    Font(DeviceFontFamilyName("pretendard"), FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight) =
    TextStyle(fontFamily = SystemFont, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** 가이드의 글자 위계(Foundations > Typography). 크기와 줄 높이는 가이드 값 그대로다. */
object Type {
    val headlineLarge = style(60, 80, FontWeight.ExtraBold)
    val headlineMedium = style(56, 72, FontWeight.ExtraBold)
    val headlineSmall = style(48, 64, FontWeight.Bold)
    val titleLarge = style(40, 52, FontWeight.Bold)
    val titleMedium = style(36, 44, FontWeight.Bold)
    val titleSmall = style(32, 40, FontWeight.Bold)
    val bodyLarge = style(30, 38, FontWeight.Normal)
    val bodyMedium = style(28, 36, FontWeight.Normal)
    val bodySmall = style(26, 34, FontWeight.Normal)
    val labelLarge = style(24, 32, FontWeight.SemiBold)
    val labelSmall = style(20, 28, FontWeight.Normal)
    /** 화면에서 가장 큰 숫자 한두 개(함께한 날수, 함께 달린 거리)에만 쓴다 */
    val hero = style(104, 116, FontWeight.ExtraBold)
    val heroCompact = style(72, 84, FontWeight.ExtraBold)
}

object Ivi {
    val colors: IviColors
        @Composable @ReadOnlyComposable get() = LocalIvi.current
}

@Composable
fun DaysTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val c = if (dark) IviDark else IviLight
    val scheme = if (dark) {
        darkColorScheme(primary = c.positive, background = c.backgroundPrimary, surface = c.backgroundSecondary,
            onBackground = c.textPrimary, onSurface = c.textPrimary)
    } else {
        lightColorScheme(primary = c.positive, background = c.backgroundPrimary, surface = c.backgroundSecondary,
            onBackground = c.textPrimary, onSurface = c.textPrimary)
    }
    // 낮·밤이 바뀌어도 시스템 막대 색이 화면과 어긋나지 않게 한다
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = c.backgroundPrimary.toArgb()
            window.navigationBarColor = c.backgroundPrimary.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalIvi provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
