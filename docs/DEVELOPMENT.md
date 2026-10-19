# Development guide

How to build, test and change the 4Money self-hosted fork. For setting up the server side (Supabase,
PowerSync) and the values for `local.properties` see [self-hosting.md](self-hosting.md). For the pull
request flow see [../CONTRIBUTING.md](../CONTRIBUTING.md).

## Prerequisites

- JDK 21 (CI uses it; Gradle runs on it).
- Android SDK with command-line tools. You do not need Android Studio. Install the
  [command-line tools](https://developer.android.com/studio#command-line-tools-only), point
  `ANDROID_HOME` at the SDK directory and accept the licenses once:

  ```bash
  sdkmanager --licenses
  ```

  The Android Gradle Plugin downloads the needed platform and build-tools on the first build, once
  the licenses are accepted.
- `adb` (from `platform-tools`, installed with `sdkmanager "platform-tools"`) to install on a phone.

## Configuration

The build needs two `local.properties` files. Both are git-ignored; never commit them.

**`local.properties`** in the repository root, the SDK location (Android Studio writes it for you):

```properties
sdk.dir=/path/to/android-sdk
```

On Windows escape backslashes (`C\:\\dev\\android-sdk`) or use forward slashes.

**`app/local.properties`**, the backend the app talks to:

```properties
supabaseUrl=https://supabase.example.com
supabaseAnonKey=<anon key>
supabaseSchema=money
powerSyncUrl=https://powersync.example.com
authTempCredential=<temporary credential>
# optional, comma-separated hosts that may be reached over plain HTTP
cleartextHosts=
```

What each value means and where to get it is described in [self-hosting.md](self-hosting.md).

`cleartextHosts` is optional. Empty or missing means HTTPS only. Entries are hostnames or IP
addresses (letters, digits, `.`, `-` and `:` only); an invalid entry fails the build with a message
naming it. The `network_security_config.xml` is generated for every build variant by the Gradle task
`generate<Variant>NetworkSecurityConfig` in `app/build.gradle`; there is no static copy to edit.

To only compile and run unit tests you can put placeholders in `app/local.properties` (this is what
CI does): `https://example.invalid` for the URLs and any text for the keys.

## Build, test, lint

Linux/macOS or Git Bash on Windows (use `./gradlew.bat` instead of `./gradlew` on Windows):

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk

./gradlew testDebugUnitTest      # unit tests
./gradlew lintDebug              # Android lint
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug   # what CI runs
```

PowerShell on Windows:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:ANDROID_HOME = 'C:\path\to\android-sdk'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

CI (`.github/workflows/ci.yml`) runs the combined command on JDK 21 with placeholder
`local.properties`. A pull request needs it green.

Reports: `app/build/reports/` (lint HTML, test results).

## Install and run

The debug APK is named after the application id and version:

```bash
adb install -r app/build/outputs/apk/debug/io.github.nickkovdev.money.debug-0.33.0-debug.apk
adb shell am start -n io.github.nickkovdev.money.debug/ua.com.radiokot.money.home.view.HomeActivity
```

(adjust the version in the file name to the current `versionName`).

### Debug vs release

| Variant | applicationId | Notes |
|---|---|---|
| debug | `io.github.nickkovdev.money.debug` | Installs next to a release build. |
| release | `io.github.nickkovdev.money` | Signed with the debug key unless `app/ci-signing.jks` exists. |

The application id differs from upstream 4Money, so the fork is a separate app and never replaces
upstream's. The Kotlin namespace stays `ua.com.radiokot.money`, see [Upstream](#upstream-and-the-namespace).

### Signing and INSTALL_FAILED_UPDATE_INCOMPATIBLE

Debug builds are signed with the debug keystore, which is created per machine
(`~/.android/debug.keystore`). An APK built on another machine has a different signature, and
`adb install -r` fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Either copy the same
`debug.keystore` to the second machine, or uninstall the app first (`adb uninstall <id>`). All app
data lives on the server and syncs back after signing in again; only device-local settings (app lock,
notification access, widget placement, local preferences) have to be set up again.

### Logs

The app writes daily log files (4 days kept) to `Documents/4Money/4MN-<date>.log` on the device's
shared storage, and to logcat under tags starting with `4MN:`:

```bash
adb logcat -s '4MN:*'
```

### Testing on a phone with real data

The app syncs with a real server, so a debug build on your daily phone edits your real data. Rules
of thumb:

- Use a separate account and a separate schema or Supabase project for experiments.
- Never run migrations, or change PowerSync sync rules, against a server holding real data without a
  backup and before the matching app build is installed (see "Server changes" in
  [../CONTRIBUTING.md](../CONTRIBUTING.md)).
- Keep screenshots and logs from real accounts out of issues, pull requests and the repository.

## Project structure

```
app/src/main/java/ua/com/radiokot/money/   Kotlin sources, one package per feature
app/src/main/res/                          resources (values = English, values-ru = Russian)
app/src/test/                              unit tests
supabase/migrations/                       SQL migrations for the `money` schema
supabase/functions/                        Supabase edge functions
deploy/powersync/                          PowerSync service config, sync rules (sync-config.yaml), compose file
tools/icons/                               Tabler icon importer
tools/onemoney-import/                     1Money to 4Money data converter
docs/                                      this guide, specs, plans, redesign notes
fastlane/metadata/                         store listing texts, screenshots, changelogs
```

Feature packages (each usually has `data/`, `logic/`, `view/` and a Koin module):

| Package | Contents |
|---|---|
| `accounts`, `categories`, `currency`, `colors` | accounts, categories, currencies, color palette |
| `transfers` | recording and editing transactions, the quick transfer screen |
| `inbox` | bank notification parsing, rules, templates, the Inbox tab |
| `overview` | statistics and charts |
| `home`, `uikit`, `theme` | main screen shell, shared Compose components, theming |
| `widget` | home screen widget |
| `lock`, `privacy` | app lock and balance hiding |
| `auth`, `powersync`, `syncerrors` | sign-in, sync with PowerSync and Supabase, upload error reporting |
| `preferences`, `util` | settings storage and helpers |

Data is stored locally in SQLite managed by PowerSync, which syncs with Postgres (Supabase, schema
`money`) through the rules in `deploy/powersync/sync-config.yaml`.

## Specs, plans and rulings

Features are specified and planned in the repository before they are built:

- `docs/superpowers/specs/` holds design specs. The binding one for the fork is
  [2026-10-01-self-hosted-4money-design.md](superpowers/specs/2026-10-01-self-hosted-4money-design.md).
- `docs/superpowers/plans/` holds implementation plans, one per feature. A plan lists files, steps
  and a "Rulings" section: decisions taken during planning or review that override a naive reading
  of the spec. Read the rulings of a plan before changing the feature it describes.
- `docs/redesign/` holds the UI redesign brief and progress log.

For a non-trivial change, add or update a plan the same way, then implement it step by step.

## Localization

Strings live in `app/src/main/res/values/strings.xml` (English) and `values-ru/strings.xml`
(Russian). Every user-visible string must exist in both: lint's `MissingTranslation` check is an
error and fails `lintDebug`. Strings that must not be translated are marked `translatable="false"`.

## Icons

Item icons come from Tabler Icons. Import or update them with `tools/icons/import_tabler.py`; see
`tools/icons/README.md`. The Tabler license is in `tools/icons/LICENSE-tabler-icons.txt`.

## License headers

The project is GPL-3.0-or-later. Every new source file starts with the license header. Copy it from
any existing file, for example the top of
`app/src/main/java/ua/com/radiokot/money/MoneyApp.kt`, and use this copyright line for files you add:

```
Copyright 2026 Nikita Kovalovs
```

Keep existing copyright lines in files inherited from upstream (Oleg Koretsky).

## Upstream and the namespace

This is a fork of [Radiokot/4money](https://github.com/Radiokot/4money). The Gradle namespace and all
Kotlin packages stay `ua.com.radiokot.money` on purpose, so that upstream changes still merge
cleanly. Only the `applicationId` was changed. Do not rename packages.

## The ignored test

`SternBrocotTreeSearchTest.extensiveTest` is marked `@Ignore`: it performs 50 000 unseeded random
moves while positions based on `double` are only reliable for a few thousand, so it fails
randomly. The explanation is in the test itself. The deterministic Stern-Brocot tests still run.

## Private notes

Developers may keep private, machine- or server-specific notes in `docs/local/`. The directory is
git-ignored; do not put anything there that the repository needs.
