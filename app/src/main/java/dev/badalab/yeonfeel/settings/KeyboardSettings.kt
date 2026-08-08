package dev.badalab.yeonfeel.settings

import android.content.Context
import android.content.SharedPreferences

/** 테마 모드. */
enum class ThemeMode {
    /** 시스템 다크 모드 설정을 따른다 */
    SYSTEM,
    DARK,
    LIGHT,
}

/** 언어 변경 방법. */
enum class LanguageSwitchMethod {
    /** 한/영 버튼으로만 변경 */
    BUTTON,

    /** 한/영 버튼 + 스페이스바 좌우 스와이프 */
    BUTTON_AND_SWIPE,

    /** 스페이스바 좌우 스와이프로만 변경 (한/영 버튼 숨김) */
    SWIPE,
}

/** 한국어 자판 종류. */
enum class KoreanLayoutType {
    /** 표준 두벌식 (KS X 5002) */
    DUBEOLSIK,

    /** 단모음: 두벌식에서 복모음 키를 빼고 모음 연타로 입력 (ㅏㅏ→ㅑ) */
    DANMOEUM,

    /** 세벌식 390: 초성·중성·종성 분리 입력 */
    SEBEOLSIK_390,

    /** 천지인: ㅣㆍㅡ 모음 조합 + 자음 연타 (2011년 특허 개방) */
    CHUNJIIN,

    /** 나랏글: 획추가·쌍자음 변형 (2011년 특허 개방, KT) */
    NARATGUL,
}

/** 키보드 사용자 설정. IME 서비스와 설정 화면이 공유한다. */
class KeyboardSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("keyboard_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching {
            ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "")
        }.getOrDefault(ThemeMode.LIGHT)
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.name).apply()

    /** 키보드 상단 툴바 표시 여부. */
    var showToolbar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOOLBAR, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TOOLBAR, value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    /** 키캡 배경 표시 여부. 끄면 글자만 보이는 플랫 스타일. */
    var showKeyBackground: Boolean
        get() = prefs.getBoolean(KEY_SHOW_KEY_BACKGROUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_KEY_BACKGROUND, value).apply()

    /** 여백(dp). 툴바의 레이아웃 메뉴에서 화살표 핸들로 조정한다. 좌우는 중앙 정렬 연동. */
    var marginTopDp: Int
        get() = prefs.getInt(KEY_MARGIN_TOP, 0)
        set(value) = prefs.edit().putInt(KEY_MARGIN_TOP, value).apply()

    /** 하단 여백 기본값은 제스처 내비게이션 영역과 겹치지 않도록 살짝 띄운다. */
    var marginBottomDp: Int
        get() = prefs.getInt(KEY_MARGIN_BOTTOM, MARGIN_BOTTOM_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_MARGIN_BOTTOM, value).apply()

    var marginSideDp: Int
        get() = prefs.getInt(KEY_MARGIN_SIDE, 0)
        set(value) = prefs.edit().putInt(KEY_MARGIN_SIDE, value).apply()

    var showNumberRow: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NUMBER_ROW, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NUMBER_ROW, value).apply()

    var koreanEnabled: Boolean
        get() = prefs.getBoolean(KEY_KOREAN_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_KOREAN_ENABLED, value).apply()

    var englishEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENGLISH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENGLISH_ENABLED, value).apply()

    /** 스페이스바 오른쪽 기호 키(즐겨찾기) 표시 여부. */
    var favoriteSymbolEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAVORITE_SYMBOL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FAVORITE_SYMBOL_ENABLED, value).apply()

    /** 스페이스바 오른쪽 기호 키에 표시할 즐겨찾기 기호 (한 글자). */
    var favoriteSymbol: String
        get() = prefs.getString(KEY_FAVORITE_SYMBOL, ".")?.takeIf { it.isNotEmpty() } ?: "."
        set(value) = prefs.edit().putString(KEY_FAVORITE_SYMBOL, value.take(1)).apply()

    /** 스페이스바 왼쪽 기호 키 표시 여부. */
    var leftSymbolEnabled: Boolean
        get() = prefs.getBoolean(KEY_LEFT_SYMBOL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LEFT_SYMBOL_ENABLED, value).apply()

    /** 스페이스바 왼쪽 기호 키에 표시할 기호 (한 글자). */
    var leftSymbol: String
        get() = prefs.getString(KEY_LEFT_SYMBOL, ",")?.takeIf { it.isNotEmpty() } ?: ","
        set(value) = prefs.edit().putString(KEY_LEFT_SYMBOL, value.take(1)).apply()

    /** 같은 키 연타 판정 시간(ms). 단모음 쌍자음·천지인·나랏글 연타에 공통 적용. */
    var multiTapDelayMs: Int
        get() = prefs.getInt(KEY_MULTI_TAP_DELAY, MULTI_TAP_DELAY_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_MULTI_TAP_DELAY, value.coerceIn(MULTI_TAP_DELAY_MIN, MULTI_TAP_DELAY_MAX)).apply()

    /** 타점 수집 여부 (디버그 — 오타 보정 기초 데이터). */
    var touchStatsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_STATS, true)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_STATS, value).apply()

    /** 키 입력 햅틱 피드백 사용 여부. */
    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    /** 햅틱 강도 (0~100). */
    var hapticStrength: Int
        get() = prefs.getInt(KEY_HAPTIC_STRENGTH, 50)
        set(value) = prefs.edit().putInt(KEY_HAPTIC_STRENGTH, value.coerceIn(0, 100)).apply()

    /** Shift 상태에서 숫자 열을 PC 자판 기호(!@#$…)로 바꿀지. */
    var shiftNumberRowSymbols: Boolean
        get() = prefs.getBoolean(KEY_SHIFT_NUMBER_SYMBOLS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHIFT_NUMBER_SYMBOLS, value).apply()

    var koreanLayout: KoreanLayoutType
        get() = runCatching {
            KoreanLayoutType.valueOf(prefs.getString(KEY_KOREAN_LAYOUT, null) ?: "")
        }.getOrDefault(KoreanLayoutType.DUBEOLSIK)
        set(value) = prefs.edit().putString(KEY_KOREAN_LAYOUT, value.name).apply()

    var languageSwitchMethod: LanguageSwitchMethod
        get() = runCatching {
            LanguageSwitchMethod.valueOf(prefs.getString(KEY_SWITCH_METHOD, null) ?: "")
        }.getOrDefault(LanguageSwitchMethod.BUTTON)
        set(value) = prefs.edit().putString(KEY_SWITCH_METHOD, value.name).apply()

    /** 키 영역 높이(dp). 상하 핸들이 가장자리 고정 방식이라 높이 자체가 조절된다. */
    var keyboardHeightDp: Int
        get() = prefs.getInt(KEY_KEYBOARD_HEIGHT, HEIGHT_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_KEYBOARD_HEIGHT, value).apply()

    companion object {
        const val MARGIN_TOP_MAX = 60
        const val MARGIN_BOTTOM_MAX = 100
        const val MARGIN_BOTTOM_DEFAULT = 12
        const val MULTI_TAP_DELAY_MIN = 100
        const val MULTI_TAP_DELAY_MAX = 600
        const val MULTI_TAP_DELAY_DEFAULT = 300
        const val MARGIN_SIDE_MAX = 120
        const val HEIGHT_MIN = 160
        const val HEIGHT_DEFAULT = 240

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_TOOLBAR = "show_toolbar"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_SHOW_KEY_BACKGROUND = "show_key_background"
        private const val KEY_SHOW_NUMBER_ROW = "show_number_row"
        private const val KEY_KOREAN_ENABLED = "korean_enabled"
        private const val KEY_ENGLISH_ENABLED = "english_enabled"
        private const val KEY_SWITCH_METHOD = "language_switch_method"
        private const val KEY_KOREAN_LAYOUT = "korean_layout"
        private const val KEY_SHIFT_NUMBER_SYMBOLS = "shift_number_row_symbols"
        private const val KEY_FAVORITE_SYMBOL = "favorite_symbol"
        private const val KEY_FAVORITE_SYMBOL_ENABLED = "favorite_symbol_enabled"
        private const val KEY_LEFT_SYMBOL_ENABLED = "left_symbol_enabled"
        private const val KEY_LEFT_SYMBOL = "left_symbol"
        private const val KEY_TOUCH_STATS = "touch_stats_enabled"
        private const val KEY_MULTI_TAP_DELAY = "multi_tap_delay_ms"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        private const val KEY_MARGIN_TOP = "margin_top_dp"
        private const val KEY_MARGIN_BOTTOM = "margin_bottom_dp"
        private const val KEY_MARGIN_SIDE = "margin_side_dp"
        private const val KEY_KEYBOARD_HEIGHT = "keyboard_height_dp"
    }
}
