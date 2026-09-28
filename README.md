# YEONFEEL Keyboard

[한국어](README.ko.md)

YEONFEEL Keyboard is an Android input method (IME) developed with a focus
on Korean input quality; English input is supported as well. It was written from
scratch in collaboration with Claude, for phones whose stock keyboards
handle Korean poorly. The design goal is a keyboard that works entirely offline: the keyboard
app requests no `INTERNET` permission, so it cannot send input, clipboard
content, or usage data over the network. The release APK is about 2.4 MB. If you are
looking for a Samsung Keyboard alternative for other Android phones, welcome
aboard.

The keyboard can also translate as you type, using translation engines that
run on the phone (see [Translation](#translation)). ML Kit, the one engine
that needs a network permission to download its language models, lives in
a separate, optional add-on app, so the keyboard itself stays offline.

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

## Translation

The translate button in the toolbar opens a translate panel. The button is
part of the default toolbar; if you saved a custom toolbar order earlier,
add it from the toolbar edit panel. Type in the source language, and about 350 ms after the last
keystroke the translation is written into the app's text field as
composing text, replacing the previous translation. Enter confirms it; on
an empty panel, Enter performs the field's normal action. Tapping the
language chip swaps the pair. The source text is limited to 2,000
characters. The translate button is hidden in password fields and in fields
where the app asks the keyboard not to learn from input (for example,
incognito mode).

Supported languages are Korean, English, Japanese, Simplified Chinese,
Spanish, French, German, and Vietnamese; the default is Korean to English.
Whether a pair actually works depends on the engine.

The engine is chosen in Settings → Translation, which also shows whether
each engine works on the phone and offers a test translation:

- **System translation** (default): Android 12+ `TranslationManager`. It
  works only where the phone maker ships a system translation service,
  which is mainly Pixel. Language packs are installed from system settings;
  the Translation screen links to them.
- **ML Kit**: works on most phones, but needs the ML Kit add-on app
  described below.
- **Gemini Nano**: Google's on-device model through the AICore system app,
  on supported recent flagships with Android 8.0 or later. AICore
  downloads the model itself when it is needed. AICore only
  serves the app in the foreground, and a keyboard is not that app, so
  AICore may refuse requests from inside the keyboard. When that happens,
  the keyboard remembers it for the installed AICore version and stops
  trying; an AICore update, or "Try Gemini Nano in the keyboard again" in
  Settings → Translation, clears it. A successful test translation in
  settings does not prove that Gemini Nano works inside the keyboard.

Translation runs on the device with every engine, and the keyboard sends
typed text to no server. The text goes only to the chosen engine's
on-device component: the phone maker's system translation service, Google's
AICore system app, or our own signed ML Kit add-on. The first two are
system apps outside this project, governed by their makers' terms.

Behavior on real phones, in particular Gemini Nano inside a keyboard, is
not yet verified; that checklist is tracked in
[#22](https://github.com/YEONFEEL96/yeonfeel-keyboard/issues/22).

### ML Kit add-on

The ML Kit engine ships as a separate app, `dev.badalab.yeonfeel.translate.mlkit`
(module `translate-mlkit/`). Only people who choose the ML Kit engine need
it. It is kept out of the keyboard for two reasons:

- ML Kit's native translation engine is 11–17 MB per CPU architecture. The
  add-on is currently built as one universal APK for all four
  architectures, about 63.8 MB.
- ML Kit downloads a language model (about 30 MB per language) the first
  time a language is used, so the add-on requests `INTERNET` and
  `ACCESS_NETWORK_STATE`. From the keyboard, models are downloaded only on
  Wi-Fi; the test translation in Settings → Translation may also download
  over mobile data. Google's ML Kit libraries in the add-on send usage
  statistics to Google. Typed text is translated on the device and is not
  sent.

Opening the add-on shows the downloaded language models and lets you
delete them. When the add-on is missing, Settings → Translation links to
the project's GitHub releases page.

The keyboard and the add-on talk over a small Messenger IPC protocol
(`translate-protocol/`). The add-on's translation service is protected by
the signature permission `dev.badalab.yeonfeel.permission.TRANSLATE`, so
only an app signed with the same key can use it. In the other direction,
the keyboard checks that the installed add-on is signed with the keyboard's
own certificate before sending any text; an add-on that fails the check is
treated as not installed. The add-on never logs source or translated text.

This means both apps must be signed with the same key. Because both declare
the same permission, Android refuses to install an add-on signed with a
different key next to the keyboard. If you build from source, build and
install both apps from your own build, and do not mix them with official
release APKs.

## Data handling

- Clipboard history is encrypted at rest with an Android Keystore key
  (AES-256-GCM). The key is hardware-backed where the device supports it.
- Password fields disable key preview, touch-data collection, and all
  text-transforming options.
- Touch-correction samples are stored per key with no ordering and no
  timestamps beyond day granularity, and are deleted after 7 days. Typed
  text cannot be reconstructed from the stored file.
- Translation is off in password fields and in fields that ask for no
  personalized learning. Typed text goes only to the chosen on-device
  engine (see [Translation](#translation)), and neither the keyboard nor the
  add-on logs it.
- Besides the project's own `translate-protocol` module, the keyboard's
  runtime dependencies are a few AndroidX libraries and Google's ML Kit
  GenAI library for Gemini Nano, which brings Google Play
  services, ML Kit common, and Google's usage-reporting library
  (`datatransport`) along. The usage-reporting components are present in
  the APK, but the keyboard has no network permission, so nothing they
  collect is uploaded. The network permissions those libraries would add
  are stripped from the keyboard's manifest, and every keyboard build runs
  a Gradle check, `verify<Variant>NoNetworkPermission`, that fails if the
  merged manifest requests `INTERNET` or `ACCESS_NETWORK_STATE`.

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
./gradlew test assembleDebug          # unit tests + debug APKs (keyboard and ML Kit add-on)
adb install app/build/outputs/apk/debug/app-debug.apk
adb install translate-mlkit/build/outputs/apk/debug/translate-mlkit-debug.apk   # optional
```

Use the project's Gradle wrapper (Gradle 8.13); newer standalone Gradle
versions are incompatible with the AGP version in use. Gradle needs the
Android SDK: set `ANDROID_HOME`, or put `sdk.dir` in a `local.properties`
file at the repository root. After installing,
open the YEONFEEL Keyboard app and follow the prompts to enable the
keyboard. The add-on only needs to be installed if you want the ML Kit
engine.
minSdk is 23.

`./gradlew assembleRelease` builds `app-release.apk` (about 2.4 MB) and
`translate-mlkit-release.apk` (about 63.8 MB). Both are signed with the
release keystore when the `YEONFEEL_RELEASE_STORE_FILE`,
`YEONFEEL_RELEASE_STORE_PASSWORD`, `YEONFEEL_RELEASE_KEY_ALIAS`, and
`YEONFEEL_RELEASE_KEY_PASSWORD` Gradle properties are set (for example in
`~/.gradle/gradle.properties`), and with the debug key otherwise, so the two
APKs from one build always share a key.

## Project layout

```
app/src/main/java/dev/badalab/yeonfeel/
├── hangul/       # Composition automata and word corrector (pure Kotlin, JVM-tested)
├── ime/          # InputMethodService, rendering, layouts, touch model, translate panel
├── translate/    # Translation engines (System, Gemini Nano, ML Kit add-on client)
├── clipboard/    # Keystore-encrypted clipboard history
├── settings/     # Settings screens (View-based UI kit)
└── debug/        # Touch-sample store
translate-protocol/  # IPC protocol shared by the keyboard and the add-on (JVM-tested)
translate-mlkit/     # ML Kit translation add-on app
scripts/          # Generators for emoji data and the correction lexicon
```

## License

App code is licensed under the [Apache License 2.0](LICENSE).

Third-party assets are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md): Lucide icons (ISC),
Google's ML Kit GenAI library in the keyboard and ML Kit Translation in the
add-on (Google's terms), and the Korean frequency data derived from
[FrequencyWords](https://github.com/hermitdave/FrequencyWords)
(OpenSubtitles 2018). The derived files `app/src/main/assets/ko_freq.txt`
and `ko_known.bloom` remain under CC BY-SA 4.0, separately from the app
code.
