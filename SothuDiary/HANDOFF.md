# Sothu Diary – agent handoff notes (read this first)

Any agent working on Sothu Diary: read this file before changing code, then `git log -10` (other agents commit
to `main` too – always `git pull` / rebase before pushing). UPDATE this file in every commit, and always before you
stop (rate limit, out of steps), so the next agent can continue. Keep each line short, factual and current; delete
lines that are no longer true. Say what you verified and how (CI run, test, phone) versus what you only believe.

Last updated: 2026-11-10 15:45 IST, by Hark (project agent), for the v2.7.1 commit (versionCode 16).

## Ground rules (from the owner)
- Understand the code and recent commits before editing. Never break existing features.
- Commit straight to `main` (no PR), one fix at a time; the owner wants the APK link after each fix. Commit style:
  `Sothu Diary X.Y.Z: <what changed>`. Bump `versionName` / `versionCode` in `composeApp/build.gradle.kts`,
  `APP_VERSION` in `ui/screens/SettingsScreen.kt`, and add a "What's new" section to `README.md` for every release.
- CI workflow "Sothu Diary APK" builds every push to `main` (desktop tests, live endpoint probe printing
  `LIVE PROBE |` lines from `desktopTest/.../LiveEndpointsProbe.kt`, then the APK) and publishes `SothuDiary.apk` to
  release tag `sothu-diary-latest`. Check the run passed and the asset updated before saying a release is done.
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
- v2.7.1 (this commit; desktop tests pass locally, CI pending at time of writing) – (1) tiles asked after 3 s
  (`VectorTileRules.HEDGE_MS`, Overpass grace 4 s; were 20 s + 5 s); (2) TNREGINET page: after 25 s without a load
  or on a page error, a banner with Reload / Open in browser (`ui/screens/GuidelineDuty.kt`, `i18n/Strings271.kt`);
  (3) no retry after HTTP 400 (`httpCodeOf` + `overpassRetryable` in `fetchGroup`); (4) `OVERPASS_SLOTS_PER_SERVER = 2`
  (was 1); (5) © OpenStreetMap (ODbL) / OpenFreeMap credit on `LandCheckScreen.kt`. Tests `V271Test`.

## Not working / known limits
- Overpass from GitHub CI runners: overpass-api.de 406 (User-Agent policy / Azure IPs), VK Maps mirror 504 or ~25 s,
  private.coffee no answer. The EXTRA group (amenities, utilities/power lines, rail, proposed roads, 10 km protected
  areas) comes ONLY from Overpass and shows *Unknown* when it fails; it now dominates the land check time. Possible
  fix: map OpenMapTiles `poi`, `transportation` (rail) and `park` layers in `VectorTiles.kt` as an EXTRA fallback
  (power lines are not in OpenMapTiles).
- TNREGINET (guideline value) unreachable from sandbox and CI (CI: HTTP -1 after 12 s). Form ids (districtList,
  SROList, villageList, streetName, surveyName) match a 2026 archived copy; live auto-fill untested.
- DuckDuckGo / DuckDuckGo Lite block the app (robot check, HTTP 202); Bing fallback works.

## Needs a test on the owner's phone (not yet confirmed)
- Land check on mobile data (Overpass from a phone, and the OpenFreeMap fallback), and its total time.
- TNREGINET guideline page auto-fill, and the new Reload / Open in browser banner.
- `.sothu` share → open from WhatsApp / Files, and the Import button's file picker.
- One AI search with a real non-Gemini provider key.

## In progress
- Nothing half-done in the code after v2.7.1.

## Yet to start (agreed with the owner 2026-11-10; next = v2.8.0)
- Document upload per property: EC, patta, chitta, adangal, sale deed (photo / PDF). Today `Property.docs` is only a
  checklist (`DocItem` enum in `data/Models.kt`), no files.
- EC analysis: read the EC (Tamil or English), trace the ownership chain for the property's patta / survey number,
  and show a summary: seller → buyer (owner names), sale value, year/date, document number, SRO.
- Red flags (non-compliance): gaps in the chain, seller not the previous buyer, mortgage without a release,
  court attachment / lis pendens, power-of-attorney sales, sale value below guideline, survey / extent mismatch
  between EC, patta, chitta, adangal and sale deed, patta holder ≠ latest EC buyer.
- Bilingual (Tamil / English). Planned approach: send document images / PDF to the owner's chosen AI provider
  (needs a multimodal request in `domain/ai/Llm.kt`), parse structured JSON, keep the chain and flag rules in common
  code with tests, save results with the property, never invent values (each row cites its EC entry).
- Other ideas offered, not chosen yet: offline Tamil OCR (Tesseract4Android), reminders for follow-ups, price
  comparison chart, Google Drive backup.
