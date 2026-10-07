# Fiscal Nest for Android

[![CI](https://github.com/skrpld/fiscal-nest-android/actions/workflows/ci.yml/badge.svg)](https://github.com/skrpld/fiscal-nest-android/actions/workflows/ci.yml)

An offline Android budget planner built on [Fiscal Nest Core](https://github.com/skrpld/fiscal-nest-core).
Everything is calculated and stored on the device: no account, no network.

The app answers three questions every day:

- **How much can I safely spend today?** Cash on hand after upcoming mandatory payments and the
  reserves you choose, divided by the days left in the period.
- **How is this period's money allocated?** Income minus mandatory and optional expenses, then the
  safety cushion top-up, the piggy bank and the free remainder.
- **Where is it heading?** A multi-period forecast with carry-forward of free money and cushion balance.

## Features

| Screen | What it does |
|--------|--------------|
| Overview | Safe daily budget, cash view, period plan, cushion progress, off-plan spending, crisis warnings, forecast of the next periods |
| Events | Income, mandatory and optional expenses: one-time, every N days or every N months on a day (31 = last day), with optional end date |
| Envelopes | Savings split by purpose, each with a spending rule: any time, a limit per budget period, locked until a date, or locked until a goal is reached. Deposits, withdrawals checked against the rule, history with undo |
| What if | Distributes any amounts with your budget rules without changing anything |
| Settings | Budget period (monthly from a pay day, or fixed length), forecast horizon, currency, cents, cushion balance and target, top-up levels, piggy bank, cash reserves, language, theme, JSON backup and restore, getting started guide |

Off-plan purchases are logged from the overview or the spending log and count as `alreadySpent`
for the current period.

Envelopes hold money that is already set aside, so they are kept out of the period plan: the engine
never sees them, and their balance is the sum of their deposits minus withdrawals.

On the first launch a short getting started guide explains events, the daily budget and savings,
then offers a quick setup: the monthly income and pay day (which also align the budget period) and
the cushion balance and target. Every field is optional; the guide can be reopened from Settings.

## Design

- Jetpack Compose with **Material 3** (stable Compose BOM; large collapsing app bars, tonal cards,
  filter chips, Material date pickers and bottom sheets).
- **Dynamic color** (Material You) from the wallpaper on Android 12+, with a hand-tuned fallback
  palette; light, dark or system theme, chosen in Settings.
- Edge-to-edge, predictive back, splash screen, themed (monochrome) launcher icon.
- Adaptive layout: navigation bar on phones, navigation rail and multi-column cards on tablets,
  foldables and landscape.
- Ten languages: English, Russian, Chinese (Simplified), Spanish, Portuguese (Brazil), French,
  German, Hindi, Arabic (right-to-left) and Japanese. On Android 13+ the language can be chosen in
  Settings or in the system per-app language settings; otherwise the device language is used.
  Amounts typed with Arabic-Indic digits are accepted.

## Architecture

```
core/     Fiscal Nest Core engine, vendored unchanged (pure Kotlin/JVM, see core/README.md)
domain/   Pure Kotlin client logic: models, JSON format, period resolution, engine mapping,
          input parsing and validation, formatters, repository contract
app/      Android: DataStore persistence, ViewModels, Compose UI, theme, resources
```

- Single source of truth: one `AppData` document in DataStore (`domain` defines its JSON format,
  also used for backups).
- Unidirectional data flow: repositories expose `Flow`, view models expose `StateFlow` (form text
  lives in Compose state so typing never lags).
- Manual dependency injection through `AppContainer`.
- Type-safe Navigation Compose routes.

## Requirements

| | Version |
|---|---|
| JDK | 17+ (CI uses 21) |
| Android Gradle Plugin | 9.3 |
| Kotlin | 2.4 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |

## Building

```bash
./gradlew :core:test :domain:test        # engine and client logic tests (plain JVM)
./gradlew :app:testDebugUnitTest         # view model and Robolectric UI tests
./gradlew :app:lintDebug                 # Android lint
./gradlew :app:assembleDebug             # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease           # minified release APK
```

## CI and releases

- **CI** (`.github/workflows/ci.yml`) runs on every push and pull request: all tests, Android lint,
  then builds the debug and release APKs and uploads them as the `fiscal-nest-apks` artifact.
- **Release** (`.github/workflows/release.yml`) runs on a `v*` tag (for example `v0.2.0`): tests,
  builds the release APK with that version and attaches it to a GitHub release.

### Release signing

Without signing secrets, release APKs are signed with the CI runner's temporary debug key: they
install, but cannot update each other. To sign with your own key, add these repository secrets:

| Secret | Value |
|--------|-------|
| `FISCAL_NEST_KEYSTORE_BASE64` | `base64 -w0 release.jks` |
| `FISCAL_NEST_KEYSTORE_PASSWORD` | keystore password |
| `FISCAL_NEST_KEY_ALIAS` | key alias |
| `FISCAL_NEST_KEY_PASSWORD` | key password |

Locally, set `FISCAL_NEST_KEYSTORE_FILE` to the keystore path plus the three other variables.

## License

Apache License 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Fiscal Nest Core is
Copyright 2026 skrpld, Apache-2.0.
