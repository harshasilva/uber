package lk.ridewise.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest = Color(0xFF123D32)
val Lime = Color(0xFFCEF49A)
val Amber = Color(0xFF785900)
private val Light = lightColorScheme(primary = Color(0xFF17694F), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8EDDE), onPrimaryContainer = Forest,
    secondary = Color(0xFF52644F), secondaryContainer = Color(0xFFEAF1E6),
    background = Color(0xFFF5F7F4), surface = Color(0xFFFFFFFF), surfaceVariant = Color(0xFFEAF0E9),
    onBackground = Color(0xFF17251E), onSurface = Color(0xFF17251E), onSurfaceVariant = Color(0xFF53635A),
    outline = Color(0xFF758279), outlineVariant = Color(0xFFDDE5DD), error = Color(0xFFAF3434))
private val Dark = darkColorScheme(primary = Lime, onPrimary = Forest,
    primaryContainer = Color(0xFF214E3C), onPrimaryContainer = Color(0xFFE1F5D9),
    background = Color(0xFF111A16), surface = Color(0xFF1B2620), surfaceVariant = Color(0xFF27362C),
    onBackground = Color(0xFFECF2E9), onSurface = Color(0xFFECF2E9), onSurfaceVariant = Color(0xFFB5C5B8),
    outlineVariant = Color(0xFF3B4C40))
private val Type = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)
@Composable fun RideWiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
