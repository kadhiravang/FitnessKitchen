# Changelog

## v2.2

**Voice**
- **On-device Whisper (experimental):** transcribe voice input on the phone itself with a Whisper
  model, no laptop server or internet needed. Turn on "Transcribe on this phone" in Settings.
  Measured on a Galaxy S26: a 6.6 s clip transcribes in about 2 s with the turbo model and
  about 1.2 s with the small model. The model files are not bundled yet (turbo is ~1 GB, small
  ~375 MB); for now they are copied to the app's files folder `whisper/<model folder>/` by hand.
  Automatic download is planned.

**Fixes**
- **NVIDIA chat:** NVIDIA retired the DeepSeek model the app used, which returned a 410 error.
  The app now uses `deepseek-v4.1-flash` and, if NVIDIA retires a model again, looks up the
  current DeepSeek flash model itself and retries once.
- **Calorie goal:** a daily calorie goal you type on the You tab now overrides the target
  calculated from your profile (macros scale to match). Before, it was ignored once your
  profile was complete.
- **Diary limits:** the Diary now updates as soon as you change your goal or the optimal-range
  buffer, instead of showing the old values until the next meal was logged.

**Build**
- The sherpa-onnx native library is downloaded automatically on the first build.
- The APK now targets 64-bit ARM phones only (arm64-v8a), which covers current Android phones.

## v2.1

- **Backup & restore:** backups made by older versions now restore correctly on newer ones
  (a missing-field error used to stop them).
- **New phone:** the first setup screen has a "Restore from a backup instead" button, so a fresh
  install can restore straight away.
- The profile-photo crop is now saved and restored with a backup.

## v2.0

- **More accurate calories:** fixed a bug that rounded gram-based foods toward 1 kcal per gram.
  Every logged food now gets fresh numbers from the AI model and USDA data instead of a cached
  catalog value.
- **Goal-aware suggestions:** ask "what should I eat now?" or "how am I doing today?" and get
  real advice using your daily goal and what is left.
- **More chat providers:** Ollama (local), Ollama Cloud, Claude and OpenAI, alongside Gemini and
  NVIDIA.
- **Faster custom dishes:** USDA ingredient lookups run in parallel.
- The confirm card rescales calories when you edit the quantity, and Ollama streaming replies
  are handled correctly.

## v1.0

- First release: chat-based logging, Diary, History, weight tracking, progress photos, and
  backup & restore.
