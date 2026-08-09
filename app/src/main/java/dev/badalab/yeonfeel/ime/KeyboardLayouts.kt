package dev.badalab.yeonfeel.ime

import dev.badalab.yeonfeel.hangul.NaratgulComposer
import dev.badalab.yeonfeel.settings.EnglishLayoutType
import dev.badalab.yeonfeel.settings.KoreanLayoutType

/**
 * 키의 동작 종류. [CHAR]는 [Key.char]를 입력하고, [SPACER]는 그리지 않는 빈 자리,
 * [GHOST]는 그리지 않지만 누르면 [Key.char]가 입력되는 투명 확장 히트 영역,
 * [PAGE]는 특수문자 1/2 페이지 전환이다.
 */
enum class KeyType { CHAR, SHIFT, DELETE, SPACE, ENTER, LANG, SYMBOLS, SPACER, GHOST, PAGE }

data class Key(
    val type: KeyType,
    val label: String,
    val char: Char = ' ',
    val widthWeight: Float = 1f,
    /** 변형 팝업에서 고른 기호를 이 키에 기억한다 (천지인 우측 하단 기호 키). */
    val remember: Boolean = false,
)

enum class LayoutMode { KOREAN, ENGLISH, SYMBOLS }

object KeyboardLayouts {

    /** PAGE 키의 동작 구분 코드 (Key.char): 숫자 패드 이동 / 기호 1페이지 복귀 / 페이지 순환. */
    const val PAGE_TO_NUMPAD = '\uE010'
    const val PAGE_TO_SYMBOLS = '\uE011'
    const val PAGE_CYCLE = '\uE012'

    private val cache = HashMap<String, List<List<Key>>>()

    /** 3x4 자판(천지인·나랏글)의 기호 키: 마지막으로 사용한 기호를 기억한다. */
    var lastSymbol3x4: Char = ','
        set(value) {
            if (field != value) {
                field = value
                cache.clear()
            }
        }

    /** 스페이스바 오른쪽 기호 키의 즐겨찾기 기호. 바뀌면 캐시를 비운다. */
    var favoriteSymbol: Char = '.'
        set(value) {
            if (field != value) {
                field = value
                cache.clear()
            }
        }

    var favoriteSymbolEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                cache.clear()
            }
        }

    var leftSymbolEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                cache.clear()
            }
        }

    var leftSymbol: Char = ','
        set(value) {
            if (field != value) {
                field = value
                cache.clear()
            }
        }

    /**
     * 레이아웃 행을 돌려준다. [showLangKey]가 false면 한/영 키를 빼고
     * 스페이스바를 그만큼 넓힌다 (스페이스바 스와이프 전용 언어 변경 모드).
     */
    fun rows(
        mode: LayoutMode,
        shifted: Boolean,
        showNumberRow: Boolean,
        symbolsPage: Int = 0,
        showLangKey: Boolean = true,
        koreanLayout: KoreanLayoutType = KoreanLayoutType.DUBEOLSIK,
        shiftNumberRowSymbols: Boolean = true,
        englishLayout: EnglishLayoutType = EnglishLayoutType.QWERTY,
        compactSymbols: Boolean = false,
    ): List<List<Key>> {
        val cacheKey =
            "$mode-$shifted-$showNumberRow-$symbolsPage-$showLangKey-$koreanLayout-" +
                "$shiftNumberRowSymbols-$englishLayout-$compactSymbols"
        return cache.getOrPut(cacheKey) {
            build(
                mode, shifted, showNumberRow, symbolsPage, showLangKey, koreanLayout,
                shiftNumberRowSymbols, englishLayout, compactSymbols,
            )
        }
    }

    private fun build(
        mode: LayoutMode,
        shifted: Boolean,
        showNumberRow: Boolean,
        symbolsPage: Int,
        showLangKey: Boolean,
        koreanLayout: KoreanLayoutType,
        shiftNumberRowSymbols: Boolean,
        englishLayout: EnglishLayoutType,
        compactSymbols: Boolean,
    ): List<List<Key>> {
        if (mode == LayoutMode.SYMBOLS) {
            if (compactSymbols) return compactSymbolRows(symbolsPage)
            return if (symbolsPage == 1) {
                symbolsPage("2/2", "`~\\|{}€£¥$", "°•○●□■♤♡◇♧", "☆▪¤《》¡¿", showLangKey)
            } else {
                symbolsPage("1/2", "+×÷=/_<>[]", "!@#₩%^&*()", "-'\":;,?", showLangKey)
            }
        }
        val base = when (mode) {
            LayoutMode.KOREAN -> when (koreanLayout) {
                KoreanLayoutType.DUBEOLSIK ->
                    if (shifted) letterRows("ㅃㅉㄸㄲㅆㅛㅕㅑㅒㅖ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ", showLangKey)
                    else letterRows("ㅂㅈㄷㄱㅅㅛㅕㅑㅐㅔ", "ㅁㄴㅇㄹㅎㅗㅓㅏㅣ", "ㅋㅌㅊㅍㅠㅜㅡ", showLangKey)
                KoreanLayoutType.DANMOEUM -> danmoeumRows(showLangKey)
                // 3x4 자판은 숫자 열을 얹지 않고 그 높이만큼 키가 커진다.
                KoreanLayoutType.CHUNJIIN -> return chunjiinRows(showLangKey)
                KoreanLayoutType.NARATGUL -> return naratgulRows(showLangKey)
                KoreanLayoutType.NARATGUL_CENTER -> return naratgulCenterRows(showLangKey)
            }
            LayoutMode.ENGLISH -> when (englishLayout) {
                EnglishLayoutType.QWERTY ->
                    if (shifted) letterRows("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM", showLangKey)
                    else letterRows("qwertyuiop", "asdfghjkl", "zxcvbnm", showLangKey)
                EnglishLayoutType.DVORAK -> dvorakRows(shifted, showLangKey)
            }
            LayoutMode.SYMBOLS -> error("unreachable")
        }
        if (!showNumberRow) return base
        // Shift 상태에서는 숫자 열이 PC 자판 기호로 바뀐다 (옵션).
        val topRow = if (shifted && shiftNumberRowSymbols) shiftedNumberRow else numberRow
        return listOf(topRow) + base
    }

    /**
     * 단모음 자판 (10키 단모음 배열):
     * 복모음 키를 빼고 같은 모음·자음 연타로 입력한다 (ㅏㅏ→ㅑ, ㄱㄱ→ㄲ, ㅐㅐ→ㅒ).
     * Shift 키가 없으며 셋째 열 왼쪽은 빈 칸이다.
     */
    private fun danmoeumRows(showLangKey: Boolean): List<List<Key>> = listOf(
        charRow("ㅂㅈㄷㄱㅅㅗㅐㅔ"),
        charRow("ㅁㄴㅇㄹㅎㅓㅏㅣ"),
        buildList {
            // 셋째 열은 윗열 격자보다 살짝 왼쪽(ㅁ 방향)으로 어긋난 배열이 통용된다.
            // 빈 공간 오른쪽 절반은 ㅋ의 투명 확장 히트 영역으로 쓴다.
            add(spacer(0.35f))
            add(Key(KeyType.GHOST, "ㅋ", 'ㅋ', widthWeight = 0.5f))
            addAll(charRow("ㅋㅌㅊㅍㅜㅡ"))
            add(Key(KeyType.DELETE, "⌫", widthWeight = 1.15f))
        },
        bottomRow(showLangKey),
    )

    private val numberRow = charRow("1234567890")
    private val shiftedNumberRow = charRow("!@#$%^&*()")

    private fun charRow(chars: String): List<Key> =
        chars.map { Key(KeyType.CHAR, it.toString(), it) }

    private fun spacer(weight: Float) = Key(KeyType.SPACER, "", widthWeight = weight)

    /** 기호 키를 끄면 그 폭만큼 스페이스바가 늘어난다 (한/영 키는 원래 크기 유지). */
    private fun bottomRow(showLangKey: Boolean): List<Key> = buildList {
        add(Key(KeyType.SYMBOLS, "?123", widthWeight = 1.3f))
        if (showLangKey) add(Key(KeyType.LANG, "한/영"))
        if (leftSymbolEnabled) add(Key(KeyType.CHAR, leftSymbol.toString(), leftSymbol))
        val spaceWeight = (if (showLangKey) 5f else 6f) -
            (if (leftSymbolEnabled) 1f else 0f) +
            (if (favoriteSymbolEnabled) 0f else 1f)
        add(Key(KeyType.SPACE, "", ' ', widthWeight = spaceWeight))
        if (favoriteSymbolEnabled) add(Key(KeyType.CHAR, favoriteSymbol.toString(), favoriteSymbol))
        add(Key(KeyType.ENTER, "⏎", widthWeight = 1.7f))
    }

    /**
     * 글쇠 폭을 균일하게 유지한다: 10키 열이 기준(1.0)이고,
     * 9키인 둘째 열은 양옆 반 칸 스페이서로 중앙 정렬한다 (통용 배치).
     */
    private fun letterRows(r1: String, r2: String, r3: String, showLangKey: Boolean): List<List<Key>> =
        listOf(
            charRow(r1),
            buildList {
                add(spacer(0.5f))
                addAll(charRow(r2))
                add(spacer(0.5f))
            },
            buildList {
                add(Key(KeyType.SHIFT, "⇧", widthWeight = 1.5f))
                addAll(charRow(r3))
                add(Key(KeyType.DELETE, "⌫", widthWeight = 1.5f))
            },
            bottomRow(showLangKey),
        )

    /**
     * 드보락. 위 두 열이 10키씩이고 셋째 열에 글자 9개가 오므로
     * Shift·백스페이스는 1칸 폭으로 줄여 전체 균형을 맞춘다.
     * Shift에서 어포스트로피는 실제 드보락처럼 따옴표(")가 된다.
     */
    private fun dvorakRows(shifted: Boolean, showLangKey: Boolean): List<List<Key>> {
        val r1 = if (shifted) "\",.PYFGCRL" else "',.pyfgcrl"
        val r2 = if (shifted) "AOEUIDHTNS" else "aoeuidhtns"
        val r3 = if (shifted) "QJKXBMWVZ" else "qjkxbmwvz"
        return listOf(
            charRow(r1),
            charRow(r2),
            buildList {
                add(Key(KeyType.SHIFT, "⇧"))
                addAll(charRow(r3))
                add(Key(KeyType.DELETE, "⌫"))
            },
            bottomRow(showLangKey),
        )
    }

    /**
     * 천지인 (국가표준 3x4 배치): 글자 3열 + 오른쪽 기능 열의 4x4 그리드.
     * 자음 키는 연타 사이클, 모음은 ㅣㆍㅡ 조합. 글자 키 길게 누르면 우상단 숫자 입력.
     */
    private fun chunjiinRows(showLangKey: Boolean): List<List<Key>> = listOf(
        listOf(
            Key(KeyType.CHAR, "ㅣ", 'ㅣ'),
            Key(KeyType.CHAR, "ㆍ", 'ㆍ'),
            Key(KeyType.CHAR, "ㅡ", 'ㅡ'),
            Key(KeyType.DELETE, "⌫"),
        ),
        listOf(
            Key(KeyType.CHAR, "ㄱㅋ", 'ㄱ'),
            Key(KeyType.CHAR, "ㄴㄹ", 'ㄴ'),
            Key(KeyType.CHAR, "ㄷㅌ", 'ㄷ'),
            Key(KeyType.ENTER, "⏎"),
        ),
        listOf(
            Key(KeyType.CHAR, "ㅂㅍ", 'ㅂ'),
            Key(KeyType.CHAR, "ㅅㅎ", 'ㅅ'),
            Key(KeyType.CHAR, "ㅈㅊ", 'ㅈ'),
            Key(KeyType.CHAR, ".,?!", '.'),
        ),
        buildList {
            if (showLangKey) {
                add(Key(KeyType.SYMBOLS, "!#1", widthWeight = 0.5f))
                add(Key(KeyType.LANG, "한/영", widthWeight = 0.5f))
            } else {
                add(Key(KeyType.SYMBOLS, "!#1"))
            }
            add(Key(KeyType.CHAR, "ㅇㅁ", 'ㅇ'))
            add(Key(KeyType.SPACE, "", ' '))
            add(Key(KeyType.CHAR, lastSymbol3x4.toString(), lastSymbol3x4, remember = true))
        },
    )

    /**
     * 나랏글 (국가표준 3x4 배치): 글자 3열 + 오른쪽 기능 열의 4x4 그리드.
     * 하단 열 없이 기능 키(⌫·스페이스바·엔터·기호·한/영)가 그리드에 통합된다.
     * 글자 키를 길게 누르면 우상단 숫자가 입력된다 (KeyboardView에서 처리).
     */
    private fun naratgulRows(showLangKey: Boolean): List<List<Key>> = listOf(
        listOf(
            Key(KeyType.CHAR, "ㄱ", 'ㄱ'),
            Key(KeyType.CHAR, "ㄴ", 'ㄴ'),
            Key(KeyType.CHAR, "ㅏㅓ", 'ㅏ'),
            Key(KeyType.DELETE, "⌫"),
        ),
        listOf(
            Key(KeyType.CHAR, "ㄹ", 'ㄹ'),
            Key(KeyType.CHAR, "ㅁ", 'ㅁ'),
            Key(KeyType.CHAR, "ㅗㅜ", 'ㅗ'),
            Key(KeyType.SPACE, "", ' '),
        ),
        listOf(
            Key(KeyType.CHAR, "ㅅ", 'ㅅ'),
            Key(KeyType.CHAR, "ㅇ", 'ㅇ'),
            Key(KeyType.CHAR, "ㅣ", 'ㅣ'),
            Key(KeyType.CHAR, lastSymbol3x4.toString(), lastSymbol3x4, widthWeight = 0.45f, remember = true),
            Key(KeyType.ENTER, "⏎", widthWeight = 0.55f),
        ),
        buildList {
            add(Key(KeyType.CHAR, "획추가", NaratgulComposer.KEY_ADD_STROKE))
            add(Key(KeyType.CHAR, "ㅡ", 'ㅡ'))
            add(Key(KeyType.CHAR, "쌍자음", NaratgulComposer.KEY_DOUBLE))
            if (showLangKey) {
                add(Key(KeyType.SYMBOLS, "!#1", widthWeight = 0.45f))
                add(Key(KeyType.LANG, "한/영", widthWeight = 0.55f))
            } else {
                add(Key(KeyType.SYMBOLS, "!#1"))
            }
        },
    )

    /**
     * 나랏글 중앙 배치: 글자 3열을 가운데 두고
     * 왼쪽에 문장부호·한/영·기호, 오른쪽에 ⌫·스페이스바·엔터·마침표를 둔다.
     */
    private fun naratgulCenterRows(showLangKey: Boolean): List<List<Key>> {
        val side = 0.75f
        return listOf(
            listOf(
                Key(KeyType.CHAR, "?!", '?', widthWeight = side),
                Key(KeyType.CHAR, "ㄱ", 'ㄱ'),
                Key(KeyType.CHAR, "ㄴ", 'ㄴ'),
                Key(KeyType.CHAR, "ㅏㅓ", 'ㅏ'),
                Key(KeyType.DELETE, "⌫", widthWeight = side),
            ),
            listOf(
                Key(KeyType.CHAR, ",", ',', widthWeight = side),
                Key(KeyType.CHAR, "ㄹ", 'ㄹ'),
                Key(KeyType.CHAR, "ㅁ", 'ㅁ'),
                Key(KeyType.CHAR, "ㅗㅜ", 'ㅗ'),
                Key(KeyType.SPACE, "", ' ', widthWeight = side),
            ),
            listOf(
                if (showLangKey) {
                    Key(KeyType.LANG, "한/영", widthWeight = side)
                } else {
                    Key(KeyType.CHAR, "!", '!', widthWeight = side)
                },
                Key(KeyType.CHAR, "ㅅ", 'ㅅ'),
                Key(KeyType.CHAR, "ㅇ", 'ㅇ'),
                Key(KeyType.CHAR, "ㅣ", 'ㅣ'),
                Key(KeyType.ENTER, "⏎", widthWeight = side),
            ),
            listOf(
                Key(KeyType.SYMBOLS, "!#1", widthWeight = side),
                Key(KeyType.CHAR, "획추가", NaratgulComposer.KEY_ADD_STROKE),
                Key(KeyType.CHAR, "ㅡ", 'ㅡ'),
                Key(KeyType.CHAR, "쌍자음", NaratgulComposer.KEY_DOUBLE),
                Key(KeyType.CHAR, lastSymbol3x4.toString(), lastSymbol3x4, widthWeight = side, remember = true),
            ),
        )
    }

    /**
     * 3x4 자판용 컴팩트 기호 키보드 (나랏글 계열 통용 기호 배치):
     * 기호 3페이지(1/3~3/3) + 숫자 패드(page 3).
     */
    private fun compactSymbolRows(page: Int): List<List<Key>> {
        if (page == 3) return compactNumberRows()
        val pages = listOf(
            Triple("!?.,()", "@:;/-♡", "*_%~^#"),
            Triple("+×÷=<>", "[]{}\"'", "₩$€£¥`"),
            Triple("○●□■☆★", "♤♡◇♧•°", "《》¡¿¤▪"),
        )
        val (r1, r2, r3) = pages[page.coerceIn(0, 2)]
        return listOf(
            charRow(r1) + Key(KeyType.DELETE, "⌫"),
            charRow(r2) + Key(KeyType.ENTER, "⏎"),
            charRow(r3) + Key(KeyType.CHAR, ".,?!", '.'),
            listOf(
                Key(KeyType.PAGE, "123", PAGE_TO_NUMPAD),
                Key(KeyType.SYMBOLS, "가"),
                Key(KeyType.PAGE, "${page.coerceIn(0, 2) + 1}/3", PAGE_CYCLE, widthWeight = 2f),
                Key(KeyType.SPACE, "", ' ', widthWeight = 2f),
                Key(KeyType.CHAR, ",", ','),
            ),
        )
    }

    private fun compactNumberRows(): List<List<Key>> = listOf(
        charRow("123") + Key(KeyType.DELETE, "⌫"),
        charRow("456") + Key(KeyType.ENTER, "⏎"),
        charRow("789") + Key(KeyType.CHAR, ".,-/", '.'),
        listOf(
            Key(KeyType.PAGE, "!@#", PAGE_TO_SYMBOLS, widthWeight = 0.5f),
            Key(KeyType.SYMBOLS, "가", widthWeight = 0.5f),
            Key(KeyType.CHAR, "0", '0'),
            Key(KeyType.SPACE, "", ' '),
            Key(KeyType.CHAR, ",", ','),
        ),
    )

    /** 특수문자 하단 열: 글자 키보드 하단 열과 같은 구성, 첫 키만 '가'. */
    private fun symbolsBottomRow(showLangKey: Boolean): List<Key> = buildList {
        add(Key(KeyType.SYMBOLS, "가", widthWeight = 1.3f))
        if (showLangKey) add(Key(KeyType.LANG, "한/영"))
        if (leftSymbolEnabled) add(Key(KeyType.CHAR, leftSymbol.toString(), leftSymbol))
        val spaceWeight = (if (showLangKey) 5f else 6f) -
            (if (leftSymbolEnabled) 1f else 0f) +
            (if (favoriteSymbolEnabled) 0f else 1f)
        add(Key(KeyType.SPACE, "", ' ', widthWeight = spaceWeight))
        if (favoriteSymbolEnabled) add(Key(KeyType.CHAR, favoriteSymbol.toString(), favoriteSymbol))
        add(Key(KeyType.ENTER, "⏎", widthWeight = 1.7f))
    }

    private fun symbolsPage(
        pageLabel: String,
        r2: String,
        r3: String,
        r4: String,
        showLangKey: Boolean,
    ): List<List<Key>> =
        listOf(
            charRow("1234567890"),
            charRow(r2),
            charRow(r3),
            buildList {
                add(Key(KeyType.PAGE, pageLabel, widthWeight = 1.5f))
                addAll(charRow(r4))
                add(Key(KeyType.DELETE, "⌫", widthWeight = 1.5f))
            },
            symbolsBottomRow(showLangKey),
        )
}
