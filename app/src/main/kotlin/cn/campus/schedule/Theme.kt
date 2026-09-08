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
@Composable fun CampusTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary=Palette.Mint, onPrimary=Palette.Night, primaryContainer=Palette.DarkContainer, onPrimaryContainer=Palette.LightInk,
        secondary=Palette.Mint, secondaryContainer=Palette.DarkContainer, onSecondaryContainer=Palette.LightInk,
        surfaceContainer=Palette.DarkNavigation, surfaceContainerHigh=Palette.DarkNavigation,
        background=Palette.Night, surface=Palette.Night, onSurface=Palette.LightInk, onBackground=Palette.LightInk)
    else lightColorScheme(
        primary=Palette.Green, onPrimary=Color.White, primaryContainer=Palette.Container, onPrimaryContainer=Palette.Ink,
        secondary=Palette.Green, secondaryContainer=Palette.Container, onSecondaryContainer=Palette.Ink,
        surfaceContainer=Palette.Navigation, surfaceContainerHigh=Palette.Navigation,
        background=Palette.Paper, surface=Palette.Paper, onSurface=Palette.Ink, onBackground=Palette.Ink)
    MaterialTheme(colorScheme=colors, content=content)
}
