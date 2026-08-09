# 연필키보드 (YEONFEEL Keyboard)

안드로이드용 한글 키보드(IME). 한글 입력 품질, AI 기능, 커스터마이징에 집중한다. 로드맵은 [PLAN.md](PLAN.md) 참고.


## 빌드 및 실행

```sh
./gradlew test assembleDebug          # 단위 테스트 + 디버그 APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

설치 후 "연필키보드" 앱을 열어 안내에 따라 키보드를 활성화한다.

## 라이선스

- 앱 코드: [Apache License 2.0](LICENSE)
- 서드파티 자산(Lucide 아이콘 — ISC, 한국어 빈도 데이터 — CC BY-SA 4.0):
  [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 참고. 특히
  `app/src/main/assets/ko_freq.txt`·`ko_known.bloom`은 ShareAlike 조건에 따라
  코드와 별도로 CC BY-SA 4.0이 적용된다.

## 구조

```
app/src/main/java/dev/badalab/yeonfeel/
├── hangul/HangulComposer.kt   # 두벌식 조합 오토마타 (순수 Kotlin, Android 의존성 없음)
├── ime/YeonfeelImeService.kt  # InputMethodService — InputConnection 연동
├── ime/KeyboardView.kt        # 키보드 렌더링·터치 처리
├── ime/KeyboardLayouts.kt     # 한글/영문/기호 레이아웃 정의
└── MainActivity.kt            # 키보드 활성화 도우미
```

- 한국어 빈도 사전: [FrequencyWords](https://github.com/hermitdave/FrequencyWords)
  (OpenSubtitles 2018, CC BY-SA 4.0 — 위 라이선스 절 참고)
