# FitnessKitchen — South Indian Food Tracker

A native Android app (Kotlin + Jetpack Compose) for tracking calories with a food
database you control — built to cover South Indian dishes that mainstream calorie
apps like Fitia don't. The headline feature: a running **chat** with an AI
nutrition assistant, like talking to ChatGPT/Claude — type or speak naturally
(English or Tamil), and it logs what you ate.

- **Conversational logging** (`ui/chat`): one continuous chat thread. By default
  it's backed by Google Gemini with real **function calling** — when it hits a
  food it doesn't already know, it looks up real nutrition data from the USDA
  FoodData Central database (per-ingredient, if you describe a custom dish by
  what went into it) instead of guessing from memory, then combines that with
  the quantity you described. A confirm card always appears before anything is
  logged — edit anything, then confirm or discard.
- **Known-food matching**: once a food exists in your own catalog, a
  deterministic matcher (`util/FoodMatcher.kt`) recognizes it by name and reuses
  your stored values instead of asking the model again, so repeat logs of the
  same dish stay numerically consistent.
- **Your food database**: seeded with ~25 common South Indian dishes (idli, dosa,
  sambar, chutneys, pongal, biryani, etc.) and fully editable — add anything you
  actually eat, edit quantity/calories on any past log entry and the rest
  rescales automatically.
- **Voice input**: on-device speech recognition by default (English/Tamil,
  auto-switching mid-sentence on Android 14+), or point it at a Whisper
  transcription server — either one you self-host, or OpenAI's hosted API —
  for noticeably higher accuracy.
- **Diary, History, weight tracking, progress photos, and full backup/restore**
  to a single zip file (including all settings and API keys) you control —
  restorable on a new device.
- Everything (food catalog, daily log, chat history) is stored locally on-device
  in a Room/SQLite database. Only what's needed to answer a chat turn — your
  message, food catalog, and any nutrition lookups — is sent to your chosen AI
  provider's API; nothing else leaves the device.

## Try it without building anything

Grab the latest APK from the [Releases page](https://github.com/kadhiravang/Foodtracker/releases)
and sideload it:

1. On your Android phone: **Settings → About phone** → tap **Build number** 7
   times to unlock Developer Options, then **Settings → System → Developer
   options** → enable **USB debugging** (or just allow "Install unknown apps"
   for whichever browser/file manager you download the APK with).
2. Download the `.apk` from the Releases page above and open it to install.
3. See **First run** below — you'll need your own free Gemini API key to use
   the chat/logging feature.

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
docker run --rm -v "$PWD":/workspace -w /workspace foodtracker-build gradle assembleDebug --no-daemon
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

### Install to your device

The container can't easily reach a USB-attached device. Simplest path: build the
APK in the container (above), then install it from the host with `adb` (install
just `platform-tools`, no full Android Studio needed):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable Developer Options + USB debugging first (Settings → About phone → tap
Build number 7 times, then Settings → System → Developer options).

Alternative: open the project in Android Studio instead of Docker — Gradle sync
will resolve the same versions and you can run directly to a connected device or
emulator. Either path works; Docker exists so you don't have to install the
Android SDK on this machine at all.

## First run

1. **Get a free Gemini API key** from [aistudio.google.com/apikey](https://aistudio.google.com/apikey)
   — Gemini 2.5 Flash's free tier is what the chat runs on by default, no
   billing required.
2. Install and open the app, go to **Settings**, paste the key in under
   **Gemini API key** (stored encrypted on-device via
   `EncryptedSharedPreferences` — never hardcoded, never included anywhere but
   your own backup file).
3. *(Optional but recommended)* Get a free **USDA FoodData Central** key from
   [fdc.nal.usda.gov/api-key-signup](https://fdc.nal.usda.gov/api-key-signup)
   and add it in the same Settings section — this lets the chat look up real
   nutrition data for foods it doesn't already know, instead of guessing from
   the model's memory alone.
4. Grant microphone permission when prompted (only needed the first time you
   tap the mic in Chat).
5. On the **Chat** tab, type or tap the mic and say something like *"two idlis
   and one dosa with sambar and coconut chutney"* — or describe a custom dish
   by its ingredients and quantities. A confirm card appears; edit anything,
   pick a meal, tap Confirm.
6. Check **Diary** for the day's totals (tap the pencil on any entry to edit
   its quantity — calories rescale automatically), **History** for past days
   and your weight trend, and **Settings → Backup & Restore** to save
   everything to a zip you control.

Prefer NVIDIA's hosted model instead of Gemini? Switch **Chat model** in
Settings to NVIDIA and paste a key from [build.nvidia.com](https://build.nvidia.com)
— note the USDA-grounding and multi-call function-calling behavior described
above is currently Gemini-only.

### Voice transcription options (all optional)

The on-device recognizer works out of the box. For higher accuracy, Settings →
**Voice transcription (Whisper)** offers two alternatives:
- **Self-hosted**: run the Whisper server on your own laptop (see
  `whisper-server/`) and enter its address — only reachable on the same Wi-Fi.
- **OpenAI's cloud API**: flip the switch and paste an OpenAI API key instead —
  needs an OpenAI account with billing set up (Whisper isn't on the free tier),
  get one at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).

If neither is reachable/configured, it falls back to the on-device recognizer
automatically.

## Project layout

```
app/src/main/java/com/kadhiravan/foodtracker/
  data/local/          Room entities/DAOs (food catalog, log entries, chat messages, weight), seed data
  data/remote/         Gemini (function-calling + USDA grounding) and NVIDIA chat clients,
                        fenced-JSON log-card parser, USDA/Whisper HTTP clients
  data/repository/     Thin repositories the ViewModels talk to; deterministic known-food
                        override happens here (ChatRepository)
  data/prefs/          Encrypted storage for API keys + settings
  data/backup/         Zip export/import of the entire local database + settings
  util/                FoodMatcher — name/unit similarity matching against your own catalog
  ui/chat/             The main chat thread: bubbles, typing indicator, inline confirm card
  ui/home/             "Diary" tab — a day's log grouped by meal, quantity-edit dialog
  ui/voice/            Mic capture + Whisper WAV recording, used by Chat
  ui/fooddb/           Food catalog CRUD
  ui/history/          Past days, weight trend, progress photos
  ui/settings/         API keys, daily calorie goal, voice/Whisper config, backup & restore
  ui/navigation/       Bottom-nav + NavHost wiring it all together (Chat is the start destination)
```

## Notes

- `app/debug.keystore` is a committed, deterministic debug signing key (standard
  `android`/`androiddebugkey` debug credentials — not a secret, this is the normal
  convention for reproducible debug builds). It exists so every rebuild — from the
  disposable Docker container or anywhere else — signs with the same key and
  `adb install -r` keeps working without needing to uninstall first.
- Calorie values in the seed data are reasonable per-serving estimates, not lab
  measurements — edit anything from the Foods screen, or let the USDA-grounded
  chat lookup refine them the first time you log something new.
- Chat calls either `https://generativelanguage.googleapis.com` (Gemini, default)
  or `https://integrate.api.nvidia.com/v1/chat/completions` (NVIDIA) with your
  own API key sent directly from the device; no backend server is involved.
- Verified: `docker build -t foodtracker-build .` and
  `docker run --rm -v "$PWD":/workspace -w /workspace foodtracker-build gradle assembleDebug --no-daemon`
  succeed end-to-end, and the resulting APK has been installed and run on a
  physical Pixel 6a via `adb`.
