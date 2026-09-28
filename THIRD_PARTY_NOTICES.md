# Third-Party Notices

연필키보드(YEONFEEL Keyboard)와 선택 설치하는 ML Kit 번역 애드온
(`translate-mlkit/`)은 아래 서드파티 자산을 포함한다. 앱 코드는 저장소 루트의
[LICENSE](LICENSE)(Apache License 2.0)를 따르며, 아래 자산에는 각 항목의
라이선스나 약관이 별도로 적용된다. Lucide 아이콘과 한국어 빈도 데이터 고지는
키보드 앱 내 "설정 → 일반 → 오픈소스 라이선스" 화면에도 포함된다.

## Lucide Icons — ISC License

`app/src/main/res/drawable/`의 일부 벡터 아이콘은 [Lucide](https://lucide.dev)에서
가져왔다 (각 파일 상단에 원본 아이콘 이름을 주석으로 표기). 키보드 툴바의 번역
버튼(`ic_toolbar_translate.xml`)과 ML Kit 애드온의 앱 아이콘
(`translate-mlkit/src/main/res/drawable/ic_launcher_foreground.xml`)은 Lucide
`languages` 아이콘을 쓴다.

> ISC License
>
> Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as
> part of Feather (MIT). All other copyright (c) for Lucide are held by
> Lucide Contributors 2022.
>
> Permission to use, copy, modify, and/or distribute this software for any
> purpose with or without fee is hereby granted, provided that the above
> copyright notice and this permission notice appear in all copies.
>
> THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
> WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
> MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
> ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
> WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
> ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
> OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

## Google ML Kit GenAI (Gemini Nano) — 키보드

키보드는 Gemini Nano 번역 엔진을 위해 Google의 ML Kit GenAI Prompt API
라이브러리(`com.google.mlkit:genai-prompt`)를 포함한다. 이 라이브러리와 함께
들어오는 Google 라이브러리(ML Kit common, Google Play 서비스, Firebase
components, `datatransport`)도 APK에 포함된다. 사용에는 다음 약관이 적용된다.

- [ML Kit 약관](https://developers.google.com/ml-kit/terms) — [Google APIs 서비스 약관](https://developers.google.com/terms)을 포함한다.
- [ML Kit GenAI API 추가 서비스 약관](https://developers.google.com/ml-kit/genai-terms)

## Google ML Kit 번역 — ML Kit 애드온

ML Kit 애드온은 Google의 ML Kit 번역 라이브러리(`com.google.mlkit:translate`)와
그 네이티브 번역 엔진, 그리고 함께 들어오는 Google 라이브러리를 포함한다.
언어 모델은 설치 후 Google에서 내려받는다. 사용에는
[ML Kit 약관](https://developers.google.com/ml-kit/terms)과 그에 포함된
[Google APIs 서비스 약관](https://developers.google.com/terms)이 적용된다.

## 한국어 빈도 데이터 — CC BY-SA 4.0

`app/src/main/assets/ko_freq.txt`와 `app/src/main/assets/ko_known.bloom`은
[FrequencyWords](https://github.com/hermitdave/FrequencyWords)의 한국어 빈도
목록(OpenSubtitles 2018 말뭉치 기반, © Hermit Dave)을 가공한 파생물이다.

- 라이선스: [Creative Commons Attribution-ShareAlike 4.0 International](https://creativecommons.org/licenses/by-sa/4.0/)
- 변경 내용: 빈도 하한으로 필터링(`ko_freq.txt`), 어절 목록을 블룸 필터로
  해시(`ko_known.bloom`). 생성 절차는 `scripts/generate_lexicon.py` 참고.
- ShareAlike에 따라 **이 두 파일은 앱 코드의 Apache-2.0과 무관하게
  CC BY-SA 4.0으로 배포된다.** 이 파일들을 재사용·재가공할 때는 같은
  라이선스를 적용해야 한다.

## Gradle Wrapper — Apache License 2.0

`gradle/wrapper/gradle-wrapper.jar`는 [Gradle](https://gradle.org)의 표준
래퍼 바이너리다 (Apache License 2.0).
