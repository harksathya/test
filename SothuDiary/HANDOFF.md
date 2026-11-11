# Sothu Diary – agent handoff notes (read this first)

Any agent working on Sothu Diary: read this file before changing code, then `git log -10` (other agents commit
to `main` too – always `git pull` / rebase before pushing). UPDATE this file in every commit, and always before you
stop (rate limit, out of steps), so the next agent can continue. Keep each line short, factual and current; delete
lines that are no longer true. Say what you verified and how (CI run, test, phone) versus what you only believe.

Last updated: 2026-11-11 18:00 IST, by Hark (project agent): added the first-launch Google sign-in requirement under Yet to start. Code is still v3.0.2 `a4ec96d` (CI 42963695478 success). Doc-only commit.

## Ground rules (from the owner)
- Understand the code and recent commits before editing. Never break existing features.
- Commit straight to `main` (no PR), one fix at a time; the owner wants the APK link after each fix. Commit style:
  `Sothu Diary X.Y.Z: <what changed>`. Bump `versionName` / `versionCode` in `composeApp/build.gradle.kts`,
  `APP_VERSION` in `ui/screens/SettingsScreen.kt`, and add a "What's new" section to `README.md` for every release.
- CI workflow "Sothu Diary APK" builds every push to `main` (desktop tests, live endpoint probe printing
  `LIVE PROBE |` lines from `desktopTest/.../LiveEndpointsProbe.kt`, then the APK) and publishes `SothuDiary.apk` to
  release tag `sothu-diary-latest`. Check the run passed and the asset updated before saying a release is done.
- Sandbox toolchain: `work/tools/setup-build.sh` (in the Hark project workspace) recreates JDK 17 + android.jar in /tmp and copies the project to /tmp/sd.
- Run the desktop tests (`./gradlew :composeApp:compileKotlinDesktop :composeApp:desktopTest` from `SothuDiary/`,
  JDK 17, `sdk.dir` in git-ignored `local.properties`) and add tests for new logic (`commonTest/.../V2xxTest.kt`).
  Android-only code is compiled only by CI.
- Only openly licensed / free data sources. ISRO Bhuvan stays OFF (its licence needs written permission).
- The owner reads Tamil; user-facing text is Tamil + English (`i18n/Strings*.kt`, `Tr(en, ta)`).
- The owner's GitHub token can push contents but can NOT comment on issues, edit `.github/workflows`, or start
  workflows by hand – so leave notes here, not in issue comments.

## Done (verified by CI on main unless noted)
- v2.5.0 `5d6804f` – choose any LLM provider at setup (Gemini, OpenAI, Anthropic, OpenRouter, xAI, Groq, Mistral,
  DeepSeek); models listed from the provider; code in `domain/ai/Llm.kt` (text-only chat today). Issue #1.
- v2.6.0 `45a4df3` – issue #1 leftovers: on-tap online price / land AI search with the chosen provider; honest Status
  lines; `.sothu` import.
- v2.6.1 `9c3a383`, v2.6.2 `30bb2f5` – Overpass retries (extra rounds after 3 s and 10 s), 40 s per server, 75 s total.
- v2.7.0 `a1f422d` – land check CORE layers (water, roads, land use) fall back to OpenFreeMap vector tiles
  (`domain/land/VectorTiles.kt`, `fetchOsmLayers(coreFallback=…)` in `Overpass.kt`, `V270Test`). CI probe on it: all
  7 layers OK, 277 elements, map step 34 s.
- v2.7.1 `f7ec265` (CI run 42796600492 success) – (1) tiles asked after 3 s
  (`VectorTileRules.HEDGE_MS`, Overpass grace 4 s; were 20 s + 5 s); (2) TNREGINET page: after 25 s without a load
  or on a page error, a banner with Reload / Open in browser (`ui/screens/GuidelineDuty.kt`, `i18n/Strings271.kt`);
  (3) no retry after HTTP 400 (`httpCodeOf` + `overpassRetryable` in `fetchGroup`); (4) `OVERPASS_SLOTS_PER_SERVER = 2`
  (was 1); (5) © OpenStreetMap (ODbL) / OpenFreeMap credit on `LandCheckScreen.kt`. Tests `V271Test`.
  CI probe on 2.7.1: CORE from tiles, map step 18 s – but EXTRA failed because of a bug in (3), fixed in 2.7.2.
- v2.7.2 `7f47a01` (CI run 42798108314 success, APK updated 15:57 IST) – the 2.7.1 stop rule used
  `overpassRetryable`, which is false for 406, so one server's 406 stopped the whole group. Now `stopsAllServers(code)`
  is true only for 400. Regression test `V271Test.refusedByOneServerStillAsksTheNext`. CI probe on 2.7.2: all three
  servers are now asked (maps.mail.ru 504, overpass-api.de 406, private.coffee timeout 41 s) – they all failed in that
  run, so EXTRA was still empty and the map step used the full 75 s budget. CORE came from tiles.

- v2.8.0 `4d24c87` (CI run 42801541942 success; all 14 desktop test classes pass locally) – Documents
  & EC check: upload EC / patta / chitta / adangal / sale deed (photo or PDF), AI transcription with the user's
  provider, ownership chain + rule flags in common code (`domain/docs/DocAnalysis.kt`, `domain/docs/DocAi.kt`,
  `ui/screens/DocumentsScreen.kt`, `Route.Documents`, tile in `ui/screens/Checks.kt`, strings `i18n/Strings280.kt`,
  data classes `PropertyDocument`, `EcEntry`, `OwnershipLink`, `DocReading`, `DocFlag`, `DocAnalysis` in
  `data/Models.kt`, repo `addDocument/removeDocument/saveDocAnalysis`, `CheckRunner.docs`, Android
  `AndroidImages.copyDocument`). Tests `V280Test` (fixture AI answers; no live AI call in tests).

- v2.8.1 (this commit; desktop tests pass locally; CI pending at time of writing) – owner's first phone test of 2.8.0
  (Tenkasi property, one EC PDF, Gemini `gemini-flash-latest`) failed with "no internet / no answer": cause not known
  (the reason was dropped). `domain/docs/DocAi.kt` now uses `httpCall` and shows the reason + seconds, timeout 240 s,
  falls back to up to 2 more models (`tryNextModel`), total budget 6 min (`DocRules.AI_BUDGET_MS`). NEXT: ask the
  owner for the new error text if it still fails; if it is a timeout, send the EC in page batches or lower thinking.

- v2.9.0 (this commit; desktop tests pass locally; Android code only compiled by CI) – EC chat. Files:
  `domain/docs/EcChat.kt` (transcribePrompt, transcribeDoc page batching, documentsBlock, chatPrompt, askText,
  quickQuestions), `CheckRunner.askDocs` / `ensureDocTexts` / `docProgress` / `chatErrors` (docs() now reads text first,
  then one text call), `Property.docTexts` + `docChat` (`DocText`, `DocChatMsg`), Repository saveDocText / addDocChat /
  clearDocChat / clearDocTexts, `PlatformActions.pdfPageImages` (Android PdfRenderer; desktop null → whole file),
  `DocumentsScreen.EcChatCard`, `i18n/Strings290.kt` (S9), V290Test. Property delete removes document files.
  UNVERIFIED on phone: PdfRenderer on scanned TN EC PDFs, Gemini free-tier rate limit with many 2-page calls
  (a 30-page EC = 15 calls), answer quality in Tamil.
- (Done in v3.0.0, see below) agreed with owner 2026-11-10: Google Drive backup/sync to the user's own Drive (Google sign-in,
  appDataFolder) so the same Gmail restores properties + documents on a new phone; owner must create a Google Cloud
  OAuth Android client (package com.hark.sothudiary? check applicationId + release SHA-1 from CI keystore). Then: a
  clear "AI key" card in Settings (masked key, Test, Change) – today it is under Settings → Online services.

- v2.9.1 (this commit) – owner's phone test of 2.9.0: "EC: pages 1–2: no internet / DNS (Unable to resolve host
  generativelanguage.googleapis.com) · 123 s · gemini-flash-latest" on mobile data. Fix: `readDocumentsWithAi` retries
  network errors (`DocRules.NET_RETRY_WAITS_MS` 4 s, 10 s), `DocText.pagesDone` + `transcribeDoc(resume, onBatch)` save
  every batch and continue from the last page, "Ask again" button (`Repository.removeLastDocChat`), page JPEG 1400 px / 80.
  Still unknown why one 2-page call took 123 s (slow upload or Gemini thinking); if it repeats, try 1 page per call.

## v3.0.1 AI key card – `ad83423`, CI 42841804054 success (APK + desktop tests incl. V301Test)
- `ui/screens/AiKeyCard.kt` (`AiKeyCard`, `maskKey`), `i18n/Strings301.kt` (S31), SettingsScreen: the provider / key /
  model editor moved from Online services into the card's `editor` slot (opens on Change, or when there is no key);
  Online services keeps the YouTube key and saved rates. Test key = `listModels(provider, key)` non-empty. `V301Test`.
- Needs phone check: Test key with the owner's Gemini key; layout in Tamil.

## v3.0.0 Google Drive backup / sync – `e7908ff`, CI 42840196706 success; needs a phone test
- Owner setup in Google Cloud console is DONE (owner, 2026-11-10): Android OAuth client, package `com.hark.sothudiary`,
  SHA-1 `A0:3D:32:E0:1C:F5:4B:B0:60:EA:2D:43:C4:F9:CD:60:C9:74:29:0D` (committed `composeApp/sothu-release.jks`, alias
  sothu, signs both CI debug and release builds). OAuth consent screen is in Testing; the only test user is
  **harksathya@gmail.com** → Drive sign-in on the phone must pick that Gmail (other accounts are refused while in testing).
  If the phone shows DEVELOPER_ERROR (ApiException 10) treat it as a REAL bug to debug, not missing setup: check the
  OAuth client and the Drive API are in the SAME Cloud project, Drive API enabled, test user listed, SHA-1 of the
  installed APK (`apksigner verify --print-certs`) equals the one above, wait 5–30 min after console changes.
- Code: `domain/drive/DriveSync.kt` (DriveRules, StoredRef, referencedFiles, planBackup, planRestore, needsRestoreChoice,
  mergeAppData, multipartBody, authErrorText, DriveClient REST v3, DriveSync connect/backup/restore/disconnect with a
  Mutex and Compose state busy/status), `ui/screens/DriveCard.kt` (Settings card + keep/use/merge dialog),
  `i18n/Strings300.kt` (S30), `AppSettings.driveAccount/driveLastBackupAt/driveAuto`, `Repository.setDrive/parseBackup/
  applyDriveBackup` (keeps this phone's language + Drive fields), auto backup `LaunchedEffect(driveKey)` in `App.kt`
  (30 s debounce; skipped until the first backup / restore choice), expect/actual `httpUploadBytes` / `httpDownloadBytes`
  (`BytesResult`; PATCH sent as POST + X-HTTP-Method-Override), `PlatformActions.driveSupported/driveAuthorize/
  driveClearToken/readAppFile/writeAppFile/appFileExists`, Android `AndroidPlatform` (Identity.getAuthorizationClient,
  hasResolution → `ActivityBridge.startIntentSender` → getAuthorizationResultFromIntent; clearToken on 401),
  `MainActivity` StartIntentSenderForResult, dependency `play-services-auth:22.0.0` (latest on Google Maven 2026-11-10).
- Drive layout (appDataFolder): `sothu_diary.json` + one file per stored file named `<folder>__<file>` (images / voice /
  music). Token never stored; a 401 clears it and asks once more. Email from Drive `about?fields=user(emailAddress)`.
- Safety: a phone whose `driveLastBackupAt == 0` never overwrites an existing Drive backup – the user must choose
  (empty phone → restored automatically; otherwise merge / use Drive / keep phone). Disconnect resets it.
- Tests: `V300Test` (diff, restore list, merge, restore-keeps-settings, multipart, DEVELOPER_ERROR text).
- UNVERIFIED (phone only): GIS consent flow, real uploads/downloads, X-HTTP-Method-Override on the upload endpoint,
  large PDFs (whole file in memory; no resumable upload yet – fine for files of a few MB).

## v3.0.2 EXTRA map layers from tiles – `a4ec96d`, CI 42963695478 success (19 desktop test classes incl. V302Test)
- `domain/land/VectorTiles.kt`: `ExtraTileRules` (z14 within 3 km, parks from z11 within 10 km, Overpass EXTRA grace 15 s,
  ids from -1e9 so they never clash with CORE tile ids in `mergeLayers`), `omtExtraTags` (poi subclass → amenity / shop /
  highway=bus_stop / railway=station|halt; transportation class rail → railway; park polygons → boundary / leisure,
  class like `wildlife_refuge` → protection_title "wildlife sanctuary"; landcover wood/forest → landuse=forest),
  `tilesToExtraLayers`, `fetchTileExtra`. Checked against real OpenFreeMap tiles 2026-11-11 (Madurai z14 poi classes,
  Mudumalai / Srivilliputhur park polygons carry name + class).
- `domain/land/Overpass.kt`: `fetchOsmLayers(extraFallback=…, extraGraceMs=…, groupFetch=…)` – CORE and EXTRA both use
  `hedgeGroup` (tiles after 3 s; Overpass preferred when it answers; stopped after the grace). `LandCheck.kt` passes
  `fetchTileExtra`; `LiveEndpointsProbe.kt` prints an "openfreemap extra tiles" line.
- Not in tiles: power lines / substations / water tanks (UTILITIES) and level crossings → still Overpass only; UTILITIES
  shows Unknown when Overpass is stopped.
- CI probe on 3.0.2 (Madurai pin): extra tiles 415 ms (amenities 9, rail 8, protected 0); land check map step 18 s
  (was 75 s in 2.7.2), ok = 6 of 7 layers, only utilities failed (both Overpass groups stopped after the tiles answered).
  Needs a phone check on mobile data.

## Not working / known limits
- Overpass from GitHub CI runners: overpass-api.de 406 (User-Agent policy / Azure IPs), VK Maps mirror 504 or ~25 s,
  private.coffee no answer. The EXTRA group (amenities, utilities/power lines, rail, proposed roads, 10 km protected
  areas) comes ONLY from Overpass and shows *Unknown* when it fails; it now dominates the land check time (2.7.2
  probe: 75 s, EXTRA empty; 2.7.0 probe: 34 s, EXTRA from maps.mail.ru). 3.0.2 adds the tile fallback for amenities,
  rail and protected areas (see its section); power lines / level crossings still need Overpass.
- TNREGINET (guideline value) unreachable from sandbox and CI (CI: HTTP -1 after 12 s). Form ids (districtList,
  SROList, villageList, streetName, surveyName) match a 2026 archived copy; live auto-fill untested.
- DuckDuckGo / DuckDuckGo Lite block the app (robot check, HTTP 202); Bing fallback works.

## Needs a test on the owner's phone (not yet confirmed)
- Land check on mobile data (Overpass from a phone, and the OpenFreeMap fallback), and its total time.
- TNREGINET guideline page auto-fill, and the new Reload / Open in browser banner.
- `.sothu` share → open from WhatsApp / Files, and the Import button's file picker.
- One AI search with a real non-Gemini provider key.

## In progress
- Nothing half-done in code. v3.0.0 Drive backup and v3.0.1 AI key card are complete and green on CI; both wait for the owner's phone test (see their sections).
- v2.8.0 needs a real test on the phone with a real EC (Tamil) and a Gemini key – not done yet. Open points:
  (a) not verified which providers accept PDF (Gemini and Claude do by their docs; OpenAI-style `file` parts work on
  OpenAI / OpenRouter but Groq / DeepSeek may refuse images – the error is shown on the screen);
  (c) a very long EC (many pages) may exceed the model's output limit – then the JSON is cut and "the AI answer could
  not be read" is shown; a fix would be to send the EC in page batches;
  (d) no "open the file" button yet on the Documents list (not done). (b) was fixed in v2.9.0 (property delete removes document files).

## Yet to start
- **TOP PRIORITY (owner, 2026-11-11 17:55 IST, after installing v3.0.2): Google sign-in as the FIRST setup step.**
  Today a fresh install goes straight to the AI provider / Gemini key screen and never asks for Google login; Drive
  connect is only in Settings. Owner wants: on first launch, before the AI key step, a "Sign in with Google /
  Google கணக்கில் உள்நுழை" screen (reuse the 3.0.0 AuthorizationClient + `drive.appdata` flow in
  `domain/drive/DriveSync.kt` / `ui/screens/DriveCard.kt`). After sign-in: if Drive has `sothu_diary.json`, restore
  everything (properties, files, and the saved AI provider/key/model if they are in the backup – check whether
  settings are in `exportJson`; if not, back them up too) and skip steps already restored; then turn auto-backup on.
  Offer "Skip / பிறகு" so the app still works offline. Same Gmail on another phone must bring everything back
  without typing the key again. Only harksathya@gmail.com is a test user in the OAuth consent screen.
  Not started: Hark usage pool was 99% at 17:55 IST on 11 Nov (resets 7 Dec 2026 14:18 IST).
- Nothing agreed with the owner is left. Other ideas offered, not chosen yet: offline Tamil OCR (Tesseract4Android), reminders for follow-ups, price
  comparison chart.
