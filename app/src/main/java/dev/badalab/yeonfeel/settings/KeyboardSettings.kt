package dev.badalab.yeonfeel.settings

import android.content.Context
import android.content.SharedPreferences

enum class BackspaceSpeed(val intervalMs: Long) {
    SLOW(80),
    NORMAL(50),
    FAST(30),
}

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
}

enum class HighContrastStyle {
    DEFAULT,

    YELLOW_BLACK,

    BLACK_WHITE,

    BLACK_YELLOW,
}

/** 기호·숫자 키보드의 표시 형식. */
enum class SymbolBoardStyle {
    /** 현재 입력 언어의 자판 형태를 따른다 (3x4 한글 자판이면 3x4 기호판). */
    AUTO,

    QWERTY,

    GRID_3X4,
}

enum class LanguageSwitchMethod {
    BUTTON,

    BUTTON_AND_SWIPE,

    /** 스페이스바 좌우 스와이프로만 변경 (한/영 버튼 숨김) */
    SWIPE,
}

enum class KoreanLayoutType {
    /** 표준 두벌식 (KS X 5002) */
    DUBEOLSIK,

    /** 단모음: 두벌식에서 복모음 키를 빼고 모음 연타로 입력 (ㅏㅏ→ㅑ) */
    DANMOEUM,

    /** 천지인: ㅣㆍㅡ 모음 조합 + 자음 연타 (2011년 특허 개방) */
    CHUNJIIN,

    /** 나랏글: 획추가·쌍자음 변형 (2011년 특허 개방, KT) */
    NARATGUL,

    /** 나랏글 중앙: 글자 열을 가운데 두고 기능 키를 양옆에 배치 */
    NARATGUL_CENTER,
}

enum class EnglishLayoutType {
    QWERTY,

    /** 드보락: 모음·빈도 높은 자음을 홈 행에 배치한 배열 */
    DVORAK,
}

/** 한 손 키보드 모드: 키 영역을 한쪽으로 몰아 좁힌다. */
enum class OneHandedMode {
    OFF,
    RIGHT,
    LEFT,
}

/** 키보드 글자 크기 (라벨 스케일). */
enum class KeyFontSize(val scale: Float) {
    SMALL(0.85f),
    NORMAL(1f),
    LARGE(1.15f),
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

    var showToolbar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_TOOLBAR, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_TOOLBAR, value).apply()

    /** 툴바 아이콘 순서 (쉼표 구분 id). 아이콘을 길게 눌러 드래그하면 바뀐다. */
    var toolbarOrder: String
        get() = prefs.getString(KEY_TOOLBAR_ORDER, TOOLBAR_ORDER_DEFAULT) ?: TOOLBAR_ORDER_DEFAULT
        set(value) = prefs.edit().putString(KEY_TOOLBAR_ORDER, value).apply()

    var highContrast: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    var highContrastStyle: HighContrastStyle
        get() = runCatching {
            HighContrastStyle.valueOf(prefs.getString(KEY_HIGH_CONTRAST_STYLE, null) ?: "")
        }.getOrDefault(HighContrastStyle.DEFAULT)
        set(value) = prefs.edit().putString(KEY_HIGH_CONTRAST_STYLE, value.name).apply()

    /** 고대비 모드에서 테마의 키캡 배경 설정과 무관하게 키캡을 항상 표시할지. */
    var highContrastForceKeycap: Boolean
        get() = prefs.getBoolean(KEY_HC_FORCE_KEYCAP, true)
        set(value) = prefs.edit().putBoolean(KEY_HC_FORCE_KEYCAP, value).apply()

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

    var favoriteSymbolEnabled: Boolean
        get() = prefs.getBoolean(KEY_FAVORITE_SYMBOL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_FAVORITE_SYMBOL_ENABLED, value).apply()

    var favoriteSymbol: String
        get() = prefs.getString(KEY_FAVORITE_SYMBOL, ".")?.takeIf { it.isNotEmpty() } ?: "."
        set(value) = prefs.edit().putString(KEY_FAVORITE_SYMBOL, value.take(1)).apply()

    var leftSymbolEnabled: Boolean
        get() = prefs.getBoolean(KEY_LEFT_SYMBOL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LEFT_SYMBOL_ENABLED, value).apply()

    var leftSymbol: String
        get() = prefs.getString(KEY_LEFT_SYMBOL, ",")?.takeIf { it.isNotEmpty() } ?: ","
        set(value) = prefs.edit().putString(KEY_LEFT_SYMBOL, value.take(1)).apply()

    /** 같은 키 연타 판정 시간(ms). 단모음 쌍자음·천지인·나랏글 연타에 공통 적용. */
    var multiTapDelayMs: Int
        get() = prefs.getInt(KEY_MULTI_TAP_DELAY, MULTI_TAP_DELAY_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_MULTI_TAP_DELAY, value.coerceIn(MULTI_TAP_DELAY_MIN, MULTI_TAP_DELAY_MAX)).apply()

    /** 길게 누르기 판정 시간(ms). 변형 팝업·숫자 입력·언어 목록 등 롱프레스 전반에 적용. */
    var longPressDelayMs: Int
        get() = prefs.getInt(KEY_LONG_PRESS_DELAY, LONG_PRESS_DELAY_DEFAULT)
        set(value) = prefs.edit()
            .putInt(KEY_LONG_PRESS_DELAY, value.coerceIn(LONG_PRESS_DELAY_MIN, LONG_PRESS_DELAY_MAX))
            .apply()

    /** 영문 문장 시작에서 자동으로 Shift를 켠다. */
    var autoCapitalize: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CAPITALIZE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CAPITALIZE, value).apply()

    /** 스페이스바를 빠르게 두 번 누르면 마침표+공백을 입력한다. */
    var doubleSpacePeriod: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE_PERIOD, true)
        set(value) = prefs.edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, value).apply()

    /** MZ 모드 (유머): ㅋ 연타에 ㅎ을 랜덤으로 섞는다. */
    var mzModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MZ_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_MZ_MODE, value).apply()

    /** 타점 수집 여부 (디버그 — 오타 보정 기초 데이터). */
    var touchStatsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_STATS, true)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_STATS, value).apply()

    var keyPreviewEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEY_PREVIEW, true)
        set(value) = prefs.edit().putBoolean(KEY_KEY_PREVIEW, value).apply()

    var backspaceSpeed: BackspaceSpeed
        get() = runCatching {
            BackspaceSpeed.valueOf(prefs.getString(KEY_BACKSPACE_SPEED, null) ?: "")
        }.getOrDefault(BackspaceSpeed.NORMAL)
        set(value) = prefs.edit().putString(KEY_BACKSPACE_SPEED, value.name).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

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

    /** 천지인 우측 하단 기호 키가 기억하는 마지막 사용 기호. */
    var rememberedSymbol: String
        get() = prefs.getString(KEY_REMEMBERED_SYMBOL, ",")?.takeIf { it.isNotEmpty() } ?: ","
        set(value) = prefs.edit().putString(KEY_REMEMBERED_SYMBOL, value.take(1)).apply()

    /** 천지인: 조합 중 첫 스페이스바가 띄어쓰기 대신 조합만 끊을지 (통용 관습). */
    var chunjiinSpaceCommits: Boolean
        get() = prefs.getBoolean(KEY_CHUNJIIN_SPACE_COMMITS, true)
        set(value) = prefs.edit().putBoolean(KEY_CHUNJIIN_SPACE_COMMITS, value).apply()

    /** 설정 화면에서 여백 조정 진입 요청 (1회성 — 키보드가 열리며 소비). */
    var adjustModeRequested: Boolean
        get() = prefs.getBoolean(KEY_ADJUST_REQUESTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADJUST_REQUESTED, value).apply()

    var oneHandedMode: OneHandedMode
        get() = runCatching {
            OneHandedMode.valueOf(prefs.getString(KEY_ONE_HANDED, null) ?: "")
        }.getOrDefault(OneHandedMode.OFF)
        set(value) = prefs.edit().putString(KEY_ONE_HANDED, value.name).apply()

    /** 이모지 기본 스킨톤 (0 = 기본, 1~5 = 밝은 → 어두운). */
    var skinTone: Int
        get() = prefs.getInt(KEY_SKIN_TONE, 0)
        set(value) = prefs.edit().putInt(KEY_SKIN_TONE, value.coerceIn(0, 5)).apply()

    /** 타점 개인화 보정 (실험): 경계 근처 탭을 사용자의 타점 분포로 재판정. */
    var touchCorrectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_CORRECTION, true)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_CORRECTION, value).apply()

    /** 1단계 기본 보정(타점 분포 재판정) 사용 여부. 마스터 스위치와 AND로 동작. */
    var touchCorrectionBasic: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_CORRECTION_BASIC, true)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_CORRECTION_BASIC, value).apply()

    /** 2단계 AI 보정(어절 단위 노이지 채널) 사용 여부. 마스터 스위치와 AND로 동작. */
    var touchCorrectionAi: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_CORRECTION_AI, false)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_CORRECTION_AI, value).apply()

    /** 실험실: '됬'을 입력하면 무조건 '됐'으로 고친다. */
    var dwaetFixEnabled: Boolean
        get() = prefs.getBoolean(KEY_DWAET_FIX, false)
        set(value) = prefs.edit().putBoolean(KEY_DWAET_FIX, value).apply()

    /** 터미널 도구 줄 (esc·tab·ctrl·alt·화살표) 표시 여부. */
    var terminalRowEnabled: Boolean
        get() = prefs.getBoolean(KEY_TERMINAL_ROW, false)
        set(value) = prefs.edit().putBoolean(KEY_TERMINAL_ROW, value).apply()

    /** 노친네 모드 (유머): ㅋ 연타에 ㄱ을 랜덤으로 섞는다. */
    var oldieModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_OLDIE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_OLDIE_MODE, value).apply()

    /** 가로 화면에서 분할 키보드 사용. */
    var splitLandscape: Boolean
        get() = prefs.getBoolean(KEY_SPLIT_LANDSCAPE, false)
        set(value) = prefs.edit().putBoolean(KEY_SPLIT_LANDSCAPE, value).apply()

    /** 세로 화면에서 분할 키보드 사용 (폴더블·태블릿 대화면 전용). */
    var splitPortrait: Boolean
        get() = prefs.getBoolean(KEY_SPLIT_PORTRAIT, false)
        set(value) = prefs.edit().putBoolean(KEY_SPLIT_PORTRAIT, value).apply()

    /** 분할 키보드 중앙 간격 (전체 폭 가중치 대비 %, 10~120). */
    var splitGapPercent: Int
        get() = prefs.getInt(KEY_SPLIT_GAP, SPLIT_GAP_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_SPLIT_GAP, value.coerceIn(SPLIT_GAP_MIN, SPLIT_GAP_MAX)).apply()

    var keyFontSize: KeyFontSize
        get() = runCatching {
            KeyFontSize.valueOf(prefs.getString(KEY_FONT_SIZE, null) ?: "")
        }.getOrDefault(KeyFontSize.NORMAL)
        set(value) = prefs.edit().putString(KEY_FONT_SIZE, value.name).apply()

    var englishLayout: EnglishLayoutType
        get() = runCatching {
            EnglishLayoutType.valueOf(prefs.getString(KEY_ENGLISH_LAYOUT, null) ?: "")
        }.getOrDefault(EnglishLayoutType.QWERTY)
        set(value) = prefs.edit().putString(KEY_ENGLISH_LAYOUT, value.name).apply()

    var symbolBoardStyle: SymbolBoardStyle
        get() = runCatching {
            SymbolBoardStyle.valueOf(prefs.getString(KEY_SYMBOL_BOARD_STYLE, null) ?: "")
        }.getOrDefault(SymbolBoardStyle.AUTO)
        set(value) = prefs.edit().putString(KEY_SYMBOL_BOARD_STYLE, value.name).apply()

    var languageSwitchMethod: LanguageSwitchMethod
        get() = runCatching {
            LanguageSwitchMethod.valueOf(prefs.getString(KEY_SWITCH_METHOD, null) ?: "")
        }.getOrDefault(LanguageSwitchMethod.BUTTON)
        set(value) = prefs.edit().putString(KEY_SWITCH_METHOD, value.name).apply()

    /** 키 영역 높이(dp). 상하 핸들이 가장자리 고정 방식이라 높이 자체가 조절된다. */
    var keyboardHeightDp: Int
        get() = prefs.getInt(KEY_KEYBOARD_HEIGHT, HEIGHT_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_KEYBOARD_HEIGHT, value).apply()

    fun resetAll() = prefs.edit().clear().apply()

    companion object {
        const val SPLIT_GAP_DEFAULT = 45
        const val SPLIT_GAP_MIN = 10
        const val SPLIT_GAP_MAX = 120
        const val MARGIN_TOP_MAX = 60
        const val MARGIN_BOTTOM_MAX = 100
        const val MARGIN_BOTTOM_DEFAULT = 12
        const val MULTI_TAP_DELAY_MIN = 100
        const val MULTI_TAP_DELAY_MAX = 600
        const val MULTI_TAP_DELAY_DEFAULT = 300
        const val LONG_PRESS_DELAY_MIN = 100
        const val LONG_PRESS_DELAY_MAX = 700
        const val LONG_PRESS_DELAY_DEFAULT = 350
        const val MARGIN_SIDE_MAX = 120
        const val HEIGHT_MIN = 160
        const val HEIGHT_DEFAULT = 240
        const val TOOLBAR_ORDER_DEFAULT = "settings,layout,clipboard,emoji,kaomoji,onehand"

        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_SHOW_TOOLBAR = "show_toolbar"
        private const val KEY_TOOLBAR_ORDER = "toolbar_order"
        private const val KEY_HIGH_CONTRAST = "high_contrast"
        private const val KEY_SHOW_KEY_BACKGROUND = "show_key_background"
        private const val KEY_HIGH_CONTRAST_STYLE = "high_contrast_style"
        private const val KEY_HC_FORCE_KEYCAP = "high_contrast_force_keycap"
        private const val KEY_SHOW_NUMBER_ROW = "show_number_row"
        private const val KEY_KOREAN_ENABLED = "korean_enabled"
        private const val KEY_ENGLISH_ENABLED = "english_enabled"
        private const val KEY_SWITCH_METHOD = "language_switch_method"
        private const val KEY_SYMBOL_BOARD_STYLE = "symbol_board_style"
        private const val KEY_KOREAN_LAYOUT = "korean_layout"
        private const val KEY_ENGLISH_LAYOUT = "english_layout"
        private const val KEY_FONT_SIZE = "key_font_size"
        private const val KEY_REMEMBERED_SYMBOL = "remembered_symbol_3x4"
        private const val KEY_CHUNJIIN_SPACE_COMMITS = "chunjiin_space_commits"
        private const val KEY_ADJUST_REQUESTED = "adjust_mode_requested"
        private const val KEY_ONE_HANDED = "one_handed_mode"
        private const val KEY_SKIN_TONE = "emoji_skin_tone"
        private const val KEY_TOUCH_CORRECTION = "touch_correction"
        private const val KEY_TOUCH_CORRECTION_BASIC = "touch_correction_basic"
        private const val KEY_TOUCH_CORRECTION_AI = "touch_correction_ai"
        private const val KEY_DWAET_FIX = "dwaet_fix"
        private const val KEY_TERMINAL_ROW = "terminal_row"
        private const val KEY_OLDIE_MODE = "oldie_mode"
        private const val KEY_SPLIT_LANDSCAPE = "split_landscape"
        private const val KEY_SPLIT_PORTRAIT = "split_portrait"
        private const val KEY_SPLIT_GAP = "split_gap_percent"
        private const val KEY_SHIFT_NUMBER_SYMBOLS = "shift_number_row_symbols"
        private const val KEY_FAVORITE_SYMBOL = "favorite_symbol"
        private const val KEY_FAVORITE_SYMBOL_ENABLED = "favorite_symbol_enabled"
        private const val KEY_LEFT_SYMBOL_ENABLED = "left_symbol_enabled"
        private const val KEY_LEFT_SYMBOL = "left_symbol"
        private const val KEY_AUTO_CAPITALIZE = "auto_capitalize"
        private const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
        private const val KEY_MZ_MODE = "mz_mode_enabled"
        private const val KEY_TOUCH_STATS = "touch_stats_enabled"
        private const val KEY_MULTI_TAP_DELAY = "multi_tap_delay_ms"
        private const val KEY_LONG_PRESS_DELAY = "long_press_delay_ms"
        private const val KEY_KEY_PREVIEW = "key_preview_enabled"
        private const val KEY_BACKSPACE_SPEED = "backspace_speed"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        private const val KEY_HAPTIC_STRENGTH = "haptic_strength"
        private const val KEY_MARGIN_TOP = "margin_top_dp"
        private const val KEY_MARGIN_BOTTOM = "margin_bottom_dp"
        private const val KEY_MARGIN_SIDE = "margin_side_dp"
        private const val KEY_KEYBOARD_HEIGHT = "keyboard_height_dp"
    }
}
