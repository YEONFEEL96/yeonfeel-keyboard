#!/usr/bin/env python3
"""EmojiData.kt 생성기. 유니코드 블록 범위로 이모지를 뽑고 카테고리를 구성한다.
미지원 글리프는 앱 런타임의 Paint.hasGlyph 필터가 걸러낸다."""
import sys

VS = "\ufe0f"

def cps(*specs):
    out = []
    for s in specs:
        if isinstance(s, tuple):
            out += [chr(c) for c in range(s[0], s[1] + 1)]
        else:
            out.append(chr(s))
    return out

def vs(seq):
    return [c + VS for c in seq]

SMILEYS = cps((0x1F600, 0x1F644), (0x1F910, 0x1F915), 0x1F917, (0x1F920, 0x1F92F),
              (0x1F970, 0x1F976), (0x1F978, 0x1F97A), 0x1F9D0, (0x1FAE0, 0x1FAE8),
              0x1F480, 0x1F47B, 0x1F47D, 0x1F916, 0x1F4A9) + vs(cps(0x2639, 0x263A))

PEOPLE = cps((0x1F645, 0x1F64F), (0x1F442, 0x1F445), (0x1F446, 0x1F450),
              (0x1F918, 0x1F91F), (0x1F930, 0x1F93E), (0x1FAF0, 0x1FAF8),
              0x1F485, 0x1F4AA, (0x1F9B4, 0x1F9BB), (0x1F466, 0x1F478), 0x1F47C,
              (0x1F481, 0x1F483), 0x1F486, 0x1F487, (0x1F9D1, 0x1F9DF),
              0x1F6B6, 0x1F3C3, 0x1F440, 0x1F5E3, 0x1F9E0, 0x1FAC0, 0x1FAC1,
              0x1F933, 0x1F487) + vs(cps(0x261D, 0x270C, 0x270D)) + cps(0x270A, 0x270B)

NATURE = cps((0x1F400, 0x1F43F), 0x1F54A, (0x1F980, 0x1F9AE), (0x1FAB0, 0x1FABD),
              (0x1F330, 0x1F344), (0x1F300, 0x1F32C), 0x26C4, 0x26C5, 0x26A1,
              0x1F308) + vs(cps((0x2600, 0x2604), 0x2744))

FOOD = cps((0x1F345, 0x1F37F), (0x1F32D, 0x1F330), (0x1F950, 0x1F96F),
            (0x1F9C0, 0x1F9CB), (0x1FAD0, 0x1FADB), 0x2615)

TRAVEL = cps((0x1F30D, 0x1F310), 0x1F301, 0x1F303, 0x1F306, 0x1F307, 0x1F309,
              (0x1F3E0, 0x1F3F0), 0x26EA, 0x26F2, 0x26F5, 0x26FA, 0x26FD,
              (0x1F680, 0x1F6C5), (0x1F6D0, 0x1F6D7), 0x1F6EB, 0x1F6EC,
              (0x1F9BC, 0x1F9BD), 0x1F6F4, 0x1F6F5, 0x1F6F9, 0x1F6FA) + vs(cps(0x2708))

ACTIVITY = cps((0x1F380, 0x1F393), 0x26BD, 0x26BE, 0x26F3, (0x1F3A0, 0x1F3C4),
                (0x1F3C5, 0x1F3CA), (0x1F3CF, 0x1F3D3), 0x1F945, (0x1F947, 0x1F94F),
                (0x1FA80, 0x1FA86), 0x1F941, 0x1F6F7, 0x1F6F8, 0x1F93F, 0x1FA70)

OBJECTS = cps(0x231A, 0x231B, 0x1F484, (0x1F489, 0x1F48E), (0x1F4B0, 0x1F4FC),
               (0x1F507, 0x1F517), (0x1F526, 0x1F52E), (0x1F9E2, 0x1F9FF),
               (0x1FA71, 0x1FA7C), (0x1F5FB, 0x1F5FF), 0x1F6AA, 0x1F6CC,
               0x1F6D2, 0x1F9F0) + vs(cps(0x2702, 0x2709, 0x270F, 0x2712, 0x2328))

SYMBOLS = (vs(cps(0x2764)) + cps((0x1F493, 0x1F49F), 0x1F5A4, 0x1F90D, 0x1F90E,
            0x1F9E1, (0x1FA75, 0x1FA77), 0x1F4AF, 0x2B50, 0x1F31F, 0x2728, 0x1F4A5,
            0x1F4AB, 0x1F4A2, 0x1F4A6, 0x1F4A8, 0x2705, 0x274C, 0x274E, 0x2757,
            0x2753, 0x2754, 0x2755, 0x2B55, 0x26D4, 0x1F6AB, 0x1F51E, (0x1F192, 0x1F19A),
            (0x2648, 0x2653), (0x23E9, 0x23EC), 0x1F503, 0x1F504, 0x1F51F)
           + vs(cps(0x203C, 0x2049, 0x2B06, 0x2B07, 0x2B05, 0x27A1, 0x2195, 0x2194)))

FLAG_CODES = ["KR", "US", "JP", "CN", "GB", "FR", "DE", "IT", "ES", "CA", "AU", "BR",
              "IN", "RU", "MX", "ID", "VN", "TH", "PH", "MY", "SG", "TW", "HK", "NL",
              "SE", "NO", "FI", "DK", "CH", "AT", "BE", "PT", "GR", "TR", "PL", "CZ",
              "HU", "UA", "IE", "NZ", "AR", "CL", "CO", "PE", "SA", "AE", "IL", "EG",
              "ZA", "NG", "KE", "MN", "KZ", "UZ", "LA", "KH", "MM", "NP", "LK", "BD"]

def flag(code):
    return "".join(chr(0x1F1E6 + ord(c) - ord("A")) for c in code)

FLAGS = cps(0x1F3C1, 0x1F6A9, 0x1F38C) + [flag(c) for c in FLAG_CODES]

KAOMOJI_GROUPS = [
    ("기쁨·웃음", [
        "(^_^)", "(^o^)/", "(^_-)", "(＾▽＾)",
        "(´▽`)", "(≧∇≦)", "(*≧ω≦)", "(￣▽￣)",
        "(•‿•)", "(☆▽☆)", "(๑˃ᴗ˂)", "ヽ(´▽`)/",
        "(＾ω＾)", "(o^▽^o)", "(⌒▽⌒)", "＼(≧▽≦)／",
        "(￣ω￣)", "(☞ﾟヮﾟ)☞", "☜(ﾟヮﾟ☜)", "( ˙▿˙ )",
        "(¬‿¬)", "(￢‿￢)", "( ͡° ͜ʖ ͡°)", "(v‿v)",
        "(＾▽＾)/", "♪(´▽｀)", "(￣▽￣)ノ",
    ]),
    ("사랑·애정", [
        "(♡´▽`♡)", "(´,,•ω•,,)♡", "(*´з`)", "( ˘ ³˘)♥",
        "(♡˙︶˙♡)", "♡(｡- ω -)", "(´• ω •`)♡", "(>᎑<)♡",
        "σ(≧ε≦σ)♡", "♡＼(￣▽￣)／♡",
    ]),
    ("슬픔·눈물", [
        "(T_T)", "(ㅠ_ㅠ)", "(;_;)", "(._.)",
        "(个_个)", "(╥﹏╥)", "(ノ_<。)", "(っ˘̩╭╮˘̩)っ",
        "。･ﾟﾟ･(＞_＜)･ﾟﾟ･。", "(＃＞＜)", "(｡•́︿•̀｡)", "(-_-;)",
    ]),
    ("화남·불만", [
        "(¬_¬)", "(＃￣ω￣)", "(￣^￣)", "(－‸ლ)",
        "(╬ Ò﹏Ó)", "(￣ヘ￣)", "(ᗒᗣᗕ)՞", "凸(-_-)凸",
        "(≖_≖ )",
    ]),
    ("놀람·혼란", [
        "(o_O)", "(⊙_⊙)", "(O_O;)", "Σ(°△°)",
        "(@_@)", "(*_*)", "(x_x)", "(°ロ°)!",
        "(¯ . ¯;)", "(→_→)",
    ]),
    ("무심·피곤", [
        "(-_-)", "(－_－)zzZ", "(=_=)", "(￣o￣)zzZ",
        "( ´ー`)", "(ー_ー﹡)", "(´･ω･`)", "┐(￣ヘ￣)┌",
        "(¯\\_(ツ)_/¯)", "╮(╯▽╰)╭",
    ]),
    ("동작·기타", [
        "(ง•̀_•́)ง", "(๑•̀ㅂ•́)و✧", "(╯°□°)╯︵┻━┻", "┬─┬ノ(º_ºノ)",
        "(ノ°∀°)ノ⌒･*", "m(_ _)m", "(・_・)ノ", "(0^◇^0)/",
        "☆ミ(o*･ω･)ﾉ", "ε=ε=┌(;･∀･)┘", "(p´∀`q)", "(￣ε￣＠)",
        "( ˘ω˘ )☞",
    ]),
    ("동물", [
        "ʕ•ᴥ•ʔ", "ʕ￫ᴥ￩ʔ", "(=^･^=)", "(=^･ω･^)y＝",
        "", "V(=^･ω･^=)v", "(･ω･)つ⊂(･ω･)", "／(≧ x ≦)＼",
        "(￢ω￢)", "🐾ʕ·ᴥ·ʔ",
    ]),
    ("장식·인사", [
        "☆*:.｡.o(≧▽≦)o.｡.:*☆", "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "✧*。(ˊᗜˋ*)✧*。", "(๑•᎑•๑)",
        "(,,>᎑<,,)", "( ˶ˆᗜˆ˵ )", "(・∀・)", "(｡•̀ᴗ-)✧",
    ]),
]

SKIN_BASES = (cps((0x1F446, 0x1F450), (0x1F918, 0x1F91F), (0x1FAF0, 0x1FAF8),
               0x1F485, 0x1F4AA, 0x1F933, 0x270A, 0x270B, (0x1F64B, 0x1F64F),
               0x1F645, 0x1F646, 0x1F647, 0x1F481, 0x1F483, 0x1F486, 0x1F487,
               (0x1F466, 0x1F478), 0x1F47C, (0x1F9D1, 0x1F9DD), 0x1F6B6, 0x1F3C3,
               0x1F442, 0x1F443, 0x1F930, 0x1F931, 0x1F934, 0x1F935, 0x1F936,
               0x1F57A, 0x1F926, 0x1F937)
              + vs(cps(0x261D, 0x270C, 0x270D)))

def kstr(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'

def klist(items, per_line=12):
    lines = []
    for i in range(0, len(items), per_line):
        lines.append("            " + ", ".join(kstr(x) for x in items[i:i + per_line]) + ",")
    return "\n".join(lines)

CATEGORIES = [
    ("스마일리", "😀", SMILEYS, False),
    ("사람·동작", "👋", PEOPLE, False),
    ("동물·자연", "🐶", NATURE, False),
    ("음식", "🍎", FOOD, False),
    ("여행·장소", "🚗", TRAVEL, False),
    ("활동", "⚽", ACTIVITY, False),
    ("사물", "💡", OBJECTS, False),
    ("기호", "❤️", SYMBOLS, False),
    ("깃발", "🏳", FLAGS, False),
]

cat_src = []
for title, tab, items, wide in CATEGORIES:
    wide_arg = ", wide = true" if wide else ""
    cat_src.append(
        f'        Category(\n            "{title}",\n            "{tab}",\n'
        f"            listOf(\n{klist(items)}\n            ){wide_arg},\n        ),"
    )
categories_block = "\n".join(cat_src)
skin_block = klist(SKIN_BASES)

template = pathlib_read = open("scripts/emoji_template.kt.in").read()
kg_src = []
for title, items in KAOMOJI_GROUPS:
    kg_src.append(f'        "{title}" to listOf(\n{klist(items, per_line=4)}\n        ),')
kaomoji_block = "\n".join(kg_src)
out = (template.replace("@CATEGORIES@", categories_block)
       .replace("@SKIN_BASES@", skin_block)
       .replace("@KAOMOJI@", kaomoji_block))
open("app/src/main/java/dev/badalab/yeonfeel/ime/EmojiData.kt", "w").write(out)
print("generated:", sum(len(c[2]) for c in CATEGORIES), "emojis")
