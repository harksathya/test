# Sothu Diary (சொத்து டைரி)

A personal property-deal tracker for a buyer in Madurai, Tamil Nadu. Keep every lead you see on
YouTube / Instagram / Facebook, the brokers behind them, every call and promise, the documents you
have verified – and get a quick, explainable **price check** and a **Go / Hold / Drop** suggestion.

Debug APK (latest build): https://github.com/harksathya/test/releases/download/sothu-diary-latest/SothuDiary.apk

## Features (Phase 1)

| Area | What it does |
|---|---|
| Properties | Title, type (Land / House / Farmland / Agricultural land / Apartment / Plot), area-taluk-district, optional lat/lng, size in cents / acres / sq ft (1 acre = 100 cents, 1 cent = 435.6 sq ft), asking price in ₹ with lakh / crore formatting, computed price per cent and per sq ft, source link with auto-detected platform, caption, broker, status pipeline (New → Contacted → Site visit → Negotiating → Bought / Dropped), document checklist (Patta, Chitta, EC, DTCP/RERA approval, road, water, EB, FMB sketch), notes, created/updated dates. |
| Brokers | Name, phone, WhatsApp, platform, area, notes; detail shows their properties and full call history; one-tap **Call** (dialer) and **WhatsApp** (`https://wa.me/91…`). |
| Calls & visits | Per property + broker: date, type (Call / Visit / WhatsApp / Meeting), quoted price, summary, promises, next follow-up. Property timeline highlights price changes ("Quoted ₹21 L on 27 Oct, now ₹24 L"). Optionally updates the asking price. |
| Follow-ups | Overdue / today / upcoming, sorted by date, mark done, call / WhatsApp directly. Badge on the tab for overdue + today. |
| Search / filter / sort | Search over title, location, broker, notes, caption. Filter chips by type, status, verdict, price range (accepts `45L`, `1.2Cr`, `4500000`). Sort by newest, price low→high, high→low, price per cent, next follow-up. |
| Price check | Land is compared per cent; houses and apartments per sq ft. Compared against (a) the guideline value you enter for the area and (b) the median of other listings of the same type group in the same area (or within 5 km if both have coordinates; needs ≥ 2 comparables). Verdicts: **Suspiciously low – possible false promise** (< 0.7× market median, or below guideline value), **Good price** (0.70–0.85×), **Fair** (±15%), **High** (15–40% above), **Too high** (> 40% above), or **Not enough data**. The card shows the reasoning: guideline value, market median, number of comparables, % differences and the property's own quoted-price history. |
| Worth it? | Go / Hold / Drop suggestion from the verdict, missing key documents, price raised since the first quote, the broker's other suspicious listings, and overdue follow-ups – with the reasons listed. The user decides. |
| Share-to-app | Android `ACTION_SEND text/plain` intent filter: share a YouTube / Instagram / Facebook post to Sothu Diary and "New property" opens with the link, detected platform and the shared text as caption. |
| Language | One-tap **த / EN** toggle in every top bar switches all UI strings at runtime; choice is remembered. |
| Tablets | Width ≥ 600 dp: NavigationRail + two-pane list/detail. Phones: bottom navigation + single pane. Landscape and resizing allowed. |
| Settings | Language, export (Android share sheet, JSON), import (file picker, replaces data after confirmation), clear / reload sample data, how the price check works, about. |
| Sample data | 11 clearly labelled SAMPLE properties around Madurai (Vilangudi, Thirumangalam, Othakadai, Thirupparankundram, Melur), 3 sample brokers, 8 interactions and 5 sample guideline values so every verdict can be seen. One tap clears them. Sample prices are illustrative only. |

## Architecture

Kotlin Multiplatform + Compose Multiplatform (Kotlin 2.0.20, Compose MP 1.7.0, AGP 8.5.2, Gradle 8.9).

```
composeApp/src/
  commonMain/kotlin/com/hark/sothudiary/
    App.kt                 root composable, adaptive phone / tablet layout
    Platform.kt            PlatformActions interface + expect PlatformBackHandler
    data/                  Models (kotlinx.serialization), JsonRepository (auto-save), SampleData
    domain/                Units (cents/acres/sq ft, link detection), Format (₹ lakh/crore, dates)
    domain/pricing/        PriceCheck (verdict) + worthIt (Go / Hold / Drop)
    i18n/                  Tr (en/ta pair), S (all strings), Labels (enum labels)
    ui/                    Nav (state-based navigation), AppContext, FilterState
    ui/theme/              Material 3 theme and status / verdict colours
    ui/components/         Top bar with த/EN toggle, chips, cards, date field, property widgets
    ui/screens/            Property list / detail / edit, brokers, interaction edit,
                           follow-ups, guideline values, settings
  androidMain/             MainActivity (share intent, file picker), AndroidPlatform
                           (dialer, WhatsApp, share, toast), AndroidFileStore (filesDir JSON)
  desktopMain/             main.kt (Window), DesktopFileStore (~/.sothudiary), DesktopPlatform
```

* **Persistence** – one JSON file (`sothu_diary.json`) behind the `FileStore` interface; every change is
  saved immediately. The same `AppData` JSON is used for export / import.
* **Navigation** – a tiny state machine (`Nav`: selected tab + route stack) – no navigation library.
* **Strings** – `S.xxx` are `Tr(en, ta)` pairs; `Tr.s()` / `Tr.f(args)` read the current language from
  `LocalLang`. Missing translations are impossible because both values are constructor parameters.

## Build

```
cd SothuDiary
./gradlew :composeApp:assembleDebug          # Android APK → composeApp/build/outputs/apk/debug/
./gradlew :composeApp:run                    # Desktop (Windows / macOS / Linux)
```

CI: `.github/workflows/sothu-diary.yml` builds on every push touching `SothuDiary/**`, uploads the APK as
an artifact and publishes it to the `sothu-diary-latest` release (not marked as the repo's latest release).

## Adding desktop / web later

* **Desktop** – the `jvm("desktop")` target and `desktopMain/main.kt` already exist. Package installers
  with `./gradlew :composeApp:packageDistributionForCurrentOs` (DMG / MSI / DEB). The desktop "Call"
  button copies the number to the clipboard; WhatsApp opens wa.me in the browser.
* **Web (wasmJs)** – add `wasmJs { browser() }` to `kotlin {}` in `composeApp/build.gradle.kts`, a
  `wasmJsMain/main.kt` using `ComposeViewport`, a `FileStore` backed by `localStorage`, a
  `PlatformActions` that uses `window.open`, and a no-op `actual PlatformBackHandler`. All UI and logic
  is already in `commonMain`. Check that kotlinx-datetime's time-zone support is configured for wasm.

## Roadmap

* **Phase 2** – record a voice note after each call → AI summary (price quoted, promises, next step)
  and a post-call popup that offers to log the call against the right broker / property.
* **Phase 3** – AI Go / Hold / Drop review using all calls, documents and market data; Google Drive
  backup / sync between phone, tablet and desktop.
* **Later** – a "YouTube search" tab: search property videos by location and import them as leads;
  map view; Android reminders for follow-ups; photo attachments for documents.

> Verdicts are only as good as the data entered. Always verify documents with a lawyer and the
> sub-registrar office before paying any advance.
