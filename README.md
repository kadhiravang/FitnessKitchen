# FitnessKitchen — South Indian Food Tracker

A native Android app (Kotlin + Jetpack Compose) for tracking calories with a food
database you control — built to cover South Indian dishes that mainstream calorie
apps like Fitia don't. The headline feature: a running **chat** with an AI
nutrition assistant, like talking to ChatGPT/Claude — type or speak naturally,
and if it doesn't already know a dish, it asks a quick clarifying question
instead of guessing, then hands you an editable confirm card before anything
is logged.

- **Conversational logging** (`ui/chat`): one continuous chat thread, backed by
  NVIDIA's hosted `deepseek-ai/deepseek-v4-flash-0731` model. The assistant either
  replies conversationally (asking for detail on an unfamiliar recipe) or, once
  confident, replies with a short line plus an inline food-log card you edit and
  confirm — nothing is ever logged without that confirm step. Grounded against
  your own food database so known dishes get accurate calories instead of guesses.
- **Your food database**: seeded with ~25 common South Indian dishes (idli, dosa,
  sambar, chutneys, pongal, biryani, etc.) and fully editable — add anything you
  actually eat.
- Everything (food catalog, daily log, chat history) is stored locally on-device
  in a Room/SQLite database. Only the conversation + your food list are sent to
  NVIDIA's API for parsing; nothing else leaves the device.

## Build environment: Docker

There's no Android SDK/JDK on this machine, so the whole toolchain (JDK 17, Gradle
8.7, Android SDK + build-tools 34) lives in a disposable Docker image. Only this
source tree persists on your host — build the image, use it, throw it away.

```bash
docker build -t foodtracker-build .
```

If you just installed Docker and get `permission denied ... docker.sock` even
after `sudo usermod -aG docker $USER`, the group change needs a fresh login
session to take effect — run `newgrp docker` in your shell, or log out/in.

### Build a debug APK

```bash
docker run --rm -v "$PWD":/workspace -w /workspace foodtracker-build gradle assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk` on your host (bind
mount), even after the container exits.

### Interactive shell (repeated builds, `gradle tasks`, etc.)

```bash
docker compose run --rm android-build
# inside the container:
gradle assembleDebug
```

A named volume caches `~/.gradle` between runs so repeat builds are fast without
keeping the container itself around.

### Install to your Pixel 6a

The container can't easily reach a USB-attached device. Simplest path: build the
APK in the container (above), then install it from the host with `adb` (install
just `platform-tools`, no full Android Studio needed):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable Developer Options + USB debugging on the Pixel 6a first (Settings → About
phone → tap Build number 7 times, then Settings → System → Developer options).

Alternative: open the project in Android Studio instead of Docker — Gradle sync
will resolve the same versions and you can run directly to a connected device or
emulator. Either path works; Docker exists so you don't have to install the
Android SDK on this machine at all.

## First run

1. Get an NVIDIA API key from [build.nvidia.com](https://build.nvidia.com) if you
   don't have one already.
2. Install and open the app, go to **Settings**, paste in the API key (stored
   encrypted on-device via `EncryptedSharedPreferences` — never hardcoded, never
   backed up).
3. Grant microphone permission when prompted (only needed the first time you tap
   the mic in Chat).
4. On the **Chat** tab, type or tap the mic and say something like *"two idlis
   and one dosa with sambar and coconut chutney"*. For something the assistant
   doesn't recognize, it'll ask a follow-up — answer it, then a confirm card
   appears; edit anything, pick a meal, tap Confirm.
5. Check **Diary** for the day's totals, **Foods** to see/edit the South Indian
   dish catalog, and **History** to browse past days.

## Project layout

```
app/src/main/java/com/kadhiravan/foodtracker/
  data/local/        Room entities/DAOs (food catalog, log entries, chat messages), seed data
  data/remote/        NVIDIA chat-completions client (multi-turn) + fenced-JSON log-card parser
  data/repository/    Thin repositories the ViewModels talk to
  data/prefs/         Encrypted storage for the API key + settings
  ui/chat/             The main chat thread: bubbles, typing indicator, inline confirm card
  ui/home/             "Diary" tab — a day's log grouped by meal
  ui/voice/            Mic capture wrapper (android.speech.SpeechRecognizer), used by Chat
  ui/fooddb/           Food catalog CRUD
  ui/history/           Past days
  ui/settings/         API key + daily calorie goal
  ui/navigation/       Bottom-nav + NavHost wiring it all together (Chat is the start destination)
```

## Notes

- `app/debug.keystore` is a committed, deterministic debug signing key (standard
  `android`/`androiddebugkey` debug credentials — not a secret, this is the normal
  convention for reproducible debug builds). It exists so every rebuild — from the
  disposable Docker container or anywhere else — signs with the same key and
  `adb install -r` keeps working without needing to uninstall first.

- Calorie values in the seed data are reasonable per-serving estimates, not lab
  measurements — edit anything from the Foods screen.
- Chat calls `https://integrate.api.nvidia.com/v1/chat/completions` with your API
  key sent as a Bearer token directly from the device; no backend server is
  involved. Each turn resends up to the last 20 messages as context so the
  assistant remembers the conversation, capped to bound token cost on a
  long-running thread.
- The database schema bumped to version 2 (added a `chat_messages` table) with
  `fallbackToDestructiveMigration()` — since there's no real user data to
  preserve yet, a schema change just wipes and reseeds the local DB rather than
  writing a migration.
- Verified: `docker build -t foodtracker-build .` and
  `docker run --rm -v "$PWD":/workspace -w /workspace foodtracker-build gradle assembleDebug`
  succeed end-to-end, and the resulting APK has been installed and run on a
  physical Pixel 6a via `adb`.
