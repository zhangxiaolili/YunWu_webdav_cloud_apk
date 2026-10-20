package com.clouddock.webdav.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.clouddock.webdav.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F63E8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E9FF),
    onPrimaryContainer = Color(0xFF111A4D),
    inversePrimary = Color(0xFFB9C3FF),
    secondary = Color(0xFF0D8F83),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F4EE),
    onSecondaryContainer = Color(0xFF043B36),
    tertiary = Color(0xFFE1A02B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEBC8),
    onTertiaryContainer = Color(0xFF4B3300),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = Color(0xFF5D6475),
    surfaceTint = Color(0xFF4F63E8),
    inverseSurface = Color(0xFF2B2F3A),
    inverseOnSurface = Color(0xFFF2F4F9),
    error = Color(0xFFD14343),
    onError = Color.White,
    errorContainer = Color(0xFFFCE5E5),
    onErrorContainer = Color(0xFF5A1111),
    outline = Color(0xFFD8DDE9),
    outlineVariant = Color(0xFFE9EDF4),
    scrim = Color.Black
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADB9FF),
    onPrimary = Color(0xFF172167),
    primaryContainer = Color(0xFF2C3A80),
    onPrimaryContainer = Color(0xFFDFE2FF),
    inversePrimary = Color(0xFF4F63E8),
    secondary = Color(0xFF68DACC),
    onSecondary = Color(0xFF003732),
    secondaryContainer = Color(0xFF0C4B45),
    onSecondaryContainer = Color(0xFFC7F5ED),
    tertiary = Color(0xFFF4C56A),
    onTertiary = Color(0xFF422E00),
    tertiaryContainer = Color(0xFF5F4400),
    onTertiaryContainer = Color(0xFFFFE9B8),
    background = Color(0xFF0C1017),
    onBackground = Color(0xFFE8EBF2),
    surface = Color(0xFF141922),
    onSurface = Color(0xFFE8EBF2),
    surfaceVariant = Color(0xFF202633),
    onSurfaceVariant = Color(0xFFA6ADBE),
    surfaceTint = Color(0xFFADB9FF),
    inverseSurface = Color(0xFFE8EBF2),
    inverseOnSurface = Color(0xFF2B2F3A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF680005),
    errorContainer = Color(0xFF6B1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF3A4252),
    outlineVariant = Color(0xFF282F3D),
    scrim = Color.Black
)

private val BaseTypography = Typography()

val CloudDockTypography = Typography(
    displaySmall = BaseTypography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp
    ),
    headlineLarge = BaseTypography.headlineLarge.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = BaseTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = BaseTypography.bodyLarge.copy(lineHeight = 25.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(lineHeight = 21.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.Medium)
)

val CloudDockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CloudDockTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CloudDockTypography,
        shapes = CloudDockShapes,
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

data class AccountAccent(
    val main: Color,
    val softLight: Color,
    val softDark: Color
)

private val AccountAccents = listOf(
    AccountAccent(Color(0xFF4F63E8), Color(0xFFE7EAFF), Color(0xFF27306B)),
    AccountAccent(Color(0xFF0D8F83), Color(0xFFD9F5EF), Color(0xFF114842)),
    AccountAccent(Color(0xFFE08822), Color(0xFFFFEFD9), Color(0xFF4D3512)),
    AccountAccent(Color(0xFFD64D72), Color(0xFFFFE5EC), Color(0xFF55202E)),
    AccountAccent(Color(0xFF8A5BD6), Color(0xFFF1E8FF), Color(0xFF352552)),
    AccountAccent(Color(0xFF2E7FB8), Color(0xFFE1F1FF), Color(0xFF15374D))
)

@Composable
fun accountAccent(index: Int): AccountAccent {
    val accent = AccountAccents[Math.floorMod(index, AccountAccents.size)]
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) accent.copy(softLight = accent.softDark) else accent
}
