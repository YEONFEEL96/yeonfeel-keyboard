#!/usr/bin/env python3
"""AI 보정용 사전 생성. 원본: FrequencyWords ko_full.txt (OpenSubtitles 2018, CC-BY-SA 4.0)
- assets/ko_freq.txt  : 교정 후보 (빈도 >= MIN_CANDIDATE)
- assets/ko_known.bloom: 알려진 어절 보호용 블룸 필터 (오탐은 '교정 안 함' 방향)
표준어에 없는 표기(됬·됀·괜찬- 등)는 둘 다에서 뺀다. 저장소 루트에서 실행한다."""
import re, math, sys

# 원본: https://github.com/hermitdave/FrequencyWords
#   content/2018/ko/ko_full.txt (CC BY-SA 4.0)
SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ko_full.txt"
MIN_CANDIDATE = 20
BITS_PER_ELEM = 8.2  # ~2% 오탐
HASHES = 6

FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3
MASK64 = (1 << 64) - 1

CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
JONG = "ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"


def syllable(cho: str, jung: str, jong: str = "") -> str:
    j = JONG.index(jong) + 1 if jong else 0
    return chr(0xAC00 + (CHO.index(cho) * 21 + JUNG.index(jung)) * 28 + j)


# 말뭉치(자막)에 흔해도 표준어에 없는 표기. 이런 어절을 사전·블룸에 넣으면 '이미 있는
# 단어'로 보호돼 교정되지 않는다. 되어·뵈어의 준말은 '돼·봬'라 받침은 ㅆ(됐·뵀)만 붙고,
# '되·뵈'에 ㅆ이 붙은 됬·뵜도 없는 음절이다. 괜찬-은 괜찮-의 굳은 오기다.
NONSTANDARD_SYLLABLES = {
    syllable(c, "ㅙ", k) for c in "ㄷㅂ" for k in JONG if k != "ㅆ"
} | {syllable(c, "ㅚ", "ㅆ") for c in "ㄷㅂ"}
NONSTANDARD_SPELLINGS = ("괜찬",)


def is_nonstandard(word: str) -> bool:
    return any(ch in NONSTANDARD_SYLLABLES for ch in word) or any(
        s in word for s in NONSTANDARD_SPELLINGS
    )


def fnv64(data: bytes, seed: int = FNV_OFFSET) -> int:
    h = seed
    for b in data:
        h ^= b
        h = (h * FNV_PRIME) & MASK64
    return h

known = []
candidates = []
excluded = 0
for line in open(SRC, encoding="utf-8"):
    parts = line.split()
    if len(parts) != 2:
        continue
    word, cnt = parts[0], int(parts[1])
    if not re.fullmatch(r"[가-힣]{1,10}", word):
        continue
    if is_nonstandard(word):
        excluded += 1
        continue
    known.append(word)
    if cnt >= MIN_CANDIDATE:
        candidates.append((word, cnt))

m = int(len(known) * BITS_PER_ELEM)
m -= m % 8
bits = bytearray(m // 8)
for w in known:
    data = w.encode("utf-8")
    h1 = fnv64(data)
    h2 = fnv64(data, seed=0x9E3779B97F4A7C15) | 1
    for i in range(HASHES):
        idx = ((h1 + i * h2) & MASK64) % m
        bits[idx >> 3] |= 1 << (idx & 7)

with open("app/src/main/assets/ko_known.bloom", "wb") as f:
    f.write(len(known).to_bytes(4, "big"))
    f.write(HASHES.to_bytes(4, "big"))
    f.write(m.to_bytes(8, "big"))
    f.write(bits)

with open("app/src/main/assets/ko_freq.txt", "w", encoding="utf-8") as f:
    for w, c in candidates:
        f.write(f"{w} {c}\n")

print(f"known={len(known)} candidates={len(candidates)} nonstandard={excluded} bloom={m//8/1024:.0f}KB")
