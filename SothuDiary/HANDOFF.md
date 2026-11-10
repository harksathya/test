# Sothu Diary – agent handoff notes (read this first)

Any agent working on Sothu Diary: read this file before changing code, then `git log -10` (other agents commit
to `main` too). When you are about to run out of steps / hit a rate limit, UPDATE this file and commit it to `main`
before you stop, so the next agent can continue. Keep each line short, factual and current; delete lines that are
no longer true. Say what you verified and how (CI run, test, phone) versus what you only believe.

Last updated: 2026-11-10 15:40 IST, by Hark (main agent), at `main` = `a1f422d` (v2.7.0, versionCode 15).

## Ground rules (from the owner)
- Understand the code and recent commits before editing. Never break existing features.
- Commit straight to `main` (no PR). Bump `versionName` / `versionCode` in `composeApp/build.gradle.kts` and add a
  "What's new" section to `README.md` for every release.
- CI workflow "Sothu Diary APK" builds every push to `main` and publishes `SothuDiary.apk` to release tag
  `sothu-diary-latest`. Check the run passed and the asset updated before saying a release is done.
- Run the desktop tests (`./gradlew :composeApp:desktopTest` from `SothuDiary/`) and add tests for new logic.
- Only openly licensed / free data sources. ISRO Bhuvan stays OFF (its licence needs written permission).
- The owner reads Tamil; user-facing text is Tamil + English.
- The owner's GitHub token can push contents but can NOT comment on issues, edit `.github/workflows`, or start
  workflows by hand – so leave notes here, not in issue comments.

## Done (verified by CI on main)
- v2.5.0 `5d6804f` – choose any LLM provider at setup (Gemini, OpenAI, Anthropic, OpenRouter, xAI, Groq, Mistral,
  DeepSeek); models listed from the provider; code in `domain/ai/Llm.kt`. Issue #1.
- v2.6.0 `45a4df3` – issue #1 leftovers: online price search (Price check, Area rates) and land-check zoning / roads /
  growth search are on-tap buttons using the chosen provider (were behind 2.4.0 off-flags); provider-neutral messages;
  honest Status lines; `.sothu` import (button on new property, property list, Home welcome card; open from
  WhatsApp / Files via VIEW octet-stream and SEND by file name).
- v2.6.1 `9c3a383`, v2.6.2 `30bb2f5` – Overpass retries (extra rounds after 3 s and 10 s), 40 s per server, 75 s total;
  price Status separates user rates from online results.
- v2.7.0 `a1f422d` – land check falls back to OpenFreeMap vector tiles (water, roads, land use) when Overpass has not
  answered in 20 s. CI run for `a1f422d`: success.

## Not working / known limits
- Overpass (all three free servers) failed from GitHub CI runners: overpass-api.de returns 406 to Azure IPs, VK Maps
  mirror 504 "busy" or 14–18 s per layer, private.coffee no answer. Amenities, utilities/power lines, rail, proposed
  roads and the 10 km protected-area search still come ONLY from Overpass and show *Unknown* when it fails.
- TNREGINET (guideline value) is unreachable from the sandbox and CI. Form ids (districtList, SROList, villageList,
  streetName, surveyName) match a 2026 archived copy; no code fault found, but the live auto-fill is untested.
- DuckDuckGo web search sometimes fails; the fallback works.

## Needs a test on the owner's phone (not yet confirmed)
- Land check on mobile data (Overpass from a phone, and the OpenFreeMap fallback).
- TNREGINET guideline page auto-fill.
- `.sothu` share → open from WhatsApp / Files, and the Import button's file picker.
- One AI search with a real non-Gemini provider key.

## In progress
- Nothing in progress at the time of this update.

## Yet to start
- No open GitHub issues (only #1, closed). Wait for the owner's next request or phone-test feedback.
