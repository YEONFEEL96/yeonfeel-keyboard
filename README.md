# YEONFEEL Keyboard

[한국어](README.ko.md)

YEONFEEL Keyboard is an Android input method (IME) developed with a focus
on Korean input quality; English input is supported as well. It was written from
scratch in collaboration with Claude, for phones whose stock keyboards
handle Korean poorly. The design goal is a keyboard that works entirely offline: the app
requests no `INTERNET` permission, so no input, clipboard content, or usage
data can leave the device. The release APK is about 1.2 MB and has no runtime
dependency beyond `androidx.core`. If you are looking for a Samsung Keyboard
alternative for other Android phones, welcome aboard.

## Input

Six layouts are included: 두벌식 (standard), 단모음 (10-key short vowel),
천지인, 나랏글, 나랏글 중앙, and English QWERTY/Dvorak. The 3x4 layouts
follow the national-standard arrangements, with long-press digit input and
compact symbol pages.

The composition automata live in `hangul/` as pure Kotlin with no Android
dependencies and are covered by JVM unit tests, including edge cases and
key rollover during fast typing.

Smaller input options include an auto-replacement of the common misspelling
됬 with 됐, double-tap shift for caps lock, spacebar language switching,
configurable multi-tap timing, and backspace undo for auto-corrections.
Holding down ㅋ repeats it until the key is released.

Two humor options ship as well, both off by default and disabled in password
fields. One retypes ㅋㅋㅋ bursts as ㅋㅋㅎㅋ so the typist reads as a younger
texter; the other reproduces the ㅋㅋㅋㅋㄱㅋㅋ typos of the feature-phone
천지인 generation.

## Correction (experimental, off by default)

Two correction features can be enabled in the 실험실 menu. Both run on the
device.

- Touch correction builds a per-key Gaussian model of where the user
  actually taps, and re-scores ambiguous touches near key boundaries.
- Word correction proposes replacements using keyboard-adjacency edit
  distance over a 28,000-word frequency lexicon. A bloom filter of 660,000
  known words prevents real words from being replaced.

## Data handling

- Clipboard history is encrypted at rest with an Android Keystore key
  (AES-256-GCM). The key is hardware-backed where the device supports it.
- Password fields disable key preview, touch-data collection, and all
  text-transforming options.
- Touch-correction samples are stored per key with no ordering and no
  timestamps beyond day granularity, and are deleted after 7 days. Typed
  text cannot be reconstructed from the stored file.

## Customization

- Light, dark, and four high-contrast themes; three key-text sizes.
- Adjustable keyboard height, margins, and long-press delay through a
  drag-to-adjust overlay.
- Split keyboard for landscape and large screens, one-handed mode, and an
  editable toolbar.
- An optional terminal tool row (Esc, Tab, Ctrl, Alt, arrow keys) for SSH
  clients.
- An emoji panel with 1,082 emojis, skin-tone memory, and 초성 search, plus
  a kaomoji panel grouped by mood.

## Building and running

```sh
./gradlew test assembleDebug          # unit tests + debug APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

Use the project's Gradle wrapper (Gradle 8.13); newer standalone Gradle
versions are incompatible with the AGP version in use. After installing,
open the YEONFEEL Keyboard app and follow the prompts to enable the
keyboard.
minSdk is 23.

## Project layout

```
app/src/main/java/dev/badalab/yeonfeel/
├── hangul/       # Composition automata and word corrector (pure Kotlin, JVM-tested)
├── ime/          # InputMethodService, rendering, layouts, touch model
├── clipboard/    # Keystore-encrypted clipboard history
├── settings/     # Settings screens (View-based UI kit)
└── debug/        # Touch-sample store
scripts/          # Generators for emoji data and the correction lexicon
```

## License

App code is licensed under the [Apache License 2.0](LICENSE).

Third-party assets are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): Lucide icons (ISC) and
the Korean frequency data derived from
[FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(OpenSubtitles 2018). The derived files `app/src/main/assets/ko_freq.txt`
and `ko_known.bloom` remain under CC BY-SA 4.0, separately from the app
code.
