package cn.campus.schedule

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Green = Color(0xFF176B52)
    val Paper = Color(0xFFF3F7F4)
    val Ink = Color(0xFF172D26)
    val Night = Color(0xFF14251E)
    val Mint = Color(0xFF91D6B4)
    val LightInk = Color(0xFFE2F2E9)
    val Container = Color(0xFFD8EBE1)
    val Navigation = Color(0xFFEAF1EC)
    val DarkContainer = Color(0xFF244C3C)
    val DarkNavigation = Color(0xFF1C3026)
}
data class CampusPalette(
    val primary: Color, val lightBackground: Color, val lightInk: Color,
    val lightContainer: Color, val lightNavigation: Color,
    val darkBackground: Color, val darkContainer: Color, val darkNavigation: Color,
    val darkPrimary: Color = Color(0xFFB7F2D5), val darkInk: Color = Color(0xFFEAF5EF)
)

object CampusPalettes {
    val Kangle = CampusPalette(Color(0xFF176B52),Color(0xFFF3F7F4),Color(0xFF172D26),Color(0xFFD8EBE1),Color(0xFFEAF1EC),Color(0xFF14251E),Color(0xFF244C3C),Color(0xFF1C3026))
    val Zhuhai = CampusPalette(Color(0xFF126B91),Color(0xFFF1F8FB),Color(0xFF102B38),Color(0xFFD7ECF6),Color(0xFFE5F2F8),Color(0xFF10242D),Color(0xFF1D4659),Color(0xFF183541),Color(0xFF9DDBF4))
    val Spring = CampusPalette(Color(0xFF35804C),Color(0xFFF4F8F1),Color(0xFF1E3022),Color(0xFFDDEFD8),Color(0xFFEDF4E9),Color(0xFF17271B),Color(0xFF2B5133),Color(0xFF203825),Color(0xFFAFE5B8))
    val Summer = CampusPalette(Color(0xFF217F9E),Color(0xFFF1F9FB),Color(0xFF153039),Color(0xFFD5EEF4),Color(0xFFE5F5F8),Color(0xFF12272E),Color(0xFF245364),Color(0xFF193A45),Color(0xFF9DDEF0))
    val Autumn = CampusPalette(Color(0xFF986120),Color(0xFFFFF8ED),Color(0xFF382716),Color(0xFFF5E2C5),Color(0xFFF8ECDA),Color(0xFF2A2118),Color(0xFF5C4225),Color(0xFF403020),Color(0xFFFFCF8A))
    val Winter = CampusPalette(Color(0xFF3C5E91),Color(0xFFF3F6FB),Color(0xFF1D293B),Color(0xFFDCE6F5),Color(0xFFE8EEF7),Color(0xFF151E2D),Color(0xFF2E466B),Color(0xFF202F48),Color(0xFFB6CFF5))
    fun resolve(mode: String): CampusPalette = when(mode) {
        "zhuhai" -> Zhuhai
        "seasonal" -> when(seasonalThemeKey()) {"spring"->Spring;"summer"->Summer;"autumn"->Autumn;else->Winter}
        else -> Kangle
    }
}

@Composable fun CampusTheme(themeMode: String = "kangle", content: @Composable () -> Unit) {
    val palette=CampusPalettes.resolve(themeMode)
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary=palette.darkPrimary, onPrimary=palette.darkBackground, primaryContainer=palette.darkContainer, onPrimaryContainer=palette.darkInk,
        secondary=palette.darkPrimary, secondaryContainer=palette.darkContainer, onSecondaryContainer=palette.darkInk,
        surfaceContainer=palette.darkNavigation, surfaceContainerHigh=palette.darkNavigation,
        background=palette.darkBackground, surface=palette.darkBackground, onSurface=palette.darkInk, onBackground=palette.darkInk)
    else lightColorScheme(
        primary=palette.primary, onPrimary=Color.White, primaryContainer=palette.lightContainer, onPrimaryContainer=palette.lightInk,
        secondary=palette.primary, secondaryContainer=palette.lightContainer, onSecondaryContainer=palette.lightInk,
        surfaceContainer=palette.lightNavigation, surfaceContainerHigh=palette.lightNavigation,
        background=palette.lightBackground, surface=palette.lightBackground, onSurface=palette.lightInk, onBackground=palette.lightInk)
    MaterialTheme(colorScheme=colors, content=content)
}
