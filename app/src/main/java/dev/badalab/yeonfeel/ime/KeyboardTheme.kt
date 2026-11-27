package dev.badalab.yeonfeel.ime

/**
 * 키보드 색상 팔레트. [keyBorder]가 null이 아니면 고대비용 키 외곽선을 그린다.
 *
 * [accent]는 키 위에 그리는 강조 글자·아이콘(엔터 동작 라벨, 켜진 시프트, 무장된 ctrl/alt),
 * [selection]·[onSelection]은 팝업에서 고른 칸의 바탕과 그 위 글자다. 모두 놓이는 바탕과
 * 4.5:1 이상 대비가 나도록 테마마다 정한다. 고대비 테마는 파랑 대신 글자색을 쓴다.
 */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val pressed: Int,
    val text: Int,
    val subText: Int,
    val keyBorder: Int? = null,
    val accent: Int = text,
    val selection: Int = text,
    val onSelection: Int = background,
) {
    /** 바탕이 어두운 테마인지 — 눌림 효과처럼 바탕 위에 겹치는 색의 밝기를 정한다. */
    val isDark: Boolean
        get() {
            val r = (specialKey shr 16) and 0xFF
            val g = (specialKey shr 8) and 0xFF
            val b = specialKey and 0xFF
            return r * 299 + g * 587 + b * 114 < 128_000
        }

    companion object {
        /** 흰 글자와 5.2:1 — 일반 테마의 팝업 선택 칸 바탕. */
        private const val SELECTION_BLUE = 0xFF2563EB.toInt()

        val DARK = KeyboardTheme(
            background = 0xFF24272E.toInt(),
            key = 0xFF3A3D45.toInt(),
            specialKey = 0xFF2B2E36.toInt(),
            pressed = 0xFF5A5E6A.toInt(),
            text = 0xFFFFFFFF.toInt(),
            subText = 0xFFB9BDC7.toInt(),
            accent = 0xFF6AA6FF.toInt(),
            selection = SELECTION_BLUE,
            onSelection = 0xFFFFFFFF.toInt(),
        )

        val LIGHT = KeyboardTheme(
            background = 0xFFEDEFF2.toInt(),
            key = 0xFFFFFFFF.toInt(),
            specialKey = 0xFFC9CDD4.toInt(),
            pressed = 0xFFA9AFBA.toInt(),
            text = 0xFF1B1D22.toInt(),
            subText = 0xFF4A4E57.toInt(),
            accent = 0xFF174AB8.toInt(),
            selection = SELECTION_BLUE,
            onSelection = 0xFFFFFFFF.toInt(),
        )

        val DARK_HIGH_CONTRAST = KeyboardTheme(
            background = 0xFF000000.toInt(),
            key = 0xFF000000.toInt(),
            specialKey = 0xFF000000.toInt(),
            pressed = 0xFF666666.toInt(),
            text = 0xFFFFFFFF.toInt(),
            subText = 0xFFFFFFFF.toInt(),
            keyBorder = 0xFFFFFFFF.toInt(),
        )

        val LIGHT_HIGH_CONTRAST = KeyboardTheme(
            background = 0xFFFFFFFF.toInt(),
            key = 0xFFFFFFFF.toInt(),
            specialKey = 0xFFFFFFFF.toInt(),
            pressed = 0xFFBBBBBB.toInt(),
            text = 0xFF000000.toInt(),
            subText = 0xFF000000.toInt(),
            keyBorder = 0xFF000000.toInt(),
        )

        // 배경까지 노랑으로 통일해 키캡 배경을 꺼도 글자·툴바가 항상 보이게 한다.
        val HC_YELLOW_BLACK = KeyboardTheme(
            background = 0xFFFFD600.toInt(),
            key = 0xFFFFD600.toInt(),
            specialKey = 0xFFE0BC00.toInt(),
            pressed = 0xFFB59A00.toInt(),
            text = 0xFF000000.toInt(),
            subText = 0xFF000000.toInt(),
            keyBorder = 0xFF000000.toInt(),
        )

        val HC_BLACK_WHITE = KeyboardTheme(
            background = 0xFF000000.toInt(),
            key = 0xFF000000.toInt(),
            specialKey = 0xFF000000.toInt(),
            pressed = 0xFF666666.toInt(),
            text = 0xFFFFFFFF.toInt(),
            subText = 0xFFFFFFFF.toInt(),
            keyBorder = 0xFFFFFFFF.toInt(),
        )

        val HC_BLACK_YELLOW = KeyboardTheme(
            background = 0xFF000000.toInt(),
            key = 0xFF000000.toInt(),
            specialKey = 0xFF000000.toInt(),
            pressed = 0xFF5C4D00.toInt(),
            text = 0xFFFFD600.toInt(),
            subText = 0xFFFFD600.toInt(),
            keyBorder = 0xFFFFD600.toInt(),
        )

        fun of(
            dark: Boolean,
            highContrast: Boolean,
            style: dev.badalab.yeonfeel.settings.HighContrastStyle =
                dev.badalab.yeonfeel.settings.HighContrastStyle.DEFAULT,
        ): KeyboardTheme = when {
            highContrast -> when (style) {
                dev.badalab.yeonfeel.settings.HighContrastStyle.YELLOW_BLACK -> HC_YELLOW_BLACK
                dev.badalab.yeonfeel.settings.HighContrastStyle.BLACK_WHITE -> HC_BLACK_WHITE
                dev.badalab.yeonfeel.settings.HighContrastStyle.BLACK_YELLOW -> HC_BLACK_YELLOW
                dev.badalab.yeonfeel.settings.HighContrastStyle.DEFAULT ->
                    if (dark) DARK_HIGH_CONTRAST else LIGHT_HIGH_CONTRAST
            }
            dark -> DARK
            else -> LIGHT
        }
    }
}
