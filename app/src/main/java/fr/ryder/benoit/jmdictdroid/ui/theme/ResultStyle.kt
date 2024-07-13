package fr.ryder.benoit.jmdictdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


val resultJapLight = Color(0xFF00008B)
val resultSenseNumLight = Color(0xFF023020)
val resultSensePosLight = Color(0xFF8B0000)
val resultSenseAttrLight = Color(0xFF800080)
val resultGlossTypeLight = Color(0xFFA0A0A0)

val resultJapDark = Color(0xFFB3B3FF)
val resultSenseNumDark = Color(0xFF6CF9C8)
val resultSensePosDark = Color(0xFFFF9999)
val resultSenseAttrDark = Color(0xFFFF66FF)
val resultGlossTypeDark = Color(0xFFBFBFBF)


class ResultColors(
    val jap: Color,
    val senseNum: Color,
    val sensePos: Color,
    val senseAttr: Color,
    val glossType: Color,
)

val resultStyleLight = ResultColors(
    jap = resultJapLight,
    senseNum = resultSenseNumLight,
    sensePos = resultSensePosLight,
    senseAttr = resultSenseAttrLight,
    glossType = resultGlossTypeLight,
)

val resultStyleDark = ResultColors(
    jap = resultJapDark,
    senseNum = resultSenseNumDark,
    sensePos = resultSensePosDark,
    senseAttr = resultSenseAttrDark,
    glossType = resultGlossTypeDark,
)

@Composable
fun themeResultColors(): ResultColors {
    if (isSystemInDarkTheme()) {
        return resultStyleDark
    } else {
        return resultStyleLight
    }
}

