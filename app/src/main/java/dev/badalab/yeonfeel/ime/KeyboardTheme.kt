package dev.badalab.yeonfeel.ime

/** 키보드 색상 팔레트. [keyBorder]가 null이 아니면 고대비용 키 외곽선을 그린다. */
data class KeyboardTheme(
    val background: Int,
    val key: Int,
    val specialKey: Int,
    val pressed: Int,
    val text: Int,
    val subText: Int,
    val keyBorder: Int? = null,
) {
    companion object {
        val DARK = KeyboardTheme(
            background = 0xFF202329.toInt(),
            key = 0xFF3A3D45.toInt(),
            specialKey = 0xFF2B2E36.toInt(),
            pressed = 0xFF5A5E6A.toInt(),
            text = 0xFFFFFFFF.toInt(),
            subText = 0xFFB9BDC7.toInt(),
        )

        val LIGHT = KeyboardTheme(
            background = 0xFFE8EAED.toInt(),
            key = 0xFFFFFFFF.toInt(),
            specialKey = 0xFFC9CDD4.toInt(),
            pressed = 0xFFA9AFBA.toInt(),
            text = 0xFF1B1D22.toInt(),
            subText = 0xFF4A4E57.toInt(),
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

        fun of(dark: Boolean, highContrast: Boolean): KeyboardTheme = when {
            dark && highContrast -> DARK_HIGH_CONTRAST
            dark -> DARK
            highContrast -> LIGHT_HIGH_CONTRAST
            else -> LIGHT
        }
    }
}
