# Saapadu — South Indian Food Tracker

A native Android app (Kotlin + Jetpack Compose) for tracking calories with a food
database you control — built to cover South Indian dishes that mainstream calorie
apps like Fitia don't. The headline feature: tap the mic, say what you ate, and get
back an editable list of foods + calories to confirm.

- **Voice logging**: on-device speech-to-text, then the transcript is sent to
  NVIDIA's hosted `deepseek-ai/deepseek-v4-flash-0731` model to extract a
  structured food list, grounded against your own food database so known dishes
  get accurate calories instead of AI guesses.
- **Your food database**: seeded with ~25 common South Indian dishes (idli, dosa,
  sambar, chutneys, pongal, biryani, etc.) and fully editable — add anything you
  actually eat.
- Everything (food catalog, daily log) is stored locally on-device in a Room/SQLite
  database. Only the transcript + your food list are sent to NVIDIA's API for
  parsing; nothing else leaves the device.

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
3. Grant microphone permission when prompted.
4. From **Today**, tap **Log by voice**, and say something like *"two idlis and
   one dosa with sambar and coconut chutney"*. Confirm/edit the parsed list, pick
   a meal, save.
5. Check **Foods** to see/edit the South Indian dish catalog, or add your own.

## Project layout

```
app/src/main/java/com/kadhiravan/foodtracker/
  data/local/        Room entities, DAOs, database, seed data (South Indian dishes)
  data/remote/        NVIDIA chat-completions client + parsed-item DTO
  data/repository/    Thin repositories the ViewModels talk to
  data/prefs/         Encrypted storage for the API key + settings
  ui/home/             Today's log, grouped by meal
  ui/voice/            Mic capture -> transcript -> AI parse -> editable confirm list
  ui/fooddb/           Food catalog CRUD
  ui/history/           Past days
  ui/settings/         API key + daily calorie goal
  ui/navigation/       Bottom-nav + NavHost wiring it all together
```

## Notes

- `app/debug.keystore` is a committed, deterministic debug signing key (standard
  `android`/`androiddebugkey` debug credentials — not a secret, this is the normal
  convention for reproducible debug builds). It exists so every rebuild — from the
  disposable Docker container or anywhere else — signs with the same key and
  `adb install -r` keeps working without needing to uninstall first.

- Calorie values in the seed data are reasonable per-serving estimates, not lab
  measurements — edit anything from the Foods screen.
- Voice parsing calls `https://integrate.api.nvidia.com/v1/chat/completions`
  with your API key sent as a Bearer token directly from the device; no backend
  server is involved.
- Verified: `docker build -t foodtracker-build .` and
  `docker run --rm -v "$PWD":/workspace -w /workspace foodtracker-build gradle assembleDebug`
  both succeed end-to-end and produce `app/build/outputs/apk/debug/app-debug.apk`.
  I haven't run the app on-device (no emulator/device attached from this
  environment) — that's the remaining step, on your Pixel 6a via `adb install`.
