#!/usr/bin/env python3
"""AI 보정용 사전 생성. 원본: FrequencyWords ko_full.txt (OpenSubtitles 2018, CC-BY-SA 4.0)
- assets/ko_freq.txt  : 교정 후보 (빈도 >= MIN_CANDIDATE)
- assets/ko_known.bloom: 알려진 어절 보호용 블룸 필터 (오탐은 '교정 안 함' 방향)"""
import re, math, sys

SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/ko_full.txt"
MIN_CANDIDATE = 20
BITS_PER_ELEM = 8.2  # ~2% 오탐
HASHES = 6

FNV_OFFSET = 0xCBF29CE484222325
FNV_PRIME = 0x100000001B3
MASK64 = (1 << 64) - 1

def fnv64(data: bytes, seed: int = FNV_OFFSET) -> int:
    h = seed
    for b in data:
        h ^= b
        h = (h * FNV_PRIME) & MASK64
    return h

known = []
candidates = []
for line in open(SRC, encoding="utf-8"):
    parts = line.split()
    if len(parts) != 2:
        continue
    word, cnt = parts[0], int(parts[1])
    if not re.fullmatch(r"[가-힣]{1,10}", word):
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

print(f"known={len(known)} candidates={len(candidates)} bloom={m//8/1024:.0f}KB")
