package com.salroumi.smscleaner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.salroumi.smscleaner.R

/**
 * Colors, type and shapes of the DGA "Platforms Code" (كود المنصات), the unified design system for Saudi
 * government platforms: SA green brand scale, neutral grays, semantic status palettes, IBM Plex Sans Arabic.
 */
object Dga {
    val Sa25 = Color(0xFFF7FDF9)
    val Sa50 = Color(0xFFF3FCF6)
    val Sa100 = Color(0xFFDFF6E7)
    val Sa200 = Color(0xFFB8EACB)
    val Sa300 = Color(0xFF88D8AD)
    val Sa400 = Color(0xFF54C08A)
    val Sa500 = Color(0xFF25935F)
    val Sa600 = Color(0xFF1B8354)
    val Sa700 = Color(0xFF166A45)
    val Sa800 = Color(0xFF14573A)
    val Sa900 = Color(0xFF104631)
    val Sa950 = Color(0xFF092A1E)
    val SaFlag = Color(0xFF074D31)

    val Gray25 = Color(0xFFFCFCFD)
    val Gray50 = Color(0xFFF9FAFB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray300 = Color(0xFFD2D6DB)
    val Gray400 = Color(0xFF9DA4AE)
    val Gray500 = Color(0xFF6C737F)
    val Gray600 = Color(0xFF4D5761)
    val Gray700 = Color(0xFF384250)
    val Gray800 = Color(0xFF1F2A37)
    val Gray900 = Color(0xFF111927)
    val Gray950 = Color(0xFF0D121C)
    val Ink = Color(0xFF161616)

    val Error50 = Color(0xFFFEF3F2)
    val Error300 = Color(0xFFFDA29B)
    val Error600 = Color(0xFFD92D20)
    val Error700 = Color(0xFFB42318)
    val Warning50 = Color(0xFFFFFAEB)
    val Warning300 = Color(0xFFFEC84B)
    val Warning600 = Color(0xFFDC6803)
    val Warning700 = Color(0xFFB54708)
    val Success50 = Color(0xFFECFDF3)
    val Success300 = Color(0xFF75E0A7)
    val Success600 = Color(0xFF079455)
    val Success700 = Color(0xFF067647)
    val Info50 = Color(0xFFEFF8FF)
    val Info300 = Color(0xFF84CAFF)
    val Info600 = Color(0xFF1570EF)
    val Info700 = Color(0xFF175CD3)
    val Lavender50 = Color(0xFFF9F5FA)
    val Lavender300 = Color(0xFFCCADD9)
    val Lavender600 = Color(0xFF6D428F)
    val Gold50 = Color(0xFFFFFEF2)
    val Gold300 = Color(0xFFFAE996)
    val Gold700 = Color(0xFFB87B02)
}

/** Colors outside Material's scheme: money in/out and the tag (badge) pairs, per light/dark mode. */
@Immutable
data class AppColors(
    val income: Color,
    val expense: Color,
    val chart: Color,
    val heroBackground: Color,
    val heroContent: Color,
    val textSecondary: Color,
)

private val LightApp = AppColors(
    income = Dga.Success700,
    expense = Dga.Error600,
    chart = Dga.Sa600,
    heroBackground = Dga.SaFlag,
    heroContent = Color.White,
    textSecondary = Dga.Gray700,
)
private val DarkApp = AppColors(
    income = Dga.Success300,
    expense = Dga.Error300,
    chart = Dga.Sa400,
    heroBackground = Dga.Sa900,
    heroContent = Color.White,
    textSecondary = Dga.Gray300,
)

val LocalAppColors = staticCompositionLocalOf { LightApp }

private val Light = lightColorScheme(
    primary = Dga.Sa600,
    onPrimary = Color.White,
    primaryContainer = Dga.Sa50,
    onPrimaryContainer = Dga.Sa800,
    secondary = Dga.Gray700,
    onSecondary = Color.White,
    secondaryContainer = Dga.Sa50,
    onSecondaryContainer = Dga.Sa900,
    tertiary = Dga.Info600,
    background = Dga.Gray50,
    onBackground = Dga.Ink,
    surface = Color.White,
    onSurface = Dga.Ink,
    surfaceVariant = Dga.Gray100,
    onSurfaceVariant = Dga.Gray600,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Dga.Gray50,
    surfaceContainerHighest = Dga.Gray100,
    outline = Dga.Gray300,
    outlineVariant = Dga.Gray200,
    error = Dga.Error600,
    onError = Color.White,
    errorContainer = Dga.Error50,
    onErrorContainer = Dga.Error700,
)

private val Dark = darkColorScheme(
    primary = Dga.Sa400,
    onPrimary = Dga.Sa950,
    primaryContainer = Dga.Sa900,
    onPrimaryContainer = Dga.Sa100,
    secondary = Dga.Gray300,
    onSecondary = Dga.Gray950,
    secondaryContainer = Dga.Sa950,
    onSecondaryContainer = Dga.Sa100,
    tertiary = Dga.Info300,
    background = Dga.Gray950,
    onBackground = Dga.Gray50,
    surface = Dga.Gray900,
    onSurface = Dga.Gray50,
    surfaceVariant = Dga.Gray800,
    onSurfaceVariant = Dga.Gray400,
    surfaceContainerLowest = Dga.Gray950,
    surfaceContainerLow = Dga.Gray900,
    surfaceContainer = Dga.Gray900,
    surfaceContainerHigh = Dga.Gray800,
    surfaceContainerHighest = Dga.Gray800,
    outline = Dga.Gray600,
    outlineVariant = Dga.Gray700,
    error = Dga.Error300,
    onError = Dga.Gray950,
    errorContainer = Color(0xFF55160C),
    onErrorContainer = Dga.Error50,
)

private val Plex = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Plex, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight)

/** DGA type scale: text xs 12/18, sm 14/20, md 16/24, lg 18/28, xl 20/30, display xs 24/32, sm 30/38. */
private val DgaTypography = Typography(
    displaySmall = style(30, 38, FontWeight.Bold),
    headlineLarge = style(30, 38, FontWeight.Bold),
    headlineMedium = style(24, 32, FontWeight.Bold),
    headlineSmall = style(20, 30, FontWeight.Bold),
    titleLarge = style(20, 30, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24),
    bodyMedium = style(14, 20),
    bodySmall = style(12, 18),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 18, FontWeight.Medium),
    labelSmall = style(12, 18),
)

/** DGA radius: sm 4 (buttons, inputs), md 8 (cards), lg 16 (sheets, dialogs). */
private val DgaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** App theme. The UI is Arabic, so layout is always right-to-left. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = DgaTypography,
        shapes = DgaShapes,
    ) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalAppColors provides if (dark) DarkApp else LightApp,
            content = content,
        )
    }
}

/** Light background + dark text tag colors (DGA "Tag" component) for each message category. */
data class TagColors(val container: Color, val content: Color)

@Composable
fun tagColors(kind: TagKind): TagColors {
    val dark = isSystemInDarkTheme()
    return if (!dark) {
        when (kind) {
            TagKind.ERROR -> TagColors(Dga.Error50, Dga.Error700)
            TagKind.WARNING -> TagColors(Dga.Warning50, Dga.Warning700)
            TagKind.SUCCESS -> TagColors(Dga.Success50, Dga.Success700)
            TagKind.INFO -> TagColors(Dga.Info50, Dga.Info700)
            TagKind.BRAND -> TagColors(Dga.Sa50, Dga.Sa700)
            TagKind.LAVENDER -> TagColors(Dga.Lavender50, Dga.Lavender600)
            TagKind.GOLD -> TagColors(Dga.Gold50, Dga.Gold700)
            TagKind.NEUTRAL -> TagColors(Dga.Gray100, Dga.Gray700)
        }
    } else {
        when (kind) {
            TagKind.ERROR -> TagColors(Color(0xFF55160C), Dga.Error300)
            TagKind.WARNING -> TagColors(Color(0xFF4E1D09), Dga.Warning300)
            TagKind.SUCCESS -> TagColors(Color(0xFF053321), Dga.Success300)
            TagKind.INFO -> TagColors(Color(0xFF102A56), Dga.Info300)
            TagKind.BRAND -> TagColors(Dga.Sa950, Dga.Sa300)
            TagKind.LAVENDER -> TagColors(Color(0xFF281047), Dga.Lavender300)
            TagKind.GOLD -> TagColors(Color(0xFF472400), Dga.Gold300)
            TagKind.NEUTRAL -> TagColors(Dga.Gray800, Dga.Gray300)
        }
    }
}

enum class TagKind { ERROR, WARNING, SUCCESS, INFO, BRAND, LAVENDER, GOLD, NEUTRAL }
