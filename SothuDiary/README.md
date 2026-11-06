# Sothu Diary (சொத்து டைரி) – v2.0

A personal property-deal tracker for a buyer in Madurai, Tamil Nadu. Keep every lead you see on
YouTube / Instagram / Facebook / WhatsApp, the brokers behind them, every call and promise, voice notes,
the documents you have verified – and get a detailed, explainable **price check** against Madurai market
rates and a **Go / Hold / Drop** suggestion. Tamil and English throughout (one-tap **த / EN**).

Debug APK (latest build): https://github.com/harksathya/test/releases/download/sothu-diary-latest/SothuDiary.apk

## What's new in v2.0

| Area | What it does |
|---|---|
| Clean start | No sample data. The app opens on a dashboard with a welcome / onboarding card. Upgrading from v1 deletes the records v1 had flagged `sample` (data migration v1 → v2); your own entries stay. |
| Dashboard (Home) | Hero with counts (properties, active deals, follow-ups due), quick actions (add property, log call, area rates, map), follow-ups due with one-tap call, price alerts (suspiciously low), deal pipeline by status, recently added (thumbnails), sources strip, recent calls & visits. |
| Smart share → auto-fill | Share a YouTube / Instagram / Facebook post or link, a caption, or **one or more images** (poster / screenshot) to the app. The image becomes the property photo; text is read from the caption, the link preview (YouTube oEmbed, Open Graph `og:title / og:description / og:image` with a browser User-Agent) and on-device **OCR** (ML Kit text recognition, Latin script). A pure-Kotlin parser (`domain/ListingParser.kt`, unit-tested) extracts title, description, price (₹25,00,000 · 25L · 25 lakhs · 25 லட்சம் · 1.2 Cr · கோடி), per-unit rates (“per cent ₹3.5L”, “சென்ட் 3 லட்சம்” → total = rate × size), size (cents / சென்ட் / sq ft / acres / ஏக்கர் / grounds = 2,400 sq ft), type (plot / மனை / DTCP, house / வீடு, farm land / பண்ணை, agricultural / விவசாய, apartment / flat / BHK / அடுக்குமாடி), phone numbers (+91 / 10-digit → matches an existing broker or offers to create one) and the locality (matched against the bundled Madurai localities in English and Tamil → also sets the map pin). Every auto-filled field is highlighted “Auto-filled – please check”. |
| Photos & thumbnails | Property photo (shared image or picked), link preview image or YouTube thumbnail (`img.youtube.com/vi/<id>/hqdefault.jpg`), loaded with Coil 3 (Compose Multiplatform). Image-led cards with gradients and 20 dp corners. |
| Folders & grouping | Properties tab has **List / Folders** views. Folders shows brand-coloured source tiles (YouTube, Instagram, Facebook, WhatsApp, Others) with counts and stacked thumbnails, plus your own folders (“Shortlist”, “Jaihindpuram plots”, …) – create, rename, recolour, delete, add / remove properties (from the folder or from a property's detail). The list can be grouped by **Source / Location / Type / Status** with collapsible sticky headers. |
| Real map location | OpenStreetMap via osmdroid (no API key; user agent = package name). Full-screen picker: Nominatim search (`countrycodes=in`, proper User-Agent, debounced, ≥ 1 s between requests) – e.g. “Jaihindpuram Madurai”; **Use my current location** (LocationManager, runtime permission); tap / long-press-drag the pin; reverse geocoding fills area / taluk / district; manual lat / lng entry. No location is ever defaulted. Property detail shows a mini map + **Open in Google Maps** (`geo:` intent, web fallback). **Map tab** shows all properties as pins coloured by verdict, filter by type, optional market-data localities. Desktop: text + lat / lng fallback. |
| Price check (rewritten) | Works for any location and type. Units: plot / land per cent (and sq ft), farmland / agricultural per acre (and per cent), house / apartment per sq ft. Comparables = (a) your own listings of the same type group within 3 km, widening to 5 / 10 km until there are 4, plus same-locality-name listings, and (b) the bundled market dataset: the locality named + the nearest 3–5 localities (≤ 15 km; nearest 3 regardless if none), with a related-type / city-wide fallback so there is always an answer. Shows the estimated fair range, median, number of comparables, each comparable with distance, rate, range, source, as-of date and a **low confidence** tag, the guideline value (your entry; for plots / land otherwise the dataset's village minimum – urban street values are not applied to farmland or built-up prices), % difference, verdict (Suspiciously low – possible false promise / Good / Fair / High / Too high), plain-language reasoning in Tamil / English, a range chart (Canvas) of where the quoted price sits, and a mini map of comparables. States that market figures are listing asking prices. |
| Area rates | Search any locality (English / Tamil) or any place on the map, pick a type → typical rate, fair range in all relevant units, guideline value, nearby localities, your own data nearby, map, and a browsable table of every locality with data. Usable before adding a property (“farmland rate near Jaihindpuram?”). |
| Voice notes | Per call / visit: record (MediaRecorder AAC .m4a, mic permission, timer, stop), attach an existing audio file such as the phone's call recording (copied into app storage), several notes per interaction, play / pause with progress, delete with confirmation. **Speech-to-text** button on the summary (Android SpeechRecognizer, ta-IN or en-IN – follows the app language, switchable) with graceful errors and a fallback to the system speech dialog. Audio is not included in the JSON export. |
| Reminders | Daily ~9 AM (inexact AlarmManager) notification for overdue / today's follow-ups, rescheduled after reboot / update; POST_NOTIFICATIONS asked when you first set a follow-up; toggle in Settings. |
| Professional UI | Adaptive vector icon (+ monochrome), splash (Android 12+ splash API, layer-list before), Material 3 blue / teal theme with **dark mode** (system / light / dark), consistent verdict / status colours, cards with shadows, animated navigation, confirm dialogs (delete, discard unsaved changes), input validation, Indian number formatting (₹1,23,45,678 · L · Cr), system back handling, tablet NavigationRail + two-pane. |

Everything from v1 still works: brokers (call / WhatsApp), calls & visits with quoted-price history, documents checklist, Go / Hold / Drop, search / filter / sort, guideline values, export / import JSON (v1 backups import too), share-to-app, desktop build.

## Market data

`composeApp/data/madurai_rates.json` – 57 Madurai localities with lat / lng and rates per type (plot per sq ft / per cent, apartment / house per sq ft, farmland per acre, TN guideline value, an `overall` portal average used only as a low-weight fallback), each with `source_name`, `source_url`, `as_of`, optional `confidence: low`. Market figures are **asking prices** from listing portals, not registered sale prices.
The file is compiled into `MarketDataJson.kt`; after editing the JSON run `python3 composeApp/data/gen_market_kt.py`.

## Architecture

Kotlin Multiplatform + Compose Multiplatform (Kotlin 2.0.20, Compose MP 1.7.0, AGP 8.5.2, Gradle 8.9). Libraries: kotlinx-serialization, kotlinx-datetime, coroutines, Coil 3 (+ OkHttp network), osmdroid, ML Kit text recognition.

```
composeApp/src/
  commonMain/kotlin/com/hark/sothudiary/
    App.kt                 root composable, adaptive phone / tablet layout, animated navigation
    Platform.kt            PlatformActions (+ AudioActions, LocationActions, images, OCR), expect httpGet / back handler
    data/                  Models (v2: voice notes, photos, folders, theme…), JsonRepository + migrate(), market/ dataset
    domain/                Units, Format (₹ lakh / crore), Geocoder (Nominatim), ListingParser + link previews
    domain/pricing/        estimateMarket / checkPrice (comparables, fair range, verdict) + worthIt
    i18n/                  Tr (en / ta), S (all strings), Labels
    ui/map/                expect GeoMap, LocationPickerDialog
    ui/components/         top bar, cards, PriceAnalysis (chart, comparables), Visuals (images, folder tiles), VoiceNotes
    ui/screens/            Home, Properties (list / folders / grouping), Folder view, Map, Area rates, Property detail / edit,
                           Brokers, Interaction edit, Follow-ups, Guideline values, More, Settings
  commonTest/              ListingParserTest (parser + price check)
  androidMain/             MainActivity (share text / images, permissions, pickers), AndroidPlatform, AndroidAudio,
                           AndroidLocation, AndroidImages (ML Kit OCR), Reminders (AlarmManager + receiver), GeoMap (osmdroid)
  desktopMain/             main.kt, desktop GeoMap fallback, httpGet
```

## Build

```
cd SothuDiary
./gradlew :composeApp:assembleDebug          # Android APK → composeApp/build/outputs/apk/debug/
./gradlew :composeApp:desktopTest            # shared unit tests
./gradlew :composeApp:run                    # Desktop (Windows / macOS / Linux)
```

CI: `.github/workflows/sothu-diary.yml` builds on every push touching `SothuDiary/**`, runs the shared tests
(informational), uploads the APK as an artifact and publishes it to the `sothu-diary-latest` release.

## Limitations

* OCR reads Latin script (English, numbers); ML Kit has no Tamil model, so Tamil text inside a poster image is not read (Tamil captions / link text are parsed).
* Instagram / Facebook often block link previews without login – then only the shared caption is used.
* Speech-to-text needs a speech service (Google app) and usually internet; Tamil needs the ta-IN voice pack.
* Map tiles need internet (they are cached afterwards). Nominatim is a free community service – heavy use is rate-limited.

> Verdicts are only as good as the data. Always verify documents with a lawyer and the sub-registrar office before paying any advance.
